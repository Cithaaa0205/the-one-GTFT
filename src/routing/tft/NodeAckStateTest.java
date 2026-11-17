package routing.tft;

import java.util.List;

/**
 * Uji mandiri NodeAckState. Jalankan setelah kompilasi proyek:
 *   java -cp "target:lib/*" routing.tft.NodeAckStateTest
 *
 * Skenario rantai A - B - C (store-carry-forward):
 *   pesan 0 -> 5 -> 9 sampai di tujuan 9; ACK dibuat di 9, lalu harus
 *   menyebar satu hop per langkah, bukan sekaligus.
 */
public class NodeAckStateTest {

	private static int failures = 0;

	public static void main(String[] args) {
		double ttl = 7200;
		NodeAckState dst = new NodeAckState(ttl); // node 9 (tujuan)
		NodeAckState b = new NodeAckState(ttl);   // node yang bertemu tujuan
		NodeAckState c = new NodeAckState(ttl);   // node yang bertemu b belakangan

		NodeAckState.Ack ack = new NodeAckState.Ack("M1", new int[] { 0, 5, 9 }, 100);

		// 1. Tujuan membuat ACK dan langsung menghitung kreditnya sendiri
		check("tujuan menerima ACK baru", dst.receive(ack, 100));
		check("ACK yang sama tidak dihitung dua kali", !dst.receive(ack, 100));
		check("kredit interval berjalan: 5 merelai untuk 0", dst.creditNow(5, 0) == 1);
		check("link ke tujuan tidak diberi kredit", dst.creditNow(9, 5) == 0);
		check("kredit t-1 masih 0 sebelum interval ditutup", dst.creditOf(5, 0) == 0);

		// 2. ACK yang baru diterima tidak boleh diteruskan pada langkah yang sama
		check("langkah yang sama: belum bisa diteruskan", dst.takeForwardable(100).isEmpty());
		List<NodeAckState.Ack> out = dst.takeForwardable(101);
		check("langkah berikutnya: satu ACK siap diteruskan", out.size() == 1);
		check("setelah diambil, antrean kosong", !dst.hasPending());

		// 3. b menerima di langkah 101; c belum tahu apa-apa (belum bertemu b)
		check("b menerima ACK dari tujuan", b.receive(out.get(0), 101));
		check("c belum punya kredit apa pun", c.creditNow(5, 0) == 0);
		check("b belum bisa meneruskan pada langkah yang sama", b.takeForwardable(101).isEmpty());

		// 4. b bertemu c jauh kemudian (store-carry-forward lewat pertukaran saat kontak)
		List<NodeAckState.Ack> missing = b.missingIn(c, 900);
		check("saat kontak b-c: satu ACK belum dimiliki c", missing.size() == 1);
		check("c menerima ACK yang dibawa b", c.receive(missing.get(0), 900));
		check("c kini tahu 5 merelai untuk 0", c.creditNow(5, 0) == 1);
		check("tidak ada lagi ACK yang hilang di c", b.missingIn(c, 900).isEmpty());

		// 5. Penutupan interval: kredit berjalan menjadi kredit t-1
		c.closeInterval(1200);
		check("setelah tutup interval: kredit t-1 = 1", c.creditOf(5, 0) == 1);
		check("setelah tutup interval: kredit berjalan direset", c.creditNow(5, 0) == 0);

		// 6. ACK kedaluwarsa tidak diterima dan tidak diteruskan
		NodeAckState d = new NodeAckState(ttl);
		check("ACK kedaluwarsa ditolak", !d.receive(ack, 100 + ttl + 1));
		check("ACK kedaluwarsa tidak ditawarkan saat kontak", b.missingIn(d, 100 + ttl + 1).isEmpty());

		// 7. Kredit tiap node terpisah: kredit di c tidak mengubah node lain
		NodeAckState e = new NodeAckState(ttl);
		check("node yang tidak menerima ACK tidak punya kredit", e.creditNow(5, 0) == 0 && e.creditOf(5, 0) == 0);

		// 8. Rantai A-B-C, ketiga kontak dimulai pada timestamp yang sama (t = 500).
		//    A sudah punya ACK sejak t = 400. Urutan pertukaran saat kontak: A-B lalu B-C.
		NodeAckState na = new NodeAckState(ttl), nb = new NodeAckState(ttl), nc = new NodeAckState(ttl);
		NodeAckState.Ack ack2 = new NodeAckState.Ack("M2", new int[] { 3, 4, 7 }, 400);
		na.receive(ack2, 400);
		for (NodeAckState.Ack x : na.missingIn(nb, 500)) nb.receive(x, 500);     // kontak A-B
		check("rantai t=500: B menerima ACK dari A", nb.creditNow(4, 3) == 1);
		check("rantai t=500: B tidak menawarkan ACK yang baru diterima", nb.missingIn(nc, 500).isEmpty());
		for (NodeAckState.Ack x : nb.missingIn(nc, 500)) nc.receive(x, 500);     // kontak B-C
		check("rantai t=500: C belum menerima ACK", nc.creditNow(4, 3) == 0);
		check("rantai t=500: B belum bisa meneruskan lewat antrean", nb.takeForwardable(500).isEmpty());
		// langkah berikutnya (t = 501): kontak B-C masih berlangsung
		for (NodeAckState.Ack x : nb.takeForwardable(501)) nc.receive(x, 501);
		check("rantai t=501: C menerima ACK satu langkah kemudian", nc.creditNow(4, 3) == 1);

		// 9. ACK yang sudah keluar dari antrean tetap bisa diberikan pada kontak berikutnya
		NodeAckState nd = new NodeAckState(ttl);
		check("antrean B sudah kosong", !nb.hasPending());
		check("kontak baru B-D (t=900): ACK tetap ditawarkan", nb.missingIn(nd, 900).size() == 1);
		check("A juga masih bisa menawarkan ACK ke D", na.missingIn(nd, 900).size() == 1);

		System.out.println(failures == 0 ? "SEMUA UJI LULUS" : failures + " UJI GAGAL");
		if (failures > 0) {
			System.exit(1);
		}
	}

	private static void check(String name, boolean ok) {
		System.out.println((ok ? "  ok   " : "  GAGAL ") + name);
		if (!ok) {
			failures++;
		}
	}
}
