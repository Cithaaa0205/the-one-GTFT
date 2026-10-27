package report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import core.DTNHost;
import core.Message;
import core.MessageListener;

public class DroppedPacketPerIntervalReport extends Report implements MessageListener {
    private Map<DTNHost, Integer> droppedNodes;
    private Map<DTNHost, Integer> removedNodes;
    private List<String> logs;

    private double lastTimeReport;
    private final double INTERVAL = 600d;
    
    public DroppedPacketPerIntervalReport() {
        init();
    }

    @Override
    protected void init() {
        // TODO Auto-generated method stub
        super.init();

        droppedNodes = new HashMap<>();
        removedNodes = new HashMap<>();
        logs = new ArrayList<>();

        lastTimeReport = 0;
    }

    @Override
    public void messageDeleted(Message m, DTNHost where, boolean dropped) {
        if (dropped) {
            droppedNodes.put(where, droppedNodes.getOrDefault(where, 0) + 1);
        } else {
            removedNodes.put(where, droppedNodes.getOrDefault(where, 0) + 1);
        }

        checkAndWriteReport();
    }

    private void checkAndWriteReport() {
        double now = getSimTime();

        if (now - lastTimeReport < INTERVAL) {
            return;
        }

        lastTimeReport = now;

        Set<DTNHost> allHosts = new HashSet<>();
        allHosts.addAll(droppedNodes.keySet());
        allHosts.addAll(removedNodes.keySet());

        for (DTNHost host : allHosts) {
            int dropped = droppedNodes.getOrDefault(host, 0);
            int removed = removedNodes.getOrDefault(host, 0);
            int total = dropped + removed;

            logs.add(String.format("%s %s %s %d %d %d", 
                "#",
                now,
                host,
                removed,
                dropped,
                total 
            ));
        }
    }

    @Override
    public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {}
    
    @Override
    public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {}

    @Override
    public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean firstDelivery) {}

    @Override
    public void newMessage(Message m) {}

    @Override
    public void done() {
        write("# Report of dropped message per node per interval - " + getScenarioName());

        write("# TIME | HOST | REMOVED | DROPPED | TOTAL");

        Iterator<String> log = logs.iterator();

        while (log.hasNext()) {
            write(log.next());
        }

        super.done();
    }
}
