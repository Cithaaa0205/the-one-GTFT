package routing.tft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Link state milik SATU node (mode "distributed").
 *
 * Isi tabel:
 *  - metrik link (mean, varians, kapasitas) dari {@link LinkStateManager},
 *    dihaluskan EWMA dan baru diumumkan setelah minimal satu siklus ON-OFF
 *    lengkap teramati;
 *  - atribut node (derajat kontak dan beban relay) yang diumumkan tiap node
 *    untuk dirinya sendiri di akhir interval.
 *
 * Tidak ada akses ke objek global: node hanya tahu apa yang ia amati sendiri
 * dan apa yang ia terima saat bertukar tabel dengan node yang ia temui.
 * Entri dengan timestamp lebih baru menang.
 */
public class NodeLinkState implements LinkStateView {

	/** Atribut satu node, immutable agar aman dibagi antar tabel. */
	private static final class Attr {
		final int degree;
		final int load;
		final double timestamp;

		Attr(int degree, int load, double timestamp) {
			this.degree = degree;
			this.load = load;
			this.timestamp = timestamp;
		}
	}

	private final int self;
	private final double degreeWindow;
	private final double packetSize;
	private final LinkStateManager links;

	private final Map<Integer, Attr> attrs = new HashMap<>();
	/** pengamatan kontak sendiri, untuk menghitung derajat */
	private final Map<Integer, Double> lastContact = new HashMap<>();
	private final Set<Integer> up = new HashSet<>();

	/** cache adjacency; dibangun ulang bila jumlah link yang diketahui berubah */
	private Map<Integer, List<Integer>> adj = new HashMap<>();
	private int adjSize = -1;

	public NodeLinkState(int self, double alpha, double degreeWindow, double packetSize) {
		this.self = self;
		this.degreeWindow = degreeWindow;
		this.packetSize = packetSize;
		this.links = new LinkStateManager(self, alpha);
	}

	// ------------------------------------------------------------------
	// Pengamatan sendiri
	// ------------------------------------------------------------------

	public void onConnectionUp(int peer, double now, double speedBytesPerSec) {
		links.onConnectionUp(peer, now, speedBytesPerSec / packetSize);
		up.add(peer);
		lastContact.put(peer, now);
	}

	public void onConnectionDown(int peer, double now) {
		links.onConnectionDown(peer, now);
		up.remove(peer);
		lastContact.put(peer, now);
	}

	/**
	 * Akhir interval: hitung metrik link sendiri (EWMA), derajat, lalu
	 * umumkan atribut diri sendiri.
	 *
	 * @param load beban relay node ini pada interval yang baru selesai
	 */
	public void closeInterval(double now, int load) {
		links.closeInterval(now);
		int deg = 0;
		for (Map.Entry<Integer, Double> e : lastContact.entrySet()) {
			if (up.contains(e.getKey()) || e.getValue() >= now - degreeWindow) {
				deg++;
			}
		}
		attrs.put(self, new Attr(deg, load, now));
	}

	// ------------------------------------------------------------------
	// Penyebaran: tukar tabel saat kontak (dua arah)
	// ------------------------------------------------------------------

	public void exchangeWith(NodeLinkState other) {
		links.mergeFrom(other.links);
		other.links.mergeFrom(links);
		mergeAttrs(other.attrs, attrs);
		mergeAttrs(attrs, other.attrs);
	}

	private static void mergeAttrs(Map<Integer, Attr> from, Map<Integer, Attr> into) {
		for (Map.Entry<Integer, Attr> e : from.entrySet()) {
			Attr mine = into.get(e.getKey());
			if (mine == null || e.getValue().timestamp > mine.timestamp) {
				into.put(e.getKey(), e.getValue());
			}
		}
	}

	// ------------------------------------------------------------------
	// LinkStateView
	// ------------------------------------------------------------------

	@Override
	public List<Integer> neighbors(int node) {
		if (adjSize != links.getKnownLinks().size()) {
			rebuildAdjacency();
		}
		List<Integer> l = adj.get(node);
		return l == null ? Collections.<Integer>emptyList() : l;
	}

	@Override
	public double[] linkStats(int a, int b) {
		LinkStateManager.LinkStats s = links.getStats(a, b);
		return s == null ? null : new double[] { s.mean, s.variance };
	}

	@Override
	public int degree(int node) {
		Attr a = attrs.get(node);
		return a == null ? 0 : a.degree;
	}

	@Override
	public int load(int node) {
		Attr a = attrs.get(node);
		return a == null ? 0 : a.load;
	}

	private void rebuildAdjacency() {
		Map<Integer, List<Integer>> m = new HashMap<>();
		for (Long key : links.getKnownLinks().keySet()) {
			int x = LinkStateManager.nodeA(key);
			int y = LinkStateManager.nodeB(key);
			m.computeIfAbsent(x, k -> new ArrayList<>()).add(y);
			m.computeIfAbsent(y, k -> new ArrayList<>()).add(x);
		}
		adj = m;
		adjSize = links.getKnownLinks().size();
	}

	/** Untuk pengujian dan diagnostik. */
	public int knownLinkCount() {
		return links.getKnownLinks().size();
	}
}
