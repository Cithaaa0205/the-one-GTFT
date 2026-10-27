package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.Tuple;

public class ProphetRouter extends ActiveRouter {
    public static final String PROPHET_NS = "ProphetRouter";
    public static final String SECOND_IN_UNIT = "secondsInTimeUnit";
    public static final String BETA_S = "beta";

    public static final double P_INIT = 0.75;
    public static final double DEFAULT_BETA = 0.25;
    public static final double GAMMA = 0.98;

    private int secondsInTimeUnit;
    private double beta;
    private Map<DTNHost, Double> preds;
    private double lastAgeUpdate;

    private Set<DTNHost> neighborLists;

    public ProphetRouter(Settings s) {
        super(s);
        Settings prophetSettings = new Settings(PROPHET_NS);
        secondsInTimeUnit = prophetSettings.getInt(SECOND_IN_UNIT);
        if (prophetSettings.contains(BETA_S)) {
            beta = prophetSettings.getDouble(BETA_S);
        } else {
            beta = DEFAULT_BETA;
        }
        initPreds();
    }

    public ProphetRouter(ProphetRouter r) {
        super(r);
        secondsInTimeUnit = r.secondsInTimeUnit;
        beta = r.beta;
        neighborLists = new HashSet<>();
        initPreds();
    }

    public Set<DTNHost> getNeighborList() {
        return neighborLists;
    }

    private void initPreds() {
        preds = new HashMap<>();
    }

    private Map<DTNHost, Double> getDeliveryPreds() {
        ageDeliveryPreds();
        return this.preds;
    }

    @Override
    public void changedConnection(Connection con) {
        if (con.isUp()) {
            DTNHost otherNode = con.getOtherNode(getHost());
            updateDeliveryPreds(otherNode);
            updateTransitivePreds(otherNode);
            neighborLists.add(otherNode);
        } else {
            DTNHost otherNode = con.getOtherNode(getHost());
            neighborLists.remove(otherNode);
        }
    }

    private void ageDeliveryPreds() {
        double timeDiff = (SimClock.getTime() - lastAgeUpdate) / secondsInTimeUnit;
        if (timeDiff == 0) {
            return;
        } 

        double mult = Math.pow(GAMMA, timeDiff);

        for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
            e.setValue(e.getValue() * mult);
        }

        lastAgeUpdate = SimClock.getTime();
    }

    public double getPredFor(DTNHost host) {
        ageDeliveryPreds();
        return preds.getOrDefault(host, 0d);
    }

    private void updateDeliveryPreds(DTNHost host) {
        double pOld = getPredFor(host);
        double pNew = pOld + (1 - pOld) * P_INIT;
        preds.put(host, pNew);
    }

    private void updateTransitivePreds(DTNHost host) {
        ProphetRouter otherRouter = (ProphetRouter) host.getRouter();
        assert otherRouter instanceof ProphetRouter : "PRoPHET only works with other routers of same type"; 
        Map<DTNHost, Double> neighborList = otherRouter.getDeliveryPreds();
        double myProbsToNeighbor = getPredFor(host);

        for(Map.Entry<DTNHost, Double> dest : neighborList.entrySet()) {
            if (dest.getKey() == getHost()) {
                continue;
            }

            double myProbsToDest = getPredFor(dest.getKey());
            double pNew = myProbsToDest + (1 - myProbsToDest) * myProbsToNeighbor * dest.getValue() * beta;

            preds.put(dest.getKey(), pNew);
        }
    }

    private Tuple<Message, Connection> tryOtherMessages() {
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
                
                if (otherRouter.getPredFor(msg.getTo()) > getPredFor(msg.getTo())) {
                    messages.add(new Tuple<>(msg, con));
                }
            }
        }

        if (messages.isEmpty()) {
            return null;
        }

        Collections.sort(messages, new TupleComparator());
        return tryMessagesForConnected(messages);
    }

    private class TupleComparator implements Comparator<Tuple<Message, Connection>> {
        @Override
        public int compare(Tuple<Message, Connection> tuple1, Tuple<Message, Connection> tuple2) {
            double p1 = ((ProphetRouter) tuple1.getValue().getOtherNode(getHost()).getRouter()).getPredFor(tuple1.getKey().getTo());
            double p2 = ((ProphetRouter) tuple2.getValue().getOtherNode(getHost()).getRouter()).getPredFor(tuple2.getKey().getTo());
     
            // Replaced manual subtraction with Double.compare for double safety
            int comp = Double.compare(p2, p1);
            if (comp == 0) {
                return compareByQueueMode(tuple1.getKey(), tuple2.getKey());
            } 
            return comp;
        }
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

    @Override
    public RoutingInfo getRoutingInfo() {
        ageDeliveryPreds();
        RoutingInfo top = super.getRoutingInfo();
        RoutingInfo ri = new RoutingInfo(preds.size() + " delivery prediction(s)");

        for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
            ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f", e.getKey(), e.getValue())));
        }

        top.addMoreInfo(ri);
        return top;
    }

    @Override
    public ProphetRouter replicate() {
        return new ProphetRouter(this);
    }
}