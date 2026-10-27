package report;

import core.DTNHost;
import core.Message;
import core.MessageListener;

public class ForensicDropReport extends Report implements MessageListener {
    public static final String HEADER = "Time, NodeID, X, Y, MessageId, Size, HopCount, LifeTime, Reason";
    
    public ForensicDropReport() {
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

        String reasonDrop = "";
        double lifeTime = getSimTime() - m.getCreationTime();

        if (dropped) {
            reasonDrop = "FULL_BUFFER"; 
        } else if (!dropped && lifeTime >= m.getTtl()) {
            reasonDrop = "TTL_EXP";
        } else {
            reasonDrop = "ACK";  
        }

        write(String.format("%.2f, %s, %.2f, %.2f, %s, %d, %d, %.2f, %s", 
            getSimTime(),
            where,
            where.getLocation().getX(),
            where.getLocation().getY(),
            m.getId(),
            m.getSize(),
            m.getHopCount(),
            getSimTime() - m.getCreationTime(),
            reasonDrop
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
}
