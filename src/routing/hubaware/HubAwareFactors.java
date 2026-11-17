package routing.hubaware;

import routing.tft.LinkStateView;
import routing.tft.TftControlPlane;

/**
 * Faktor generosity hub-aware (proposal Bab III, Pers. 3.1-3.3):
 *
 *   S = min(max(delta_B / delta_A, 1), S_max)        faktor strata
 *   K = max(0, 1 - L_B / C_B)                        faktor kapasitas
 *   epsilon(B,A) = floor(epsBase * S * K)      (rounding = floor, default)
 *   epsilon(B,A) = round(epsBase * S * K)      (rounding = round, setengah ke atas)
 *
 * B = relay, A = node sebelumnya pada rute (upstream). Semua masukan diambil
 * dari snapshot interval t-1 pada {@link TftControlPlane}, sehingga source dan
 * relay memperoleh nilai yang sama selama satu interval.
 */
public class HubAwareFactors {

	private final double sMax;
	private final boolean useS;
	private final boolean useK;
	/** true = pembulatan ke bilangan bulat terdekat (setengah ke atas); false = floor */
	private final boolean roundHalfUp;

	public HubAwareFactors(double sMax, boolean useS, boolean useK) {
		this(sMax, useS, useK, false);
	}

	public HubAwareFactors(double sMax, boolean useS, boolean useK, boolean roundHalfUp) {
		this.sMax = sMax;
		this.useS = useS;
		this.useK = useK;
		this.roundHalfUp = roundHalfUp;
	}

	private TftControlPlane cp() {
		return TftControlPlane.get();
	}

	/** Faktor strata. Jika delta_A = 0, delta_A dianggap 1. */
	public double strata(int relayB, int upstreamA) {
		return strata(relayB, upstreamA, cp());
	}

	/** Faktor strata dari pandangan link state sebuah node. */
	public double strata(int relayB, int upstreamA, LinkStateView v) {
		int dA = Math.max(1, v.degree(upstreamA));
		int dB = v.degree(relayB);
		double ratio = (double) dB / dA;
		return Math.min(Math.max(ratio, 1.0), sMax);
	}

	/** Faktor kapasitas dari beban relay L_B dan kapasitas C. */
	public double capacityFactor(int relayB) {
		return capacityFactor(relayB, cp());
	}

	/** Faktor kapasitas dari pandangan link state sebuah node. */
	public double capacityFactor(int relayB, LinkStateView v) {
		return Math.max(0.0, 1.0 - v.load(relayB) / cp().getCapacity());
	}

	/** Generosity adaptif (pandangan ideal/global); faktor yang dinonaktifkan bernilai 1. */
	public int epsilon(int epsBase, int relayB, int upstreamA) {
		return epsilon(epsBase, relayB, upstreamA, cp());
	}

	/**
	 * Generosity adaptif dari pandangan link state sebuah node. Pada mode
	 * distributed, source dan relay bisa memperoleh nilai yang berbeda karena
	 * tabel mereka tidak selalu sama.
	 */
	public int epsilon(int epsBase, int relayB, int upstreamA, LinkStateView v) {
		double s = useS ? strata(relayB, upstreamA, v) : 1.0;
		double k = useK ? capacityFactor(relayB, v) : 1.0;
		double x = epsBase * s * k;
		// 1e-9 menyerap galat floating point (mis. 2 x 0,75 = 1,4999999...)
		return (int) Math.floor(roundHalfUp ? x + 0.5 + 1e-9 : x + 1e-9);
	}
}
