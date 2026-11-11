package report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.Settings;
import core.SimScenario;
import routing.ShevadeTftRouter;
import routing.tft.TftControlPlane;

/**
 * Laporan metrik penelitian (Tabel 3.5 proposal):
 * Gini index beban relay, beban relay maksimum, porsi beban hub, utilisasi hub,
 * delivery ratio, delay, paket dibuang karena TFT, dan overhead ratio.
 *
 * Beban relay node = jumlah paket milik node lain yang diterima node tersebut
 * sebagai relay (bukan sebagai tujuan). Pesan dihitung per pesan asli
 * (replika dengan ID berbeda digabung lewat properti tft.orig).
 *
 * Setting opsional: TftLoadReport.hubFraction (default 0.2) = porsi node
 * dengan jumlah tetangga unik terbanyak yang dianggap hub untuk evaluasi.
 */
public class TftLoadReport extends Report implements MessageListener {

	public static final String HUB_FRACTION_S = "hubFraction";

	private double hubFraction;
	private final Map<DTNHost, Integer> relayLoad = new HashMap<>();
	private final Set<String> created = new HashSet<>();
	private final Map<String, Double> createTime = new HashMap<>();
	private final Map<String, Double> firstDelivery = new HashMap<>();
	private int relayed = 0;
	private int transfers = 0;

	public TftLoadReport() {
		init();
		Settings s = getSettings();
		hubFraction = s.contains(HUB_FRACTION_S) ? s.getDouble(HUB_FRACTION_S) : 0.2;
	}

	private static String orig(Message m) {
		Object o = m.getProperty(ShevadeTftRouter.ORIG_P);
		return o != null ? (String) o : m.getId();
	}

	@Override
	public void newMessage(Message m) {
		if (isWarmup() || m.getProperty(ShevadeTftRouter.REPLICA_P) != null) {
			return;
		}
		created.add(orig(m));
		createTime.put(orig(m), getSimTime());
	}

	@Override
	public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean first) {
		String id = orig(m);
		if (!created.contains(id)) {
			return; // dibuat saat warmup
		}
		transfers++;
		if (m.getTo() == to) {
			if (!firstDelivery.containsKey(id)) {
				firstDelivery.put(id, getSimTime());
			}
		} else {
			relayLoad.merge(to, 1, Integer::sum);
			relayed++;
		}
	}

	@Override public void messageTransferStarted(Message m, DTNHost from, DTNHost to) { }
	@Override public void messageDeleted(Message m, DTNHost where, boolean dropped) { }
	@Override public void messageTransferAborted(Message m, DTNHost from, DTNHost to) { }

	@Override
	public void done() {
		List<DTNHost> hosts = SimScenario.getInstance().getHosts();
		double[] loads = new double[hosts.size()];
		double total = 0, max = 0;
		for (int i = 0; i < hosts.size(); i++) {
			loads[i] = relayLoad.getOrDefault(hosts.get(i), 0);
			total += loads[i];
			max = Math.max(max, loads[i]);
		}
		double gini = gini(loads);

		// hub untuk evaluasi: node dengan tetangga unik terbanyak
		TftControlPlane cp = TftControlPlane.get();
		Map<Integer, Integer> uniq = cp.totalUniqueContacts();
		List<DTNHost> sorted = new ArrayList<>(hosts);
		Collections.sort(sorted, (a, b) -> Integer.compare(
				uniq.getOrDefault(b.getAddress(), 0), uniq.getOrDefault(a.getAddress(), 0)));
		int nHub = Math.max(1, (int) Math.round(hubFraction * hosts.size()));
		Set<Integer> hubs = new HashSet<>();
		double hubLoad = 0;
		StringBuilder hubNames = new StringBuilder();
		for (int i = 0; i < nHub; i++) {
			DTNHost h = sorted.get(i);
			hubs.add(h.getAddress());
			hubLoad += relayLoad.getOrDefault(h, 0);
			hubNames.append(h).append(i + 1 < nHub ? " " : "");
		}
		double hubUtil = 0;
		int cnt = 0;
		for (Map<Integer, Double> snap : cp.getUtilHistory()) {
			for (int h : hubs) {
				if (snap.containsKey(h)) {
					hubUtil += snap.get(h);
					cnt++;
				}
			}
		}
		hubUtil = cnt > 0 ? hubUtil / cnt : 0;

		int delivered = firstDelivery.size();
		double delaySum = 0;
		for (Map.Entry<String, Double> e : firstDelivery.entrySet()) {
			delaySum += e.getValue() - createTime.get(e.getKey());
		}

		write("Skenario\t" + getScenarioName());
		write("created\t" + created.size());
		write("delivered\t" + delivered);
		write("delivery_ratio\t" + format(created.isEmpty() ? 0 : (double) delivered / created.size()));
		write("avg_delay\t" + format(delivered == 0 ? 0 : delaySum / delivered));
		write("relayed\t" + relayed);
		// overhead ratio seperti MessageStatsReport ONE: (semua transfer - terkirim) / terkirim
		write("overhead_ratio\t" + format(delivered == 0 ? 0 : (transfers - delivered) / (double) delivered));
		write("tft_drops\t" + cp.getTftDrops());
		write("gini_relay_load\t" + format(gini));
		write("max_relay_load\t" + format(max));
		write("hub_share\t" + format(total == 0 ? 0 : hubLoad / total));
		write("hub_utilization\t" + format(hubUtil));
		write("hubs\t" + hubNames);
		double[] hubLoads = new double[nHub];
		for (int i = 0; i < nHub; i++) {
			hubLoads[i] = relayLoad.getOrDefault(sorted.get(i), 0);
		}
		write("gini_among_hubs\t" + format(gini(hubLoads)));
		write("");
		write("node\tunique_contacts\trelay_load\tplanned_by_sources\tavg_eps_checked\ttft_denials");
		for (DTNHost h : sorted) {
			int a = h.getAddress();
			write(h + "\t" + uniq.getOrDefault(a, 0) + "\t"
					+ relayLoad.getOrDefault(h, 0) + "\t" + cp.plannedThrough(a) + "\t"
					+ format(cp.avgEps(a)) + "\t" + cp.denials(a));
		}
		super.done();
	}

	/** Gini index: sum_i sum_j |Li - Lj| / (2 n sum L). */
	public static double gini(double[] x) {
		int n = x.length;
		double sum = 0;
		for (double v : x) {
			sum += v;
		}
		if (n == 0 || sum == 0) {
			return 0;
		}
		double diff = 0;
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				diff += Math.abs(x[i] - x[j]);
			}
		}
		return diff / (2.0 * n * sum);
	}
}
