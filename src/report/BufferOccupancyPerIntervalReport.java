package report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import core.DTNHost;
import core.UpdateListener;

public class BufferOccupancyPerIntervalReport extends Report implements UpdateListener {
    private Set<Double> simTimes;
    // {host: {time, occupancy}}
    private Map<DTNHost, Map<Double, Double>> bufferOccupancyNodes;
    private double lastTimeReport = 0;
    private final double INTERVAL = 600d;
    
    public BufferOccupancyPerIntervalReport() {
        init();
    }
    
    @Override
    protected void init() {
        super.init();
        simTimes = new TreeSet<>();
        bufferOccupancyNodes = new TreeMap<>();
    }
    
    @Override
    public void updated(List<DTNHost> hosts) {
        double now = Math.floor(getSimTime());
        if (now - lastTimeReport < INTERVAL) {
            return;
        }
        lastTimeReport = now;

        simTimes.add(now);
        
        for (DTNHost host : hosts) {
            double bufferOccupancy = host.getBufferOccupancy();
            bufferOccupancyNodes.computeIfAbsent(host, k -> new LinkedHashMap<>()).put(now, bufferOccupancy);
        }
    }
    
    @Override
    public void done() {
		write("Report of Buffer Occupancy Per Node Per Interval - " + getScenarioName());
        write(String.format("%-6s | %s", 
            "H/T",
            simTimes.stream().map(t -> String.format("%-6.0f", t)).collect(Collectors.joining(" | "))
        ));

        for (Map.Entry<DTNHost, Map<Double, Double>> e : bufferOccupancyNodes.entrySet()) {
            List<Double> values = new ArrayList<>();
            
            for (Double time : simTimes) {
                values.add(e.getValue().getOrDefault(time, 0d));
            }

            write(String.format("%-6s | %-6s", 
                e.getKey(),
                values.stream().map(v -> String.format("%-6.1f", v)).collect(Collectors.joining(" | "))
            ));
        }
        
        super.done();
    }
}
