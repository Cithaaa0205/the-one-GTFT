package routing.policies.queue;

import core.Message;
import routing.ActiveRouter;
import java.util.Collection;

public class LowestTtlMessage implements QueuePolicy {
    @Override
    public Message getMessageToRemove(ActiveRouter router, boolean excludeMsgBeingSent) {
        Collection<Message> messages = router.getMessageCollection();
        Message lowest = null;

        for (Message m : messages) {
            if (excludeMsgBeingSent && router.isSending(m.getId())) {
                continue;
            }
            if (lowest == null || lowest.getTtl() < m.getTtl()) {
                lowest = m;
            }
        }
        return lowest;
    }
}