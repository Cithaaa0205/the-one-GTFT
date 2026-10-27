package routing.policies.queue;

import core.Message;
import routing.ActiveRouter;
import java.util.Collection;

public class OldestMessage implements QueuePolicy {
    @Override
    public Message getMessageToRemove(ActiveRouter router, boolean excludeMsgBeingSent) {
        Collection<Message> messages = router.getMessageCollection();
        Message oldest = null;
        
        for (Message m : messages) {
            if (excludeMsgBeingSent && router.isSending(m.getId())) {
                continue; 
            }
            if (oldest == null || oldest.getReceiveTime() > m.getReceiveTime()) {
                oldest = m;
            }
        }
        return oldest;
    }
}