package routing.hubaware;

import routing.tft.TftControlPlane;

/**
 * Faktor generosity hub-aware (proposal Bab III, Pers. 3.1-3.3):
 *
 *   S = min(max(delta_B / delta_A, 1), S_max)        faktor strata
 *   K = max(0, 1 - L_B / C_B)                        faktor kapasitas
 *   epsilon(B,A) = floor(epsBase * S * K)
 *
 * B = relay, A = node sebelumnya pada rute (upstream). Semua masukan diambil
 * dari snapshot interval t-1 pada {@link TftControlPlane}, sehingga source dan
 * relay memperoleh nilai yang sama selama satu interval.
 */
public class HubAwareFactors {

	private final double sMax;
	private final boolean useS;
	private final boolean useK;

	public HubAwareFactors(double sMax, boolean useS, boolean useK) {
		this.sMax = sMax;
		this.useS = useS;
		this.useK = useK;
	}

	private TftControlPlane cp() {
		return TftControlPlane.get();
	}

	/** Faktor strata. Jika delta_A = 0, delta_A dianggap 1. */
	public double strata(int relayB, int upstreamA) {
		int dA = Math.max(1, cp().degree(upstreamA));
		int dB = cp().degree(relayB);
		double ratio = (double) dB / dA;
		return Math.min(Math.max(ratio, 1.0), sMax);
	}

	/** Faktor kapasitas dari beban relay L_B dan kapasitas C. */
	public double capacityFactor(int relayB) {
		return Math.max(0.0, 1.0 - cp().load(relayB) / cp().getCapacity());
	}

	/** Generosity adaptif; faktor yang dinonaktifkan bernilai 1. */
	public int epsilon(int epsBase, int relayB, int upstreamA) {
		double s = useS ? strata(relayB, upstreamA) : 1.0;
		double k = useK ? capacityFactor(relayB) : 1.0;
		return (int) Math.floor(epsBase * s * k + 1e-9);
	}
}
