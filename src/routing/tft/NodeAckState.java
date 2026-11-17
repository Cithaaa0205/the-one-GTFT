package routing.tft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * ACK dan kredit TFT milik SATU node (mode distributed).
 *
 * Mengikuti Bagian V paper Shevade dkk. (ACK dissemination):
 *  - tujuan membuat ACK yang memuat rute yang dilalui pesan, lalu ACK itu
 *    di-flood ke node lain lewat kontak;
 *  - setiap node yang menerima ACK menghitung, untuk setiap link (u, v) pada
 *    rute dengan v relay (bukan tujuan), bahwa v telah merelai satu paket
 *    untuk u. Karena ACK memuat rute lengkap, node dapat menghitung trafik
 *    pada setiap link, bukan hanya link miliknya sendiri;
 *  - kredit dari ACK yang diterima pada interval t-1 dipakai pada interval t.
 *
 * Penyebaran bersifat store-carry-forward: ACK hanya berpindah antara dua node
 * yang sedang berkontak, paling jauh satu hop per langkah simulasi. ACK yang
 * baru diterima disimpan dan baru boleh diteruskan pada langkah berikutnya
 * (lihat {@link #takeForwardable(double)}), atau saat node bertemu node lain.
 * Node hanya tahu ACK yang sudah sampai kepadanya, jadi kredit tiap node bisa
 * berbeda dan terlambat. Tanda tangan rute (HIBC di paper) tidak dimodelkan:
 * ACK dianggap selalu lolos verifikasi.
 */
public class NodeAckState {

	/** Satu ACK. Immutable, aman dibagi antar node dalam simulasi. */
	public static final class Ack {
		public final String id;
		public final int[] route;
		public final double created;

		public Ack(String id, int[] route, double created) {
			this.id = id;
			this.route = route;
			this.created = created;
		}
	}

	private final double ackTtl;
	/** ACK yang sudah diketahui node ini (untuk flooding dan mencegah dobel hitung) */
	private final Map<String, Ack> known = new HashMap<>();
	/** waktu ACK pertama kali diterima node ini; ACK baru boleh diteruskan setelah waktu ini */
	private final Map<String, Double> receivedAt = new HashMap<>();
	/** ACK yang baru diterima dan belum diteruskan ke tetangga yang sedang terhubung */
	private final List<Ack> pendingOut = new ArrayList<>();
	private final List<Double> pendingAt = new ArrayList<>();
	/** kredit dari ACK yang diterima pada interval berjalan: (relay, upstream) -> paket */
	private Map<Long, Integer> curCredit = new HashMap<>();
	/** kredit dari ACK yang diterima pada interval sebelumnya */
	private Map<Long, Integer> snapCredit = new HashMap<>();

	public NodeAckState(double ackTtl) {
		this.ackTtl = ackTtl;
	}

	/**
	 * Menerima satu ACK. Mengembalikan true bila ACK baru bagi node ini (lalu
	 * dihitung sebagai kredit dan perlu diteruskan), false bila sudah pernah
	 * diterima atau sudah kedaluwarsa.
	 */
	public boolean receive(Ack a, double now) {
		if (known.containsKey(a.id) || now - a.created > ackTtl) {
			return false;
		}
		known.put(a.id, a);
		receivedAt.put(a.id, now);
		pendingOut.add(a);
		pendingAt.add(now);
		for (int i = 0; i + 2 < a.route.length; i++) {
			curCredit.merge(dirKey(a.route[i + 1], a.route[i]), 1, Integer::sum);
		}
		return true;
	}

	/**
	 * ACK yang diterima SEBELUM waktu now, untuk diteruskan ke tetangga yang
	 * sedang terhubung. ACK yang diterima pada langkah yang sama ditahan sampai
	 * langkah berikutnya, sehingga ACK tidak melompati beberapa hop sekaligus.
	 */
	public List<Ack> takeForwardable(double now) {
		List<Ack> out = new ArrayList<>();
		int i = 0;
		while (i < pendingOut.size()) {
			if (pendingAt.get(i) < now) {
				out.add(pendingOut.remove(i));
				pendingAt.remove(i);
			} else {
				i++;
			}
		}
		return out;
	}

	public boolean hasPending() {
		return !pendingOut.isEmpty();
	}

	/** true bila ACK sudah dimiliki dan diterima SEBELUM waktu now (boleh diteruskan). */
	public boolean canForward(String id, double now) {
		Double t = receivedAt.get(id);
		return t != null && t < now;
	}

	/**
	 * ACK milik node ini yang belum dimiliki node lain, belum kedaluwarsa, dan
	 * sudah boleh diteruskan (diterima sebelum langkah simulasi ini). Aturan
	 * waktunya sama dengan {@link #takeForwardable(double)}.
	 */
	public List<Ack> missingIn(NodeAckState other, double now) {
		List<Ack> out = new ArrayList<>();
		for (Ack a : known.values()) {
			if (!other.known.containsKey(a.id) && now - a.created <= ackTtl && canForward(a.id, now)) {
				out.add(a);
			}
		}
		return out;
	}

	/** Akhir interval node ini: kredit interval berjalan menjadi kredit t-1. */
	public void closeInterval(double now) {
		snapCredit = curCredit;
		curCredit = new HashMap<>();
		for (Iterator<Ack> it = known.values().iterator(); it.hasNext();) {
			Ack a = it.next();
			if (now - a.created > ackTtl) {
				it.remove(); // ACK kedaluwarsa tidak akan diterima lagi oleh receive()
				receivedAt.remove(a.id);
			}
		}
	}

	/** Kredit upstream A di relay B: paket yang direlai A untuk B, dari ACK interval t-1. */
	public int creditOf(int upstreamA, int relayB) {
		return snapCredit.getOrDefault(dirKey(upstreamA, relayB), 0);
	}

	/** Seperti creditOf, tetapi dari ACK yang sudah diterima pada interval berjalan. */
	public int creditNow(int upstreamA, int relayB) {
		return curCredit.getOrDefault(dirKey(upstreamA, relayB), 0);
	}

	/** Diagnostik/uji: apakah node ini sudah memiliki ACK dengan ID ini. */
	public boolean knows(String id) {
		return known.containsKey(id);
	}

	public int knownAcks() {
		return known.size();
	}

	private static long dirKey(int x, int y) {
		return ((long) x << 32) | (y & 0xffffffffL);
	}
}
