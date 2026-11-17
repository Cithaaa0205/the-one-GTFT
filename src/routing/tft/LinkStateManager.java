package routing.tft;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Tahap 1 - Link State Dissemination.
 *
 * Satu instance per node. Kelas ini sengaja tidak bergantung pada kelas ONE
 * (DTNHost, Connection, SimClock) supaya bisa diuji sendiri dengan main().
 *
 * Alur:
 *   1. Router memanggil onConnectionUp / onConnectionDown tiap ada kontak.
 *   2. Di akhir tiap interval router memanggil closeInterval(): metrik link
 *      milik node ini (mean, varians, kapasitas) dihitung lalu dihaluskan EWMA.
 *   3. Saat dua node bertemu, router memanggil mergeFrom(): entri dengan
 *      timestamp lebih baru yang menang.
 *
 * Link diperlakukan tak berarah (A-B sama dengan B-A) karena statistik
 * waktu tunggu hanya bergantung pada pola kontak, bukan arah.
 */
public class LinkStateManager {

    /** Metrik satu link. Immutable, jadi aman dibagi antar node di simulasi. */
    public static final class LinkStats {
        public final double mean;       // E[W], detik
        public final double variance;   // Var(W), detik^2
        public final double capacity;   // paket per interval
        public final double timestamp;  // waktu simulasi saat dihitung

        public LinkStats(double mean, double variance, double capacity, double timestamp) {
            this.mean = mean;
            this.variance = variance;
            this.capacity = capacity;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return String.format("mean=%.4f var=%.4f cap=%.1f t=%.1f",
                    mean, variance, capacity, timestamp);
        }
    }

    /** Pengamatan mentah satu tetangga milik node ini. */
    private static final class Observation {
        boolean up = false;
        double onStart = -1;     // kapan periode ON terakhir mulai
        double lastDown = -1;    // kapan periode OFF terakhir mulai
        double lastOn = -1;      // durasi ON terakhir yang sudah selesai (a)
        double bw = 0;           // bandwidth saat ON, paket/detik

        // akumulator untuk interval berjalan
        double sumB2 = 0;        // jumlah b^2 dari siklus yang selesai
        double sumB3 = 0;        // jumlah b^3
        double sumCycle = 0;     // jumlah (a + b)
        double onTime = 0;       // total waktu ON di interval ini

        void resetInterval() {
            sumB2 = 0;
            sumB3 = 0;
            sumCycle = 0;
            onTime = 0;
        }
    }

    private final int selfAddress;
    private final double alpha;                          // bobot EWMA untuk nilai baru
    private final Map<Integer, Observation> observations = new HashMap<>();
    private final Map<Long, LinkStats> table = new HashMap<>();
    private double intervalStart = 0;

    public LinkStateManager(int selfAddress, double alpha) {
        if (alpha <= 0 || alpha > 1) {
            throw new IllegalArgumentException("alpha harus di (0, 1], dapat: " + alpha);
        }
        this.selfAddress = selfAddress;
        this.alpha = alpha;
    }

    // ------------------------------------------------------------------
    // Rumus (dibuat static agar mudah diuji)
    // ------------------------------------------------------------------

    /** E[W] = sum(b^2) / (2 * sum(a + b)) */
    public static double meanWait(double sumB2, double sumCycle) {
        return sumB2 / (2.0 * sumCycle);
    }

    /** E[W^2] = sum(b^3) / (3 * sum(a + b)) */
    public static double secondMoment(double sumB3, double sumCycle) {
        return sumB3 / (3.0 * sumCycle);
    }

    /** Var(W) = E[W^2] - E[W]^2, dijepit minimal 0 untuk galat pembulatan. */
    public static double variance(double sumB2, double sumB3, double sumCycle) {
        double m = meanWait(sumB2, sumCycle);
        return Math.max(0.0, secondMoment(sumB3, sumCycle) - m * m);
    }

    // ------------------------------------------------------------------
    // Event kontak
    // ------------------------------------------------------------------

