/*
 * Copyright 2010-2012 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package report;

import java.util.HashMap;
/**
 * Records the average buffer occupancy and its variance with format:
 * <p>
 * [Simulation time] [average buffer occupancy % [0..100] ] [variance]
 * </p>
 *
 * <p>
 * The occupancy is calculated as an instantaneous snapshot every nth second
 * as defined by the <code>occupancyInterval</code> setting, not as an
 * average over time.
 * </p>
 *
 * @author	teemuk
 */
import java.util.List;
import java.util.Map;

import core.DTNHost;
import core.Settings;
import core.SimClock;
import core.UpdateListener;

public class BufferOccupancyReport extends Report implements UpdateListener {

	/**
	 * Record occupancy every nth second -setting id ({@value}).
	 * Defines the interval how often (seconds) a new snapshot of buffer
	 * occupancy is taken
	 */
	public static final String BUFFER_REPORT_INTERVAL = "occupancyInterval";
	/** Default value for the snapshot interval */
	public static final int DEFAULT_BUFFER_REPORT_INTERVAL = 5;

	private double lastRecord = Double.MIN_VALUE;
	private int interval;
    private Map<DTNHost, Double> nodeOccup;

	/**
	 * Creates a new BufferOccupancyReport instance.
	 */
	public BufferOccupancyReport() {
		super();

		Settings settings = getSettings();
		if (settings.contains(BUFFER_REPORT_INTERVAL)) {
			interval = settings.getInt(BUFFER_REPORT_INTERVAL);
		} else {
			interval = -1; /* not found; use default */
		}

		if (interval < 0) { /* not found or invalid value -> use default */
			interval = DEFAULT_BUFFER_REPORT_INTERVAL;
		}

		nodeOccup = new HashMap<>();
	}

	public void updated(List<DTNHost> hosts) {
		if (SimClock.getTime() - lastRecord >= interval) {
			lastRecord = SimClock.getTime();
			printLine(hosts);
		}
	}

	/**
	 * Prints a snapshot of the average buffer occupancy
	 * @param hosts The list of hosts in the simulation
	 */
	private void printLine(List<DTNHost> hosts) {
		// double bufferOccupancy = 0.0;
		// double bo2 = 0.0;

		for (DTNHost h : hosts) {
            if (nodeOccup.containsKey(h)) {
                nodeOccup.put(h, (h.getBufferOccupancy() + nodeOccup.get(h)) / 2);
            } else {
                nodeOccup.put(h, h.getBufferOccupancy());
            }
			// double tmp = h.getBufferOccupancy();
			// tmp = (tmp<=100.0)?(tmp):(100.0);
			// bufferOccupancy += tmp;
			// bo2 += (tmp*tmp)/100.0;
		}

		// double E_X = bufferOccupancy / hosts.size();
		// double Var_X = bo2 / hosts.size() - (E_X*E_X)/100.0;

		// String output = format(SimClock.getTime()) + " " + format(E_X) + " " +
		// 	format(Var_X);
		// write(output);
	}


    @Override
    public void done() {
		write("Report of Buffer Occupancy Per Node - " + getScenarioName());
        write("NODE, OCCUPANCY");
        for (Map.Entry<DTNHost, Double> e : nodeOccup.entrySet()) {
            write(String.format("%s, %.2f", 
                e.getKey(), 
                e.getValue()
            ));
        }
        super.done();
    }

}