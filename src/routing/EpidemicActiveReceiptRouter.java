package routing;

import core.DTNHost;
import core.Message;
import core.Settings;

public class EpidemicActiveReceiptRouter extends EpidemicRouter {
    
    public static final String ACK_PREFIX = "ACK_";
    private static final int ACK_SIZE = 1;

    public EpidemicActiveReceiptRouter(Settings s) {
        super(s);
    }

    public EpidemicActiveReceiptRouter(EpidemicActiveReceiptRouter r) {
        super(r);
    }

    @Override
    public EpidemicActiveReceiptRouter replicate() {
        return new EpidemicActiveReceiptRouter(this);
    }

    @Override
    public int receiveMessage(Message m, DTNHost from) {
        String msgId = m.getId();

        // check if message is ACK message
        if (msgId.startsWith(ACK_PREFIX)) {
            String originalMsgId = msgId.substring(ACK_PREFIX.length());
            
            // if this node still has original message, remove that message 
            if (this.hasMessage(originalMsgId)) {
                this.deleteMessage(originalMsgId, false);
   
            }

            return super.receiveMessage(m, from);
        } else {
            // anticipate the re-infection
            if (this.hasMessage(ACK_PREFIX + msgId)) {
                return DENIED_OLD;
            }
        }
    
        return super.receiveMessage(m, from);
    }

    @Override
    public Message messageTransferred(String id, DTNHost from) {
        Message m = super.messageTransferred(id, from);

        // make sure message success to retrieve and this node is final destination.
        if (m != null && m.getTo() == getHost()) { 
            generateActiveReceipt(m);
        }

        return m;
    }


    private void generateActiveReceipt(Message originalMsg) {
        String ackId = ACK_PREFIX + originalMsg.getId();

        // prevent node to create duplicate ACK
        if (!this.hasMessage(ackId)) {
            // Create new ACK message
            // Source: this node (final destination / U)
            // Destination: original source node 
            Message ackMsg = new Message(this.getHost(), originalMsg.getFrom(), ackId, ACK_SIZE);

            // Put the message in to buffer node
            // Because this is EpidemicRouter, update() default function will automate
            // Broadcast this ACK to each other neighbors that it found.
            // this.createNewMessage(ackMsg);
            this.createNewMessage(ackMsg);
        }
    }
}
