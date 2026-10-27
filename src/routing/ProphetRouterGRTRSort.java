package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.Tuple;

public class ProphetRouterGRTRSort extends ProphetRouter {

    public ProphetRouterGRTRSort(Settings s) {
        super(s);
    }

    public ProphetRouterGRTRSort(ProphetRouterGRTRSort r) {
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

    /**
     * Custom tryOtherMessages dengan GRTR (ascending sort)
     */
    protected Tuple<Message, Connection> tryOtherMessages() {
        List<Tuple<Message, Connection>> messages = new ArrayList<>();

        Collection<Message> messageCollection = getMessageCollection();

        for (Connection con : getConnections()) {
            DTNHost other = con.getOtherNode(getHost());
            ProphetRouter otherRouter = (ProphetRouter) other.getRouter();

            if (otherRouter.isTransferring()) {
                continue;
            }

            for (Message msg : messageCollection) {
                if (otherRouter.hasMessage(msg.getId())) {
                    continue;
                }

                // hanya kirim kalau neighbor lebih "bagus"
                if (otherRouter.getPredFor(msg.getTo()) > getPredFor(msg.getTo())) {
                    messages.add(new Tuple<>(msg, con));
                }
            }
        }

        if (messages.isEmpty()) {
            return null;
        }

        Collections.sort(messages, new TupleGRTRSortComparator());

        return tryMessagesForConnected(messages);
    }

    /**
     * Comparator GRTR (ascending)
     */
    private class TupleGRTRSortComparator implements Comparator<Tuple<Message, Connection>> {
        @Override
        public int compare(Tuple<Message, Connection> tuple1, Tuple<Message, Connection> tuple2) {
            double p1 = ((ProphetRouter)tuple1.getValue().getOtherNode(getHost()).getRouter()).getPredFor(tuple1.getKey().getTo());
            double p2 = ((ProphetRouter)tuple2.getValue().getOtherNode(getHost()).getRouter()).getPredFor(tuple2.getKey().getTo());
     
            if (p1 - p2 == 0) {
                return compareByQueueMode(tuple1.getKey(), tuple2.getKey());
            } else if (p1 - p2 < 0) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    @Override
    public ProphetRouterGRTRSort replicate() {
        return new ProphetRouterGRTRSort(this);
    }
}