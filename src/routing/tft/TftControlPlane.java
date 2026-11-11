package routing.tft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import core.DTNHost;
import core.DTNSim;
import core.Settings;
import core.SimClock;

/**
 * Control plane bersama untuk incentive-aware routing Shevade dkk. (2008)
 * beserta modifikasi generosity adaptif (faktor strata S dan kapasitas K).
 *
 * Penyederhanaan simulasi (sesuai asumsi skema acuan bahwa control plane
 * disebarkan dengan jujur):
 *  - Link state dan ACK dianggap tersebar ke seluruh node pada batas interval.
 *    Artinya semua node memakai snapshot interval t-1 yang sama (aturan
 *    pembaruan pada Bab III). Overhead control plane tidak dihitung.
 *  - Satu objek global menyimpan informasi yang secara konseptual dimiliki
 *    setiap node setelah flooding.
 *
 * Notasi mengikuti proposal: B = relay (penolong), A = node sebelumnya pada
 * rute (upstream). T(x,y) = trafik yang direlai x untuk y.
 *
 * Komponen BERSAMA baseline dan skema turunan. Setting (namespace
 * TftControlPlane): interval, degreeWindow, relayCapacity, loadMetric.
 */
public class TftControlPlane {

	static {
		DTNSim.registerForReset(TftControlPlane.class.getCanonicalName());
	}

	private static TftControlPlane instance = null;

	public static TftControlPlane get() {
		if (instance == null) {
			instance = new TftControlPlane();
		}
		return instance;
	}

	/** Dipanggil ONE di antara run batch. */
	public static void reset() {
		instance = null;
	}

	// ------------------------------------------------------------------
	// Parameter (namespace TftControlPlane)
	// ------------------------------------------------------------------
	public static final String NS = "TftControlPlane";
	private final double interval;
	private final double degreeWindow;
	private final double capacity;
	private final String loadMetric;

	private TftControlPlane() {
		Settings s = new Settings(NS);
		interval = s.getDouble("interval", 600);
		degreeWindow = s.getDouble("degreeWindow", 3600);
		capacity = s.getDouble("relayCapacity", 20);
		loadMetric = s.getSetting("loadMetric", "interval").trim();
		nextBoundary = interval;
	}

	public double getInterval() { return interval; }
	/** Kapasitas relay C (paket per interval atau paket buffer, sesuai loadMetric). */
	public double getCapacity() { return capacity; }

	// ------------------------------------------------------------------
	// Registrasi router
	// ------------------------------------------------------------------
	private final Map<Integer, ShevadeRouterView> routers = new HashMap<>();

	/** Antarmuka minimal yang disediakan router ke control plane. */
	public interface ShevadeRouterView {
		DTNHost host();
		int relayMessagesInBuffer();
	}

	public void register(ShevadeRouterView r) {
		routers.put(r.host().getAddress(), r);
	}

	// ------------------------------------------------------------------
	// Statistik kontak (link state): ON/OFF per pasangan
	// ------------------------------------------------------------------
	private static class LinkHistory {
		double firstUp = -1;
		double lastUp = -1;
		double lastDown = -1;
		boolean up = false;
		/** jumlah integral waktu tunggu dan kuadratnya pada periode OFF yang selesai */
		double sumW = 0;   // sigma x^2 / 2
		double sumW2 = 0;  // sigma x^3 / 3
	}

	private final Map<Long, LinkHistory> links = new HashMap<>();
	/** waktu kontak terakhir per pasangan, untuk derajat kontak */
	private final Map<Long, Double> lastContact = new HashMap<>();
	/** total tetangga unik sepanjang simulasi, untuk definisi hub di laporan */
	private final Map<Integer, java.util.Set<Integer>> everMet = new HashMap<>();

	private static long key(int a, int b) {
		int lo = Math.min(a, b), hi = Math.max(a, b);
		return ((long) lo << 32) | (hi & 0xffffffffL);
	}

	public void connectionUp(int a, int b) {
		double now = SimClock.getTime();
		LinkHistory h = links.computeIfAbsent(key(a, b), k -> new LinkHistory());
		if (h.up) {
			return;
		}
		if (h.firstUp < 0) {
			h.firstUp = now;
		} else if (h.lastDown >= 0) {
			double off = now - h.lastDown;
			h.sumW += off * off / 2.0;
			h.sumW2 += off * off * off / 3.0;
		}
		h.up = true;
		h.lastUp = now;
		lastContact.put(key(a, b), now);
		everMet.computeIfAbsent(a, k -> new java.util.HashSet<>()).add(b);
		everMet.computeIfAbsent(b, k -> new java.util.HashSet<>()).add(a);
	}

