package routing.tft;

/**
 * Uji mandiri. Jalankan:
 *   javac -d out src/routing/tft/*.java
 *   java -cp out routing.tft.LinkStateManagerTest
 */
public class LinkStateManagerTest {

    private static int failures = 0;

    public static void main(String[] args) {
        // Uji 1: satu siklus a=1, b=10 -> mean 4,5455 ; var 9,6418
        check("1 siklus: mean", LinkStateManager.meanWait(100, 11), 4.5455);
        check("1 siklus: var",
                LinkStateManager.variance(100, 1000, 11), 9.6418);

        // Uji 2: tiga siklus OFF = 8, 12, 10 dengan ON = 1
        // sumB2 = 308, sumB3 = 3240, sumCycle = 33
        check("3 siklus: mean", LinkStateManager.meanWait(308, 33), 4.6667);
        check("3 siklus: var",
                LinkStateManager.variance(308, 3240, 33), 10.9495);

        // Uji 3: urutan event sungguhan
        // up 0, down 1, up 11, down 12, up 22 -> dua siklus (1,10)
        LinkStateManager m = new LinkStateManager(0, 0.8);
        m.onConnectionUp(1, 0, 10);
        m.onConnectionDown(1, 1);
        m.onConnectionUp(1, 11, 10);
        m.onConnectionDown(1, 12);
        m.onConnectionUp(1, 22, 10);
        m.closeInterval(22);
        LinkStateManager.LinkStats s = m.getStats(0, 1);
        check("event: link terdaftar", s != null ? 1 : 0, 1);
        if (s != null) {
            check("event: mean", s.mean, 4.5455);
            check("event: var", s.variance, 9.6418);
            check("event: kapasitas", s.capacity, 20.0);   // ON 2 detik x 10 paket/detik
            check("event: timestamp", s.timestamp, 22.0);
            check("event: link tak berarah",
                    m.getStats(1, 0) == s ? 1 : 0, 1);
        }

        // Uji 4: penggabungan tabel antar node
        LinkStateManager other = new LinkStateManager(5, 0.8);
        check("merge: awalnya kosong", other.getStats(0, 1) == null ? 1 : 0, 1);
        other.mergeFrom(m);
        check("merge: terisi setelah gabung", other.getStats(0, 1) != null ? 1 : 0, 1);

        // Uji 5: EWMA pada interval kedua (nilai baru 4,5455 sama -> tetap)
        // siklus (1,10) lagi: down 23, up 33 -> cap interval ini = ON 22->23 = 1 detik x 10 = 10
        m.onConnectionDown(1, 23);
        m.onConnectionUp(1, 33, 10);
        m.closeInterval(44);
        LinkStateManager.LinkStats s2 = m.getStats(0, 1);
        // cap baru: ON 22->23 (1) + ON 33->44 (11) = 12 detik x 10 = 120
        // EWMA: 0,8 x 120 + 0,2 x 20 = 100
        check("ewma: kapasitas", s2.capacity, 100.0);

        // Uji 6: kunci link
        long k = LinkStateManager.key(7, 3);
        check("key: node kecil", LinkStateManager.nodeA(k), 3);
        check("key: node besar", LinkStateManager.nodeB(k), 7);

        System.out.println(failures == 0 ? "SEMUA UJI LULUS" : failures + " UJI GAGAL");
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void check(String name, double actual, double expected) {
        boolean ok = Math.abs(actual - expected) < 1e-3;
        System.out.printf("%-4s %-30s dapat=%.4f harapan=%.4f%n",
                ok ? "OK" : "GAGAL", name, actual, expected);
        if (!ok) {
            failures++;
        }
    }
}