    /**
     * Dipanggil saat link ke 'peer' menjadi UP.
     * Bila sebelumnya ada periode ON lalu OFF, satu siklus (a, b) selesai.
     */
    public void onConnectionUp(int peer, double now, double bwPktPerSec) {
        Observation o = obs(peer);
        if (o.up) {
            return; // abaikan event ganda
        }
        if (o.lastDown >= 0 && o.lastOn >= 0) {
            double b = now - o.lastDown;   // OFF yang baru selesai
            double a = o.lastOn;           // ON sebelum OFF itu
            o.sumB2 += b * b;
            o.sumB3 += b * b * b;
            o.sumCycle += a + b;
        }
        o.up = true;
        o.onStart = now;
        o.bw = bwPktPerSec;
    }

    /** Dipanggil saat link ke 'peer' menjadi DOWN. */
    public void onConnectionDown(int peer, double now) {
        Observation o = obs(peer);
        if (!o.up) {
            return;
        }
        o.lastOn = now - o.onStart;
        o.onTime += now - Math.max(o.onStart, intervalStart); // hanya bagian di interval ini
        o.up = false;
        o.lastDown = now;
    }

    // ------------------------------------------------------------------
    // Akhir interval
    // ------------------------------------------------------------------

    /**
     * Hitung metrik semua link milik node ini, haluskan dengan EWMA,
     * simpan ke tabel, lalu mulai interval baru.
     */
    public void closeInterval(double now) {
        for (Map.Entry<Integer, Observation> e : observations.entrySet()) {
            int peer = e.getKey();
            Observation o = e.getValue();
            long key = key(selfAddress, peer);
            LinkStats old = table.get(key);

            double onTime = o.onTime;
            if (o.up) {
                onTime += now - Math.max(o.onStart, intervalStart); // ON yang masih berjalan
            }
            double capNow = onTime * o.bw;

            LinkStats updated;
            if (o.sumCycle > 0) {
                double meanNow = meanWait(o.sumB2, o.sumCycle);
                double varNow = variance(o.sumB2, o.sumB3, o.sumCycle);
                updated = new LinkStats(
                        ewma(meanNow, old == null ? null : old.mean),
                        ewma(varNow, old == null ? null : old.variance),
                        ewma(capNow, old == null ? null : old.capacity),
                        now);
            } else if (old != null) {
                // tidak ada siklus lengkap di interval ini: pertahankan mean/varians lama
                updated = new LinkStats(old.mean, old.variance,
                        ewma(capNow, old.capacity), now);
            } else {
                updated = null; // belum ada satu siklus pun, jangan umumkan link ini
            }
            if (updated != null) {
                table.put(key, updated);
            }
            o.resetInterval();
        }
        intervalStart = now;
    }

    private double ewma(double fresh, Double previous) {
        return previous == null ? fresh : alpha * fresh + (1.0 - alpha) * previous;
    }

    // ------------------------------------------------------------------
    // Penyebaran
    // ------------------------------------------------------------------

    /** Gabungkan tabel node lain: untuk tiap link, ambil yang timestamp-nya lebih baru. */
    public void mergeFrom(LinkStateManager other) {
        for (Map.Entry<Long, LinkStats> e : other.table.entrySet()) {
            LinkStats mine = table.get(e.getKey());
            if (mine == null || e.getValue().timestamp > mine.timestamp) {
                table.put(e.getKey(), e.getValue());
            }
        }
    }

    // ------------------------------------------------------------------
    // Akses untuk PathCalculator (Tahap 2)
    // ------------------------------------------------------------------

    public LinkStats getStats(int a, int b) {
        return table.get(key(a, b));
    }

    public Map<Long, LinkStats> getKnownLinks() {
        return Collections.unmodifiableMap(table);
    }

    public static long key(int a, int b) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        return ((long) lo << 32) | (hi & 0xffffffffL);
    }

    public static int nodeA(long key) {
        return (int) (key >>> 32);
    }

    public static int nodeB(long key) {
        return (int) key;
    }

    private Observation obs(int peer) {
        return observations.computeIfAbsent(peer, p -> new Observation());
    }
}