	public void connectionDown(int a, int b) {
		double now = SimClock.getTime();
		LinkHistory h = links.get(key(a, b));
		if (h == null || !h.up) {
			return;
		}
		h.up = false;
		h.lastDown = now;
		lastContact.put(key(a, b), now);
	}

	// ------------------------------------------------------------------
	// Snapshot interval t-1 (dipakai selama interval t)
	// ------------------------------------------------------------------
	/** mean dan varians waktu tunggu per link pada snapshot */
	private Map<Long, double[]> snapLinkStats = new HashMap<>();
	/** adjacency pada snapshot */
	private Map<Integer, List<Integer>> snapAdj = new HashMap<>();
	private Map<Integer, Integer> snapDegree = new HashMap<>();
	private Map<Integer, Integer> snapLoad = new HashMap<>();
	/** kredit: snapCredit[(x,y)] = paket yang direlai x untuk y (terbukti ACK) pada t-1 */
	private Map<Long, Integer> snapCredit = new HashMap<>();

	/** kredit interval berjalan (dari ACK), dipindah ke snapshot di batas interval */
	private Map<Long, Integer> curCredit = new HashMap<>();
	/** relai yang diterima per interval berjalan (untuk loadMetric = interval) */
	private Map<Integer, Integer> curAccepted = new HashMap<>();
	/** T(B,A) interval berjalan: paket yang sudah diterima B dari A sebagai relay */
	private Map<Long, Integer> curServed = new HashMap<>();

	private double nextBoundary = 600;
	private int intervalIndex = 0;

	/** Riwayat utilisasi hub per interval: list per interval berisi L/C per node */
	private final List<Map<Integer, Double>> utilHistory = new ArrayList<>();

	public int getIntervalIndex() { return intervalIndex; }

	/** Dipanggil setiap update router; menggulung snapshot bila melewati batas. */
	public void tick() {
		double now = SimClock.getTime();
		while (now >= nextBoundary) {
			rollSnapshot(nextBoundary);
			nextBoundary += interval;
		}
	}

	private void rollSnapshot(double at) {
		// 1. link state
		Map<Long, double[]> stats = new HashMap<>();
		Map<Integer, List<Integer>> adj = new HashMap<>();
		for (Map.Entry<Long, LinkHistory> e : links.entrySet()) {
			LinkHistory h = e.getValue();
			if (h.firstUp < 0) {
				continue;
			}
			double sumW = h.sumW, sumW2 = h.sumW2;
			if (!h.up && h.lastDown >= 0) { // periode OFF yang sedang berjalan
				double off = at - h.lastDown;
				sumW += off * off / 2.0;
				sumW2 += off * off * off / 3.0;
			}
			double span = at - h.firstUp;
			if (span <= 0) {
				span = 1;
			}
			double mean = sumW / span;
			double var = Math.max(0, sumW2 / span - mean * mean);
			stats.put(e.getKey(), new double[] { mean, var });
			int a = (int) (e.getKey() >> 32), b = (int) (e.getKey() & 0xffffffffL);
			adj.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
			adj.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
		}
		snapLinkStats = stats;
		snapAdj = adj;

		// 2. derajat kontak dalam jendela waktu
		Map<Integer, Integer> deg = new HashMap<>();
		for (Map.Entry<Long, Double> e : lastContact.entrySet()) {
			LinkHistory h = links.get(e.getKey());
			boolean recent = (h != null && h.up) || e.getValue() >= at - degreeWindow;
			if (recent) {
				int a = (int) (e.getKey() >> 32), b = (int) (e.getKey() & 0xffffffffL);
				deg.merge(a, 1, Integer::sum);
				deg.merge(b, 1, Integer::sum);
			}
		}
		snapDegree = deg;

		// 3. beban relay aktual (penghitung lokal, diumumkan lewat link state)
		Map<Integer, Integer> load = new HashMap<>();
		Map<Integer, Double> util = new HashMap<>();
		for (ShevadeRouterView r : routers.values()) {
			int addr = r.host().getAddress();
			int L = "interval".equals(loadMetric)
					? curAccepted.getOrDefault(addr, 0)
					: r.relayMessagesInBuffer();
			load.put(addr, L);
			util.put(addr, Math.min(1.0, L / capacity));
		}
		snapLoad = load;
		utilHistory.add(util);

		// 4. kredit dari ACK interval t-1
		snapCredit = curCredit;
		curCredit = new HashMap<>();
		curAccepted = new HashMap<>();
		curServed = new HashMap<>();
		intervalIndex++;
	}

