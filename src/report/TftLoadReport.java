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
	/** cara memilih hub untuk evaluasi: unique (tetangga unik) atau contacts (jumlah kontak) */
	private String hubRanking;
	private final Map<DTNHost, Integer> relayLoad = new HashMap<>();
	private final Set<String> created = new HashSet<>();
	private final Map<String, Double> createTime = new HashMap<>();
	private final Map<String, Double> firstDelivery = new HashMap<>();
	/** TTL awal tiap pesan (detik), untuk delivery ratio pesan matang */
	private final Map<String, Double> ttlSec = new HashMap<>();
	private int relayed = 0;
	private int transfers = 0;
	/** pesan yang pertama kali sampai lewat minimal satu relay */
	private int deliveredViaRelay = 0;

	public TftLoadReport() {
		init();
		Settings s = getSettings();
		hubFraction = s.contains(HUB_FRACTION_S) ? s.getDouble(HUB_FRACTION_S) : 0.2;
		hubRanking = s.contains("hubRanking") ? s.getSetting("hubRanking").trim() : "unique";
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
		// saat dibuat, sisa TTL = TTL awal (menit)
		ttlSec.put(orig(m), m.getTtl() == Integer.MAX_VALUE ? Double.POSITIVE_INFINITY : m.getTtl() * 60.0);
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
				if (m.getHopCount() > 1) {
					deliveredViaRelay++;
				}
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
		// unique: tetangga unik terbanyak (default); contacts: kontak terbanyak, untuk trace
		// yang hampir semua nodenya pernah bertemu semua node (misalnya Haggle Infocom)
		Map<Integer, Integer> uniq = "contacts".equals(hubRanking) ? cp.totalContacts() : cp.totalUniqueContacts();
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
		// pesan matang: dibuat paling lambat endTime - TTL, jadi sempat menunggu selama TTL penuh
		double end = SimScenario.getInstance().getEndTime();
		int matured = 0, maturedDelivered = 0;
		for (String id : created) {
			if (createTime.get(id) + ttlSec.get(id) <= end) {
				matured++;
				if (firstDelivery.containsKey(id)) {
					maturedDelivered++;
				}
			}
		}
		write("created_matured\t" + matured);
		write("delivery_ratio_matured\t" + format(matured == 0 ? 0 : (double) maturedDelivered / matured));
		write("avg_delay\t" + format(delivered == 0 ? 0 : delaySum / delivered));
		write("relayed\t" + relayed);
		// overhead ratio seperti MessageStatsReport ONE: (semua transfer - terkirim) / terkirim
		write("overhead_ratio\t" + format(delivered == 0 ? 0 : (transfers - delivered) / (double) delivered));
		write("tft_drops\t" + cp.getTftDrops());
		write("control_mode\t" + (cp.isDistributedLinkState() ? "distributed" : "ideal"));
		write("ack_transmissions\t" + cp.getAckTransmissions());
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
		// dua definisi hub sekaligus, agar hasil tidak bergantung pada satu definisi
		write("gini_among_hubs_degree\t" + format(giniTop(hosts, cp.totalUniqueContacts(), nHub)));
		write("gini_among_hubs_contacts\t" + format(giniTop(hosts, cp.totalContacts(), nHub)));
		// semua transfer pesan, termasuk transfer terakhir ke tujuan (bukan hanya kerja relay;
		// kerja relay = baris relayed)
		write("total_transfers\t" + transfers);
		write("delivered_via_relay\t" + deliveredViaRelay);
		write("relay_delivery_share\t" + format(delivered == 0 ? 0 : (double) deliveredViaRelay / delivered));
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

	/** Gini beban relay di antara nHub node teratas menurut peringkat rank. */
	private double giniTop(List<DTNHost> hosts, Map<Integer, Integer> rank, int nHub) {
		List<DTNHost> s = new ArrayList<>(hosts);
		Collections.sort(s, (a, b) -> Integer.compare(
				rank.getOrDefault(b.getAddress(), 0), rank.getOrDefault(a.getAddress(), 0)));
		double[] x = new double[nHub];
		for (int i = 0; i < nHub; i++) {
			x[i] = relayLoad.getOrDefault(s.get(i), 0);
		}
		return gini(x);
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
