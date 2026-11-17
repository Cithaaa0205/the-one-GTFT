package routing.tft;

import java.util.List;

/**
 * Pandangan sebuah node terhadap link state jaringan.
 *
 * Dua implementasi:
 *  - {@link TftControlPlane}  (mode "ideal"): satu snapshot global yang
 *    dipakai semua node pada batas interval, tanpa biaya penyebaran.
 *  - {@link NodeLinkState}    (mode "distributed"): tabel milik satu node,
 *    diperbarui dari pengamatan sendiri dan pertukaran tabel saat kontak.
 *
 * Router dan HubAwareFactors hanya bergantung pada antarmuka ini, sehingga
 * kedua mode memakai kode skema yang sama.
 */
public interface LinkStateView {

	/** Tetangga node pada graf link yang diketahui (kosong bila tidak ada). */
	List<Integer> neighbors(int node);

	/** {mean, varians} waktu tunggu link a-b, atau null bila belum diketahui. */
	double[] linkStats(int a, int b);

	/** Derajat kontak delta(node) dalam jendela waktu; 0 bila tidak diketahui. */
	int degree(int node);

	/** Beban relay L(node) pada interval terakhir; 0 bila tidak diketahui. */
	int load(int node);
}
