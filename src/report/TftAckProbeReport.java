package report;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import core.DTNHost;
import core.Settings;
import core.SimClock;
import core.UpdateListener;
import routing.ShevadeTftRouter;

/**
 * Laporan uji integrasi ACK: mencatat kapan setiap node PERTAMA KALI memiliki
 * ACK dengan ID tertentu (setting TftAckProbeReport.ackIds, dipisah koma).
 * Dipakai oleh toolkit/tft/uji_ack_integrasi.py; tidak dipakai di eksperimen.
 */
public class TftAckProbeReport extends Report implements UpdateListener {

	private final String[] ids;
	private final Map<String, Double> first = new HashMap<>();

	public TftAckProbeReport() {
		super();
		Settings s = getSettings();
		ids = s.getSetting("ackIds", "M1").split(",");
	}

	@Override
	public void updated(List<DTNHost> hosts) {
		double now = SimClock.getTime();
		for (DTNHost h : hosts) {
			if (!(h.getRouter() instanceof ShevadeTftRouter)) {
				continue;
			}
			ShevadeTftRouter r = (ShevadeTftRouter) h.getRouter();
			for (String id : ids) {
				String k = id.trim() + "\t" + h.getAddress();
				if (!first.containsKey(k) && r.knowsAck(id.trim())) {
					first.put(k, now);
				}
			}
		}
	}

	@Override
	public void done() {
		for (Map.Entry<String, Double> e : first.entrySet()) {
			write(e.getKey() + "\t" + format(e.getValue()));
		}
		super.done();
	}
}
