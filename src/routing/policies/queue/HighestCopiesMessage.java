package routing.policies.queue;

import core.Message;
import routing.ActiveRouter;
import java.util.Collection;

public class HighestCopiesMessage implements QueuePolicy {
    @Override
    public Message getMessageToRemove(ActiveRouter router, boolean excludeMsgBeingSent) {
        Collection<Message> messages = router.getMessageCollection();
        Message highest = null;

        for (Message m : messages) {
            if (excludeMsgBeingSent && router.isSending(m.getId())) {
                continue;
            }
            if (highest == null || highest.getHopCount() > m.getHopCount()) {
                highest = m;
            }
        }
        return highest;
    }
}