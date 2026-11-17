package routing.tft;

/**
 * Uji mandiri NodeLinkState. Jalankan setelah kompilasi proyek:
 *   java -cp "target:lib/*" routing.tft.NodeLinkStateTest
 */
public class NodeLinkStateTest {

	private static int failures = 0;

	public static void main(String[] args) {
		final double alpha = 0.8, window = 3600, pktSize = 10000;
		final double speed = 100000; // byte/detik -> 10 paket/detik

		// A (node 0) mengamati dua siklus ON-OFF dengan node 1: (a=1, b=10) x2
		NodeLinkState a = new NodeLinkState(0, alpha, window, pktSize);
		a.onConnectionUp(1, 0, speed);
		a.onConnectionDown(1, 1);
		a.onConnectionUp(1, 11, speed);
		a.onConnectionDown(1, 12);
		a.onConnectionUp(1, 22, speed);

		NodeLinkState c = new NodeLinkState(2, alpha, window, pktSize);

		// 1. Belum ada interval yang ditutup: tidak ada yang diumumkan
		check("sebelum close: A belum tahu link", a.linkStats(0, 1) == null);
		a.closeInterval(22, 7);
		double[] s = a.linkStats(0, 1);
		check("setelah close: A tahu link 0-1", s != null);
		if (s != null) {
			near("mean link 0-1", s[0], 4.5455);
			near("varians link 0-1", s[1], 9.6418);
		}
		check("A: derajat diri sendiri = 1", a.degree(0) == 1);
		check("A: beban diri sendiri = 7", a.load(0) == 7);

		// 2. C belum bertemu siapa pun: tidak tahu apa-apa
		check("sebelum tukar: C tidak tahu link 0-1", c.linkStats(0, 1) == null);
		check("sebelum tukar: C tidak punya tetangga untuk node 0", c.neighbors(0).isEmpty());

		// 3. C bertemu A: tabel dipertukarkan
		a.exchangeWith(c);
		check("setelah tukar: C tahu link 0-1", c.linkStats(0, 1) != null);
		check("setelah tukar: C melihat tetangga node 0 = {1}",
				c.neighbors(0).size() == 1 && c.neighbors(0).get(0) == 1);
		check("setelah tukar: C melihat derajat A", c.degree(0) == 1);
		check("setelah tukar: C melihat beban A", c.load(0) == 7);

		// 4. Entri terbaru menang
		a.closeInterval(622, 12);
		check("sebelum tukar ulang: C masih melihat beban lama", c.load(0) == 7);
		a.exchangeWith(c);
		check("setelah tukar ulang: C melihat beban baru", c.load(0) == 12);

		// 5. Arah sebaliknya: pengetahuan C mengalir ke A
		NodeLinkState d = new NodeLinkState(3, alpha, window, pktSize);
		d.onConnectionUp(2, 0, speed);
		d.onConnectionDown(2, 1);
		d.onConnectionUp(2, 31, speed);
		d.onConnectionDown(2, 32);
		d.onConnectionUp(2, 62, speed);
		d.closeInterval(62, 3);
		d.exchangeWith(c);   // C kini tahu link 2-3
		c.exchangeWith(a);   // A bertemu C: A harus tahu link 2-3 lewat C
		check("transitif: A tahu link 2-3 lewat C", a.linkStats(2, 3) != null);

		// 6. Link yang baru sekali ON tidak diumumkan (belum ada siklus lengkap)
		NodeLinkState e = new NodeLinkState(4, alpha, window, pktSize);
		e.onConnectionUp(5, 0, speed);
		e.onConnectionDown(5, 1);
		e.closeInterval(10, 0);
		check("satu ON saja: link tidak diumumkan", e.linkStats(4, 5) == null);

		// 7. Jendela derajat: kontak lama keluar dari jendela
		NodeLinkState f = new NodeLinkState(6, alpha, window, pktSize);
		f.onConnectionUp(7, 0, speed);
		f.onConnectionDown(7, 1);
		f.closeInterval(5000, 0);   // 5000 - 3600 = 1400 > 1 -> di luar jendela
		check("jendela derajat: kontak lama tidak dihitung", f.degree(6) == 0);
		f.onConnectionUp(7, 5100, speed);
		f.closeInterval(5200, 0);
		check("jendela derajat: kontak baru dihitung", f.degree(6) == 1);

		System.out.println(failures == 0 ? "SEMUA UJI LULUS" : failures + " UJI GAGAL");
		if (failures > 0) {
			System.exit(1);
		}
	}

	private static void check(String name, boolean ok) {
		System.out.printf("%-6s %s%n", ok ? "OK" : "GAGAL", name);
		if (!ok) {
			failures++;
		}
	}

	private static void near(String name, double actual, double expected) {
		check(name + " (dapat " + String.format("%.4f", actual) + ")",
				Math.abs(actual - expected) < 1e-3);
	}
}
