package report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import core.ConnectionListener;
import core.DTNHost;

public class ConnectionDurationReport extends Report implements ConnectionListener {
    private List<String> logs;
    private Map<String, Double> startTimes;

    public ConnectionDurationReport() {
        init();
    }

    @Override
    protected void init() {
        // TODO Auto-generated method stub
        super.init();
        logs = new ArrayList<>();
        startTimes = new HashMap<>();
    }

    @Override
    public void hostsConnected(DTNHost host1, DTNHost host2) {
        startTimes.put(String.format("%s -> %s", host1, host2), getSimTime());
    }

    @Override
    public void hostsDisconnected(DTNHost host1, DTNHost host2) {
        String key = host1 + " -> " + host2;
        if (startTimes.containsKey(key)) {
            double duration = getSimTime() - startTimes.get(key);
            logs.add(String.format("Connection %s -> %s lasted for %.2f sec", 
                host1,
                host2,
                duration
            ));

            startTimes.remove(key);
        }
    }

    @Override
    public void done() {
        for (String log : logs) {
            write(log);
        }

        super.done();
    }
}
