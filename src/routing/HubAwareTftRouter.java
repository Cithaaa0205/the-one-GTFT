package routing;

import core.Settings;
import routing.hubaware.HubAwareFactors;

/**
 * MODIFIKASI: Generous Tit-for-Tat hub-aware.
 *
 * Mewarisi seluruh mekanisme baseline {@link ShevadeTftRouter} dan hanya
 * mengganti epsilon menjadi floor(epsBase * S * K).
 *
 * Varian (setting HubAwareTftRouter.variant):
 *  REACTIVE   epsilon adaptif hanya di relay; source memakai epsilon konstan
 *  PROACTIVE  epsilon adaptif di source (TFTCap) dan relay  (skema usulan)
 *  S_ONLY     proaktif, hanya faktor strata (K = 1)
 *  K_ONLY     proaktif, hanya faktor kapasitas (S = 1)
 *
 * Setting lain: HubAwareTftRouter.sMax (default 3). Nilai epsBase diambil dari
 * ShevadeTftRouter.epsBase agar sama dengan epsilon konstan baseline.
 */
public class HubAwareTftRouter extends ShevadeTftRouter {

	public static final String HUB_NS = "HubAwareTftRouter";
	public static final String VARIANT_S = "variant";
	public static final String SMAX_S = "sMax";

	public enum Variant {
		REACTIVE, PROACTIVE, S_ONLY, K_ONLY
	}

	private Variant variant;
	private double sMax;
	private HubAwareFactors factors;

	public HubAwareTftRouter(Settings s) {
		super(s);
		forceMode(Mode.GTFT);
		Settings hs = new Settings(HUB_NS);
		variant = Variant.valueOf(hs.getSetting(VARIANT_S, "PROACTIVE").trim());
		sMax = hs.getDouble(SMAX_S, 3);
		factors = makeFactors();
	}

	protected HubAwareTftRouter(HubAwareTftRouter r) {
		super(r);
		this.variant = r.variant;
		this.sMax = r.sMax;
		this.factors = makeFactors();
	}

	private HubAwareFactors makeFactors() {
		boolean useS = variant != Variant.K_ONLY;
		boolean useK = variant != Variant.S_ONLY;
		return new HubAwareFactors(sMax, useS, useK);
	}

	@Override
	public HubAwareTftRouter replicate() {
		return new HubAwareTftRouter(this);
	}

	@Override
	protected int epsilonAtSource(int relayB, int upstreamA) {
		if (variant == Variant.REACTIVE) {
			return super.epsilonAtSource(relayB, upstreamA); // source belum tahu epsilon baru
		}
		return factors.epsilon(epsBase, relayB, upstreamA);
	}

	@Override
	protected int epsilonAtRelay(int relayB, int upstreamA) {
		return factors.epsilon(epsBase, relayB, upstreamA);
	}

	@Override
	public String toString() {
		return "HubAwareTftRouter[" + variant + "]";
	}
}