	// ------------------------------------------------------------------
	// Kueri untuk source dan relay
	// ------------------------------------------------------------------
	public List<Integer> neighbors(int node) {
		return snapAdj.getOrDefault(node, java.util.Collections.emptyList());
	}

	public boolean hasLink(int a, int b) {
		return snapLinkStats.containsKey(key(a, b));
	}

	/** {mean, var} waktu tunggu link, atau null bila link belum pernah teramati */
	public double[] linkStats(int a, int b) {
		return snapLinkStats.get(key(a, b));
	}

	public int degree(int node) {
		return snapDegree.getOrDefault(node, 0);
	}

	public int load(int node) {
		return snapLoad.getOrDefault(node, 0);
	}

	/** Kredit upstream A di relay B: paket yang direlai A untuk B pada t-1. */
	public int creditOf(int upstreamA, int relayB) {
		return snapCredit.getOrDefault(dirKey(upstreamA, relayB), 0);
	}

	/** T(B,A) pada interval berjalan. */
	public int servedNow(int relayB, int upstreamA) {
		return curServed.getOrDefault(dirKey(relayB, upstreamA), 0);
	}

	public void recordServed(int relayB, int upstreamA) {
		curServed.merge(dirKey(relayB, upstreamA), 1, Integer::sum);
		curAccepted.merge(relayB, 1, Integer::sum);
	}

	/**
	 * ACK untuk rute yang berhasil sampai. Untuk setiap pasangan berurutan
	 * (u, v) dengan v relay (bukan tujuan), v telah merelai untuk u.
	 */
	public void recordAck(int[] route) {
		for (int i = 0; i + 2 < route.length; i++) {
			int upstream = route[i];
			int relay = route[i + 1];
			curCredit.merge(dirKey(relay, upstream), 1, Integer::sum);
		}
	}

	private static long dirKey(int x, int y) {
		return ((long) x << 32) | (y & 0xffffffffL);
	}

	// ------------------------------------------------------------------
	// Untuk laporan
	// ------------------------------------------------------------------
	public Map<Integer, Integer> totalUniqueContacts() {
		Map<Integer, Integer> m = new HashMap<>();
		for (Map.Entry<Integer, java.util.Set<Integer>> e : everMet.entrySet()) {
			m.put(e.getKey(), e.getValue().size());
		}
		return m;
	}

	public List<Map<Integer, Double>> getUtilHistory() {
		return utilHistory;
	}

	private int tftDrops = 0;

	// ---------------- diagnostik (untuk analisis Bab IV) ----------------
	/** jumlah salinan yang direncanakan source melewati relay ini */
	private final Map<Integer, Integer> plannedThrough = new HashMap<>();
	/** jumlah dan total epsilon yang dicek relay */
	private final Map<Integer, double[]> epsChecks = new HashMap<>();
	/** jumlah penolakan TFT per relay */
	private final Map<Integer, Integer> denials = new HashMap<>();

	public void recordPlan(int[] path) {
		for (int i = 1; i + 1 < path.length; i++) {
			plannedThrough.merge(path[i], 1, Integer::sum);
		}
	}

	public void recordEpsCheck(int relayB, int eps, boolean denied) {
		double[] a = epsChecks.computeIfAbsent(relayB, k -> new double[2]);
		a[0]++;
		a[1] += eps;
		if (denied) {
			denials.merge(relayB, 1, Integer::sum);
		}
	}

	public int plannedThrough(int node) { return plannedThrough.getOrDefault(node, 0); }

	public double avgEps(int node) {
		double[] a = epsChecks.get(node);
		return (a == null || a[0] == 0) ? Double.NaN : a[1] / a[0];
	}

	public int denials(int node) { return denials.getOrDefault(node, 0); }

	public void countTftDrop() { tftDrops++; }

	public int getTftDrops() { return tftDrops; }
}
