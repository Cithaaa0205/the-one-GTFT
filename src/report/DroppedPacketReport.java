package report;

import java.util.ArrayList;
import java.util.List;

import core.DTNHost;
import core.Message;
import core.MessageListener;

public class DroppedPacketReport extends Report implements MessageListener {

    private List<String> logs;
    private int nrofDropped;
    private int nrofRemoved;

    /**
     * Constructor
     */
    public DroppedPacketReport() {
        init();
    }

    @Override
    protected void init() {
        super.init();
        
        logs = new ArrayList<String>();

        nrofDropped = 0;
        nrofRemoved = 0;

    }

    @Override
    public void messageDeleted(Message m, DTNHost where, boolean dropped) {
        String status = dropped ? "DROPPED" : "REMOVED";

        if (dropped) {
            nrofDropped++;
        } else {
            nrofRemoved++;
        }

        logs.add(String.format("%.2f %s %s %s", 
            getSimTime(),
            where,
            m.getId(),
            status
        ));
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
        write("# Dropped Packet Report " + getScenarioName());
        write("# TOTAL DROPPED: " + nrofDropped);
        write("# TOTAL REMOVED: " + nrofRemoved);
        write("# ------------------------------------");
        write("# Time | Node | MsgID | Reason");
        
        for(String log : logs) {
            write(log);
        }

        super.done();
    }
}
