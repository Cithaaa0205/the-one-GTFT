package routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.Settings;
import core.SimClock;
import routing.tft.LinkStateView;
import routing.tft.NodeAckState;
import routing.tft.NodeLinkState;
import routing.tft.TftControlPlane;

/**
 * BASELINE: incentive-aware source routing berbasis Generous Tit-for-Tat
 * (Shevade dkk., 2008).
 *
 * Mode (setting ShevadeTftRouter.scheme):
 *  COOP_NOTFT     semua node kooperatif, tanpa batas TFT
 *  SELFISH_NOTFT  node selfish tanpa insentif: hanya pengiriman langsung
 *  GTFT           Generous TFT dengan epsilon konstan (baseline)
 *
 * Alur per interval:
 *  1. snapshot t-1 (link state, beban relay, kredit ACK) digulung oleh
 *     {@link TftControlPlane#tick()};
 *  2. source menghitung peluang sampai tiap jalur kandidat (<= maxHops) dengan
 *     ketaksamaan Chebyshev, lalu mengalokasikan pesan dari jalur terbaik
 *     hingga TFTCap jalur habis, kemudian ke jalur berikutnya (Fig. 4);
 *     pesan yang belum terkirim dialokasikan ulang di awal setiap interval;
 *  3. relay menolak (membuang) trafik dari upstream A bila
 *     T(B,A) >= kredit(A di B) + epsilon(B,A);
 *  4. tujuan mengirim ACK yang menambah kredit untuk interval berikutnya.
 *
 * Titik perluasan: epsilonAtSource() dan epsilonAtRelay(). Skema turunan
 * (misalnya HubAwareTftRouter) cukup menimpa kedua fungsi ini.
 *
 * TODO baseline (lihat Fig. 4 dan Bagian IV paper): contrition, kapasitas link
 * (Cap) dan trafik latar belakang (B_prev) pada TFTCap, serta model eksploitasi
 * generosity oleh node selfish. Catatan: pada Fig. 4 TFTCap = min(forwarded(k))
 * tanpa epsilon; di kode ini epsilon ikut dijumlahkan ke TFTCap (perlu
 * dikonfirmasi). EWMA link state sudah diimplementasikan di LinkStateManager.
 */
