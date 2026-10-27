package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.Tuple;

public class ProphetRouterGRTR extends ProphetRouter {

    public ProphetRouterGRTR(Settings s) {
        super(s);
    }

    public ProphetRouterGRTR(ProphetRouterGRTR r) {
        super(r);
    }

    @Override
    public void update() {
        super.update();

        if (!canStartTransfer() || isTransferring()) {
            return;
        }

        if (exchangeDeliverableMessages() != null) {
            return;
        }

        tryOtherMessages();
    }
    
    protected Tuple<Message, Connection> tryOtherMessages() {
        
        List<Tuple<Message, Connection>> candidates = new ArrayList<>();
        Collection<Message> messages = getMessageCollection();
        
        for (Connection con : getConnections()) {
            DTNHost other = con.getOtherNode(getHost());
            ProphetRouter otherRouter = (ProphetRouter) other.getRouter();
            
            if (otherRouter.isTransferring()) {
                continue;
            }
            
            for (Message msg : messages) {
                if (otherRouter.hasMessage(msg.getId())) {
                    continue;
                }
                
                if (otherRouter.getPredFor(msg.getTo()) > getPredFor(msg.getTo())) {
                    candidates.add(new Tuple<>(msg, con));
                }
            }
        }
        
        if (candidates.isEmpty()) {
            return null;
        }
        
        return tryMessagesForConnected(candidates);
    }

    @Override
    public ProphetRouterGRTR replicate() {
        return new ProphetRouterGRTR(this);
    }
}