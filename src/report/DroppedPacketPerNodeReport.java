package report;

import java.util.ArrayList;
// import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import core.DTNHost;
import core.Message;
import core.MessageListener;

public class DroppedPacketPerNodeReport extends Report implements MessageListener {
    
    private Map<DTNHost, Integer> droppedPerNode;
    private Map<DTNHost, Integer> removedPerNode;
    private List<String> logs;
    
    // private DTNHost maxDroppedNode;
    // private DTNHost maxRemovedNode;
    // private DTNHost maxDroppedAndRemovedNode;

    public DroppedPacketPerNodeReport() {
        init();
    }
    
    @Override
    protected void init() {
        // TODO Auto-generated method stub
        super.init();
        droppedPerNode = new HashMap<>();
        removedPerNode = new HashMap<>();
        logs = new ArrayList<>();

        // maxDroppedNode = null;
        // maxRemovedNode = null;
        // maxDroppedAndRemovedNode = null;
    }
    
    @Override
    public void messageDeleted(Message m, DTNHost where, boolean dropped) {
        if (dropped) {
            droppedPerNode.put(
                where, 
                droppedPerNode.getOrDefault(where, 0) + 1
            );
        } else {
            removedPerNode.put(
                where, 
                removedPerNode.getOrDefault(where, 0) + 1
            );
        }
        
    }
    
    @Override
    public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
        // TODO Auto-generated method stub
        
    }
    
    @Override
    public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
        // TODO Auto-generated method stub
        
    }
    
    @Override
    public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean firstDelivery) {
        // TODO Auto-generated method stub
        
    }
    
    @Override
    public void newMessage(Message m) {
        // TODO Auto-generated method stub
        
    }
    
    @Override
    public void done() {
        write("# Report of dropped message per node - " + getScenarioName());
        
        // maxDroppedNode = Collections.max(droppedPerNode.entrySet(), Map.Entry.comparingByValue()).getKey();
        // maxRemovedNode = Collections.max(removedPerNode.entrySet(), Map.Entry.comparingByValue()).getKey();
     
        Set<DTNHost> allHosts = new HashSet<>();
        allHosts.addAll(droppedPerNode.keySet());
        allHosts.addAll(removedPerNode.keySet());
        
        // int maxTotal = -1;

        for(DTNHost host : allHosts) {
            int dropped = droppedPerNode.getOrDefault(host, 0);
            int removed = removedPerNode.getOrDefault(host, 0);
            // int total = dropped + removed;
                        
            // if (total > maxTotal) {
            //     maxTotal = total;
            //     maxDroppedAndRemovedNode = host;
            // }

            // logs.add(String.format("%s %s %d %d %d", 
            logs.add(String.format("%s, %s, %d, %d",  
                "#",
                host,
                removed,
                dropped
                // total
            ));
        }

        // write("# MAX DROPPED NODE: " + maxDroppedNode);
        // write("# MAX REMOVED NODE: " + maxRemovedNode);
        // write("# MOST PROBLEMATIC NODE: " + maxDroppedAndRemovedNode);

        // write("# ------------------------------------");

        write("# HOST, REMOVED, DROPPED");
    
        for(String log : logs) {
            write(log);
        }

        super.done();
    }
}
