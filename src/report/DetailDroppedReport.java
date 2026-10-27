package report;

import core.DTNHost;
import core.Message;
import core.MessageListener;

public class DetailDroppedReport extends Report implements MessageListener {
    private static final String HEADER = "[Waktu] [NodeID] [MessageID] [Reason] [BufferRemainingNode] [BufferOccupancyNode(%)]";
    
    public DetailDroppedReport() {
        init();
    }

    @Override
    protected void init() {
        super.init();
        write(HEADER);
    }

    @Override
    public void messageDeleted(Message m, DTNHost where, boolean dropped) {
        if (getSimTime() < warmupTime) {
            return;
        }

        String reason = dropped ? "FULL_BUFFER" : "TTL_EXPIRED";
        
        write(String.format("%s %s %s %s %d %.2f", 
            getSimTime(),
            where,
            m.getId(),
            reason,
            where.getRouter().getFreeBufferSize(),
            where.getBufferOccupancy()
        ));
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

    // @Override
    // public void done() {
        // for (String log : logs) {
        //     write(log);
        // }
        // super.done();
    // }
}