public class ShevadeTftRouter extends ActiveRouter
		implements TftControlPlane.ShevadeRouterView {

	public static final String NS = "ShevadeTftRouter";
	public static final String SCHEME_S = "scheme";
	public static final String EPS_BASE_S = "epsBase";
	public static final String REP_FACTOR_S = "repFactor";
	public static final String MAX_HOPS_S = "maxHops";
	public static final String DEADLINE_S = "deadline";
	public static final String REPLAN_S = "replanEachInterval";

	/** properti pesan */
	public static final String ROUTE_P = "tft.route";
	public static final String ORIG_P = "tft.orig";
	public static final String REPLICA_P = "tft.replica";

	/** kode penolakan TFT (negatif = ditolak, lihat MessageRouter) */
	public static final int DENIED_TFT = -50;

	public enum Mode {
		COOP_NOTFT, SELFISH_NOTFT, GTFT
	}

	private Mode mode;
	/** epsilon konstan (baseline) dan epsilon dasar skema turunan */
	protected int epsBase;
	private int repFactor;
	private int maxHops;
	private double deadline;
	/** rencanakan ulang rute pesan milik sendiri yang belum terkirim di awal setiap interval */
	private boolean replan;
	private int lastReplanInterval = 0;

	/** mode distributed: link state milik node ini (null pada mode ideal) */
	private NodeLinkState nodeLinks = null;
	/** mode distributed: ACK dan kredit TFT milik node ini (null pada mode ideal) */
	private NodeAckState nodeAcks = null;
	private double nextBoundary = 0;
	private int acceptedThisInterval = 0;
	/** tukar tabel satu langkah simulasi setelah batas interval, agar semua node sudah mengumumkan */
	private boolean exchangePending = false;

	/** penggunaan TFTCap oleh source ini pada interval berjalan, per link (A->B) */
	private Map<Long, Integer> plannedUse = new HashMap<>();
	private int plannedInterval = -1;
	/**
	 * Rute yang memakai kuota TFTCap pada interval berjalan, per ID pesan milik
	 * sendiri. Hanya pencatatan; dipakai untuk melepas kuota saat replan di
	 * tengah interval (lihat {@link #replanOwnMessagesMidInterval()}).
	 */
	private Map<String, int[]> allocThisInterval = new HashMap<>();
	/** penanda per rute hasil planRoutes terakhir: true bila rute memakai kuota */
	private List<Boolean> lastConsumed = new ArrayList<>();

	public ShevadeTftRouter(Settings s) {
		super(s);
		Settings ts = new Settings(NS);
		mode = Mode.valueOf(ts.getSetting(SCHEME_S, "GTFT").trim());
		epsBase = ts.getInt(EPS_BASE_S, 2);
		repFactor = ts.getInt(REP_FACTOR_S, 1);
		maxHops = ts.getInt(MAX_HOPS_S, 3);
		deadline = ts.getDouble(DEADLINE_S, -1);
		replan = ts.getBoolean(REPLAN_S, true);
		TftControlPlane.get(); // membaca setting TftControlPlane.*
	}

	protected ShevadeTftRouter(ShevadeTftRouter r) {
		super(r);
		this.mode = r.mode;
		this.epsBase = r.epsBase;
		this.repFactor = r.repFactor;
		this.maxHops = r.maxHops;
		this.deadline = r.deadline;
		this.replan = r.replan;
	}

	@Override
	public void init(DTNHost host, List<MessageListener> mListeners) {
		super.init(host, mListeners);
		if (deadline <= 0) {
			deadline = (msgTtl > 0) ? msgTtl * 60.0 : 3600;
		}
		TftControlPlane.get().register(this);
		if (cp().isDistributedLinkState()) {
			nodeLinks = new NodeLinkState(host.getAddress(), cp().getEwmaAlpha(),
					cp().getDegreeWindow(), cp().getPacketSize());
			nodeAcks = new NodeAckState(cp().getAckTtl());
			nextBoundary = cp().getInterval();
		}
	}

	@Override
	public ShevadeTftRouter replicate() {
		return new ShevadeTftRouter(this);
	}

	// ------------------------------------------------------------------
	// View untuk control plane
	// ------------------------------------------------------------------
	@Override
	public DTNHost host() {
		return getHost();
	}

	@Override
	public int relayMessagesInBuffer() {
		int n = 0;
		for (Message m : getMessageCollection()) {
			if (m.getFrom() != getHost()) {
				n++;
			}
		}
		return n;
	}

	// ------------------------------------------------------------------
	// Link state: mode ideal (global) atau distributed (per node)
	// ------------------------------------------------------------------
	/** Pandangan link state yang dipakai node ini untuk keputusan routing dan epsilon. */
	protected LinkStateView linkView() {
		return nodeLinks != null ? nodeLinks : cp();
	}

	private int currentLoad() {
		return cp().isIntervalLoad() ? acceptedThisInterval : relayMessagesInBuffer();
	}

	/** Menutup interval milik node ini dan menukar tabel dengan tetangga yang sedang terhubung. */
	private void distributedTick() {
		if (exchangePending) {
			exchangePending = false;
			exchangeWithConnected();
		}
		double now = SimClock.getTime();
		while (now >= nextBoundary) {
			nodeLinks.closeInterval(nextBoundary, currentLoad());
			nodeAcks.closeInterval(nextBoundary);
			acceptedThisInterval = 0;
			nextBoundary += cp().getInterval();
			exchangePending = true;
		}
	}

	private void exchangeWithConnected() {
		int me = getHost().getAddress();
		for (Connection c : getConnections()) {
			DTNHost o = c.getOtherNode(getHost());
			if (me < o.getAddress() && o.getRouter() instanceof ShevadeTftRouter) {
				NodeLinkState theirs = ((ShevadeTftRouter) o.getRouter()).nodeLinks;
				if (theirs != null) {
					nodeLinks.exchangeWith(theirs);
					onLinkStateReceived();
					((ShevadeTftRouter) o.getRouter()).onLinkStateReceived();
				}
			}
		}
	}

	// ------------------------------------------------------------------
	// Kredit TFT dan ACK flooding
	// ------------------------------------------------------------------
	/** Kredit A di B (paket yang direlai A untuk B) menurut pengetahuan node ini, interval t-1. */
	protected int credit(int upstreamA, int relayB) {
		return nodeAcks != null ? nodeAcks.creditOf(upstreamA, relayB) : cp().creditOf(upstreamA, relayB);
	}

	/** Kredit yang dipakai relay untuk menegakkan TFT (lihat TftControlPlane.enforceWindow). */
	private int enforcementCredit(int upstreamA, int relayB) {
		if (!cp().enforceWithCurrentCredit()) {
			return credit(upstreamA, relayB);
		}
		return nodeAcks != null ? nodeAcks.creditNow(upstreamA, relayB) : cp().creditNow(upstreamA, relayB);
	}

	/**
	 * Saat kontak dimulai: kedua node saling mengirim ACK yang belum dimiliki
	 * lawannya (satu hop). Penerima menyimpan ACK itu dan baru meneruskannya
	 * pada langkah simulasi berikutnya.
	 */
	private void exchangeAcks(ShevadeTftRouter other) {
		double now = SimClock.getTime();
		List<NodeAckState.Ack> toOther = nodeAcks.missingIn(other.nodeAcks, now);
		List<NodeAckState.Ack> toMe = other.nodeAcks.missingIn(nodeAcks, now);
		for (NodeAckState.Ack a : toOther) {
			if (other.nodeAcks.receive(a, now)) {
				cp().countAckTransmission();
			}
		}
		for (NodeAckState.Ack a : toMe) {
			if (nodeAcks.receive(a, now)) {
				cp().countAckTransmission();
			}
		}
	}

	/**
	 * Setiap langkah simulasi: ACK yang diterima pada langkah sebelumnya
	 * diteruskan ke node yang sedang berkontak dengan node ini (satu hop).
	 */
	private void forwardPendingAcks() {
		double now = SimClock.getTime();
		if (!nodeAcks.hasPending()) {
			return;
		}
		List<NodeAckState.Ack> out = nodeAcks.takeForwardable(now);
		if (out.isEmpty()) {
			return;
		}
		for (Connection c : getConnections()) {
			if (!c.isUp() || !(c.getOtherNode(getHost()).getRouter() instanceof ShevadeTftRouter)) {
				continue;
			}
			ShevadeTftRouter o = (ShevadeTftRouter) c.getOtherNode(getHost()).getRouter();
			if (o.nodeAcks == null) {
				continue;
			}
			for (NodeAckState.Ack a : out) {
				if (o.nodeAcks.receive(a, now)) {
					cp().countAckTransmission();
				}
			}
		}
	}

	/** Diagnostik/uji (hanya baca): apakah node ini sudah menerima ACK dengan ID ini. */
	public boolean knowsAck(String id) {
		return nodeAcks != null && nodeAcks.knows(id);
	}

	/** Dipakai skema turunan untuk memastikan mekanisme TFT aktif. */
	protected void forceMode(Mode m) {
		this.mode = m;
	}

	protected Mode mode() {
		return mode;
	}

	protected TftControlPlane cp() {
		return TftControlPlane.get();
	}

	// ------------------------------------------------------------------
	// Link state: pencatatan kontak
	// ------------------------------------------------------------------
	@Override
	public void changedConnection(Connection con) {
		DTNHost other = con.getOtherNode(getHost());
		int a = getHost().getAddress(), b = other.getAddress();
		if (nodeLinks != null) { // mode distributed: tiap node mencatat kontaknya sendiri
			double now = SimClock.getTime();
			if (con.isUp()) {
				nodeLinks.onConnectionUp(b, now, con.getSpeed());
				if (a < b && other.getRouter() instanceof ShevadeTftRouter) {
					NodeLinkState theirs = ((ShevadeTftRouter) other.getRouter()).nodeLinks;
					if (theirs != null) {
						nodeLinks.exchangeWith(theirs); // tukar tabel saat kontak dimulai
						exchangeAcks((ShevadeTftRouter) other.getRouter());
						onLinkStateReceived();
						((ShevadeTftRouter) other.getRouter()).onLinkStateReceived();
					}
				}
			} else {
				nodeLinks.onConnectionDown(b, now);
			}
		}
		if (a > b) {
			return; // dicatat sekali per pasangan
		}
		if (con.isUp()) {
			cp().connectionUp(a, b);
		} else {
			cp().connectionDown(a, b);
		}
	}

	// ------------------------------------------------------------------
	// Pihak source: perhitungan rute (Fig. 4)
	// ------------------------------------------------------------------
	private static class Path {
		final int[] nodes;
		final double p;
		Path(int[] nodes, double p) {
			this.nodes = nodes;
			this.p = p;
		}
	}

	@Override
	public boolean createNewMessage(Message m) {
		cp().tick();
		List<int[]> routes = planRoutes(m, repFactor);
		List<Boolean> used = lastConsumed;
		m.addProperty(ROUTE_P, routes.get(0));
		noteAlloc(m.getId(), routes.get(0), used, 0);
		m.addProperty(ORIG_P, m.getId());
		boolean ok = super.createNewMessage(m);
		for (int k = 1; k < routes.size(); k++) {
			Message rep = new Message(m.getFrom(), m.getTo(), m.getId() + "_r" + k, m.getSize());
			rep.addProperty(ROUTE_P, routes.get(k));
			rep.addProperty(ORIG_P, m.getId());
			rep.addProperty(REPLICA_P, Boolean.TRUE);
			noteAlloc(rep.getId(), routes.get(k), used, k);
			super.createNewMessage(rep);
		}
		return ok;
	}

	/** Memilih hingga repFactor rute; selalu menghasilkan minimal rute langsung. */
	private List<int[]> planRoutes(Message m, int copies) {
		int src = getHost().getAddress();
		int dst = m.getTo().getAddress();
		List<int[]> chosen = new ArrayList<>();
		lastConsumed = new ArrayList<>();

		if (mode == Mode.SELFISH_NOTFT) {
			chosen.add(new int[] { src, dst });
			return chosen;
		}

		if (plannedInterval != cp().getIntervalIndex()) {
			plannedUse = new HashMap<>();
			allocThisInterval = new HashMap<>();
			plannedInterval = cp().getIntervalIndex();
		}

		List<Path> paths = candidatePaths(src, dst);
		// urutkan dari peluang sampai tertinggi; seri -> hop lebih sedikit
		Collections.sort(paths, (x, y) -> {
			int c = Double.compare(y.p, x.p);
			return c != 0 ? c : Integer.compare(x.nodes.length, y.nodes.length);
		});

		for (Path p : paths) {
			if (chosen.size() >= copies) {
				break;
			}
			if (p.p <= 0 && !chosen.isEmpty()) {
				break;
			}
			if (tftCap(p.nodes) >= 1) {
				consume(p.nodes);
				chosen.add(p.nodes);
				lastConsumed.add(Boolean.TRUE);
				cp().recordPlan(p.nodes);
			}
		}
		if (chosen.isEmpty()) {
			chosen.add(new int[] { src, dst }); // tunggu bertemu langsung
		}
		return chosen;
	}

	/** Enumerasi jalur kandidat hingga maxHops pada snapshot link state. */
	private List<Path> candidatePaths(int src, int dst) {
		List<Path> out = new ArrayList<>();
		List<Integer> cur = new ArrayList<>();
		cur.add(src);
		dfs(src, dst, cur, out);
		return out;
	}

	private void dfs(int node, int dst, List<Integer> cur, List<Path> out) {
		if (cur.size() - 1 >= maxHops) {
			return;
		}
		for (int nb : linkView().neighbors(node)) {
			if (cur.contains(nb)) {
				continue;
			}
			cur.add(nb);
			if (nb == dst) {
				int[] arr = cur.stream().mapToInt(Integer::intValue).toArray();
				out.add(new Path(arr, deliveryLowerBound(arr)));
			} else {
				dfs(nb, dst, cur, out);
			}
			cur.remove(cur.size() - 1);
		}
	}

	/** Batas bawah peluang sampai sebelum deadline (ketaksamaan Chebyshev). */
	private double deliveryLowerBound(int[] nodes) {
		double mu = 0, var = 0;
		for (int i = 0; i + 1 < nodes.length; i++) {
			double[] st = linkView().linkStats(nodes[i], nodes[i + 1]);
			if (st == null) {
				return 0;
			}
			mu += st[0];
			var += st[1];
		}
		if (deadline <= mu) {
			return 0;
		}
		double r = Math.sqrt(var) / (deadline - mu);
		return Math.max(0, 1 - r * r);
	}

	/**
	 * Epsilon yang dipakai source saat menghitung TFTCap.
	 * Baseline: konstan. Skema turunan boleh menimpa.
	 */
	protected int epsilonAtSource(int relayB, int upstreamA) {
		return epsBase;
	}

	/**
	 * Epsilon yang ditegakkan relay pada aturan penolakan.
	 * Baseline: konstan. Skema turunan boleh menimpa.
	 */
	protected int epsilonAtRelay(int relayB, int upstreamA) {
		return epsBase;
	}

	protected boolean tftActive() {
		return mode == Mode.GTFT;
	}

	/** TFTCap(p) = min per link relay (A->B) dari kredit + epsilon - pemakaian. */
	private int tftCap(int[] nodes) {
		if (!tftActive()) {
			return Integer.MAX_VALUE;
		}
		int cap = Integer.MAX_VALUE;
		for (int i = 0; i + 2 < nodes.length; i++) {
			int a = nodes[i], b = nodes[i + 1];
			int budget = credit(a, b) + epsilonAtSource(b, a)
					- plannedUse.getOrDefault(linkKey(a, b), 0);
			cap = Math.min(cap, budget);
		}
		return cap;
	}

	private void consume(int[] nodes) {
		for (int i = 0; i + 2 < nodes.length; i++) {
			plannedUse.merge(linkKey(nodes[i], nodes[i + 1]), 1, Integer::sum);
		}
	}

	private static long linkKey(int a, int b) {
		return ((long) a << 32) | (b & 0xffffffffL);
	}

	// ------------------------------------------------------------------
	// Pihak relay: aturan penolakan TFT
	// ------------------------------------------------------------------
	@Override
	public int receiveMessage(Message m, DTNHost from) {
		cp().tick();
		if (m.getTo() != getHost()) {
			if (mode == Mode.SELFISH_NOTFT) {
				return DENIED_TFT;
			}
			if (tftActive()) {
				int b = getHost().getAddress(), a = from.getAddress();
				int eps = epsilonAtRelay(b, a);
				int allowed = enforcementCredit(a, b) + eps;
				boolean deny = cp().servedNow(b, a) >= allowed;
				cp().recordEpsCheck(b, eps, deny);
				if (deny) {
					return DENIED_TFT;
				}
			}
		}
		return super.receiveMessage(m, from);
	}

	@Override
	public Message messageTransferred(String id, DTNHost from) {
		Message m = super.messageTransferred(id, from);
		if (m.getTo() == getHost()) {
			// ACK memuat jalur yang benar-benar dilalui; dibanjirkan dan
			// berlaku sebagai kredit pada interval berikutnya
			List<DTNHost> hops = m.getHops();
			int[] path = new int[hops.size()];
			for (int i = 0; i < path.length; i++) {
				path[i] = hops.get(i).getAddress();
			}
			cp().recordAck(path);
			if (nodeAcks != null) { // tujuan membuat ACK lalu mem-flood-nya
				NodeAckState.Ack ack = new NodeAckState.Ack(m.getId(), path, SimClock.getTime());
				nodeAcks.receive(ack, SimClock.getTime()); // diteruskan mulai langkah berikutnya
			}
		} else {
			cp().recordServed(getHost().getAddress(), from.getAddress());
			acceptedThisInterval++;
		}
		return m;
	}

	// ------------------------------------------------------------------
	// Penerusan sesuai rute
	// ------------------------------------------------------------------
	@Override
	public void update() {
		super.update();
		cp().tick();
		if (nodeLinks != null) {
			distributedTick();
			forwardPendingAcks();
		}
		if (replan && mode != Mode.SELFISH_NOTFT
				&& cp().getIntervalIndex() != lastReplanInterval) {
			lastReplanInterval = cp().getIntervalIndex();
			replanOwnMessages();
		}
		if (isTransferring() || !canStartTransfer()) {
			return;
		}
		// pengiriman langsung ke tujuan yang sedang terhubung
		if (exchangeDeliverableMessages() != null) {
			return;
		}
		forwardAlongRoutes();
	}

	/**
	 * Fig. 4 dijalankan pada awal setiap interval: pesan milik sendiri yang
	 * masih menunggu di source dialokasikan ulang dengan TFTCap interval baru.
	 */
	private void replanOwnMessages() {
		List<Message> own = new ArrayList<>();
		for (Message m : getMessageCollection()) {
			if (m.getFrom() == getHost()) {
				own.add(m);
			}
		}
		sortByQueueMode(own);
		for (Message m : own) {
			List<int[]> r = planRoutes(m, 1);
			m.updateProperty(ROUTE_P, r.get(0));
			noteAlloc(m.getId(), r.get(0), lastConsumed, 0);
		}
	}

	private void noteAlloc(String id, int[] route, List<Boolean> used, int k) {
		if (k < used.size() && used.get(k)) {
			allocThisInterval.put(id, route);
		} else {
			allocThisInterval.remove(id);
		}
	}

	/**
	 * Replan di tengah interval: kuota TFTCap milik pesan sendiri yang masih
	 * menunggu di buffer dilepas dulu (agar tidak terhitung dua kali), lalu
	 * Fig. 4 dijalankan ulang dengan link state terbaru milik node ini.
	 * Kuota pesan yang sudah dikirim pada interval ini tetap terhitung.
	 */
	protected void replanOwnMessagesMidInterval() {
		if (mode == Mode.SELFISH_NOTFT || plannedInterval != cp().getIntervalIndex()) {
			replanOwnMessages(); // belum ada alokasi di interval ini
			return;
		}
		List<Message> own = new ArrayList<>();
		for (Message m : getMessageCollection()) {
			// pesan yang sedang ditransfer tetap memakai rute lamanya, jadi kuotanya tidak dilepas
			if (m.getFrom() != getHost() || isSending(m.getId())) {
				continue;
			}
			own.add(m);
			int[] r = allocThisInterval.remove(m.getId());
			if (r != null) {
				for (int i = 0; i + 2 < r.length; i++) {
					long k = linkKey(r[i], r[i + 1]);
					plannedUse.merge(k, -1, Integer::sum);
					assert plannedUse.get(k) >= 0 : "kuota TFTCap negatif pada link " + r[i] + "->" + r[i + 1];
				}
			}
		}
		sortByQueueMode(own);
		for (Message m : own) {
			List<int[]> r = planRoutes(m, 1);
			m.updateProperty(ROUTE_P, r.get(0));
			noteAlloc(m.getId(), r.get(0), lastConsumed, 0);
		}
	}

	/**
	 * Dipanggil setiap kali node ini menerima tabel link state dari node yang
	 * ditemuinya (hanya mode distributed). Baseline tidak melakukan apa-apa.
	 */
	protected void onLinkStateReceived() {
	}

	private void forwardAlongRoutes() {
		Map<Integer, Connection> byAddr = new HashMap<>();
		for (Connection c : getConnections()) {
			byAddr.put(c.getOtherNode(getHost()).getAddress(), c);
		}
		if (byAddr.isEmpty()) {
			return;
		}
		List<Message> msgs = new ArrayList<>(getMessageCollection());
		sortByQueueMode(msgs);
		for (Message m : msgs) {
			int next = nextHop(m);
			if (next < 0) {
				continue;
			}
			Connection c = byAddr.get(next);
			if (c == null) {
				continue;
			}
			int ret = startTransfer(m, c);
			if (ret == RCV_OK) {
				return;
			}
			if (ret == DENIED_TFT) {
				cp().countTftDrop();
				deleteMessage(m.getId(), true); // relay membuang trafik di atas batas
			}
		}
	}

	private int nextHop(Message m) {
		int[] route = (int[]) m.getProperty(ROUTE_P);
		if (route == null) {
			return -1;
		}
		int me = getHost().getAddress();
		for (int i = 0; i + 1 < route.length; i++) {
			if (route[i] == me) {
				return route[i + 1];
			}
		}
		return -1;
	}

	@Override
	protected void transferDone(Connection con) {
		Message m = con.getMessage();
		if (m == null) {
			return;
		}
		// salinan tunggal per rute: setelah diteruskan ke hop berikutnya, hapus
		if (hasMessage(m.getId())) {
			deleteMessage(m.getId(), false);
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + mode + "]";
	}

}
