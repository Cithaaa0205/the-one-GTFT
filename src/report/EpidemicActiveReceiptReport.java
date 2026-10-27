package report;

import core.DTNHost;
import core.Message;
import routing.EpidemicActiveReceiptRouter;

/**
 * Report statistik pesan (MessageStatsReport) yang dimodifikasi untuk 
 * mengabaikan pesan Active Receipt (ACK) agar metrik murni hanya untuk data.
 */
public class EpidemicActiveReceiptReport extends MessageStatsReport {

    public EpidemicActiveReceiptReport() {
        super();
    }

    /**
     * Method helper untuk mengecek apakah pesan adalah ACK
     */
    private boolean isAck(Message m) {
        return m.getId().startsWith(EpidemicActiveReceiptRouter.ACK_PREFIX);
    }

    @Override
    public void newMessage(Message m) {
        if (isAck(m)) return;
        
        super.newMessage(m);
    }

    @Override
    public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
        if (isAck(m)) return;
        
        super.messageTransferStarted(m, from, to);
    }

    @Override
    public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean firstDelivery) {
        if (isAck(m)) return;
        
        super.messageTransferred(m, from, to, firstDelivery);
    }

    @Override
    public void messageDeleted(Message m, DTNHost host, boolean dropped) {
        if (isAck(m)) return;
        
        super.messageDeleted(m, host, dropped);
    }

    @Override
    public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
        if (isAck(m)) return;
        
        super.messageTransferAborted(m, from, to);
    }
}