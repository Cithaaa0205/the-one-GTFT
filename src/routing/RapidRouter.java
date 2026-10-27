package routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.Tuple;

public class RapidRouter extends ActiveRouter {
    public static final String RAPID_NS = "RapidRouter";
    public static final String METRIC_S = "metric";
    public static final String BANDWIDTH_S = "transmitSpeed";
    public static final int METRIC_AVG_DELAY = 1;
    public static final int METRIC_MISSED_DEADLINE = 2;
    public static final int METRIC_MAX_DELAY = 3; 

    private int metric;

    private Map<String, Set<DTNHost>> packetManifest;
    private Map<String, Double> deliveredList;
    private Map<DTNHost, Map<Double, Integer>> interContactTime;
    private Map<DTNHost, Integer> transferOpportunity;
    private Map<String, Double> delayEstimates;

    private int bandwidth;

    public RapidRouter(Settings s) {
        super(s);
        Settings rapidSettings = new Settings(RAPID_NS);
        if (rapidSettings.contains(METRIC_S)) {
            if (rapidSettings.getInt(METRIC_S) >= 1 && rapidSettings.getInt(METRIC_S) <= 3) {
                metric = rapidSettings.getInt(METRIC_S);
            }
        }

        if (s.contains(BANDWIDTH_S)) {
            bandwidth = s.getInt(BANDWIDTH_S);    
        }

        initStructure();
    }

    public RapidRouter(RapidRouter r) {
        super(r);
        metric = r.metric;
        bandwidth = r.bandwidth;
        initStructure();
    }

    private void initStructure() {
        packetManifest = new HashMap<>();
        deliveredList = new HashMap<>();
        interContactTime = new HashMap<>();
        delayEstimates = new HashMap<>();
        transferOpportunity = new HashMap<>();
    }

    @Override
    public void changedConnection(Connection con) {
        DTNHost oth = con.getOtherNode(getHost());
        
        if (con.isUp()) {
            exchangeMetaData(oth);
        } else {
            transferOpportunity.put(oth, con.getTotalBytesTransferred());
        }
        
    }

    private void exchangeMetaData(DTNHost host) {
        RapidRouter othRouter = (RapidRouter) host.getRouter();
        assert othRouter instanceof RapidRouter : "RAPID is works with other routers of same type";

        updateInterMeeting(host, othRouter);

        exchangeACK(host, othRouter);
        exchangePacketManifest(host, othRouter);
        exchangeDelayEstimates(host, othRouter);
    }

    private void updateInterMeeting(DTNHost host, RapidRouter hostRouter) {
        double now = SimClock.getTime();

        if (interContactTime.containsKey(host)) {
            double old = interContactTime.get(host).keySet().stream().findFirst().orElse(0d);
            double timeDiff = now - old;

            if (timeDiff == 0) {
                return;
            }

            int freq = interContactTime.get(host).values().stream().findFirst().orElse(0);

            int f;
            if (freq == 0) {
                f = 1;
            } else {
                f = freq;
            }
        
            interContactTime.computeIfAbsent(host, v -> new HashMap<>()).put(timeDiff / f, f++);
        } else {
            for (Map.Entry<DTNHost, Map<Double, Integer>> dest : hostRouter.interContactTime.entrySet()) {
                if (interContactTime.containsKey(dest.getKey())) {
                    double eXY = interContactTime.get(dest.getKey()).keySet().stream().findFirst().orElse(0d);
                    double eYZ = hostRouter.interContactTime.get(dest.getKey()).keySet().stream().findFirst().orElse(0d);
                    
                    double eXZ = eXY + eYZ;

                    interContactTime.computeIfAbsent(host, v -> new HashMap<>()).put(eXZ, 1);
                }
            }
        }
    }

    private void exchangeACK(DTNHost host, RapidRouter hostRouter) {
        // Map<String, Set<DTNHost>>
        for (String msgId : hostRouter.deliveredMessages.keySet()) {
            // [m1 => [h1, h3, h4]]
            Message msg = getMessage(msgId);
            double util = getMarginalUtility(msg, host);
            deliveredList.put(msgId, util);
        }
    
        deliveredList.entrySet().stream().sorted(Map.Entry.comparingByValue()).collect(Collectors.toMap(
            Map.Entry::getKey,
            Map.Entry::getValue,
            (e1, e2) -> e1,
            LinkedHashMap::new
        ));
    }

    private void exchangePacketManifest(DTNHost host, RapidRouter hostRouter) {
        // Map<String, Set<DTNHost>>
        for (Message msg : hostRouter.getMessageCollection()) {
            packetManifest.computeIfAbsent(msg.getId(), v -> new HashSet<>()).add(host);
        }
    }
    
    private void exchangeDelayEstimates(DTNHost host, RapidRouter hostRouter) {
        // Map<String, Double>
        for (Map.Entry<String,Double> e: hostRouter.delayEstimates.entrySet()) {
            delayEstimates.merge(e.getKey(), e.getValue(), Math::min);
        }
    }

    private double estimateDelay(Message msg) {
        // T(i)
        double deltaMessage = SimClock.getTime() - msg.getTtl();
        double estimateToDest = 0d;

        for (Map.Entry<String, Set<DTNHost>> e : packetManifest.entrySet()) {
            if (e.getKey() == msg.getId()) {
                Set<DTNHost> hosts = e.getValue();
        
                for (DTNHost h : hosts) {
                    if (msg.getTo() == h) {
                        continue;
                    }
                    // A(i)
                    estimateToDest += 1/interContactTime.get(h).keySet().iterator().next();
                }
            }
        }

        // D(i) = T(i) + A(i)
        double finalEstimation = deltaMessage + estimateToDest;

        return finalEstimation;
    }

    private double getMarginalUtility(Message msg, DTNHost host) {
        double myD = delayEstimates.getOrDefault(msg.getId(), estimateDelay(msg));
        double othD = ((RapidRouter)host.getRouter()).delayEstimates.getOrDefault(msg.getId(), estimateDelay(msg));

        switch (metric) {
            case METRIC_AVG_DELAY:
                return myD - othD;        
            case METRIC_MISSED_DEADLINE:
                double deadline = msg.getTtl();
                double pOld = (myD > deadline) ? 1 : 0;
                double pNew = (othD > deadline) ? 1 : 0;
                return pOld - pNew;
            case METRIC_MAX_DELAY:
                return myD - othD;
            default:
                return myD - othD;
        }
    }

    private List<Tuple<Message, Double>> getUtilities() {
        List<Tuple<Message, Double>> list = new ArrayList<>();
        for (Message msg : getMessageCollection()) {
            for (Connection con : getConnections()) {
                DTNHost oth = con.getOtherNode(getHost());
                if (packetManifest.containsKey(msg.getId()) && packetManifest.get(msg.getId()).contains(oth)) {
                    continue;
                }

                double util = getMarginalUtility(msg, oth);

                if (util > 0) {
                    list.add(new Tuple<Message,Double>(msg, util));
                }
            }
        }

        Collections.sort(list, new UtilComparator());
        return list;
    }

    private List<Message> knapsackSelection(List<Tuple<Message, Double>> scoredMsg, double capacity) {
        List<Message> res = new ArrayList<>();
        double used = 0;

        for (Tuple<Message, Double> sm : scoredMsg) {
            double size = sm.getKey().getSize();
            if (used + size <= capacity) {
                used += size;
                res.add(sm.getKey());
            }
        }

        return res;
    }

    @Override
    public void update() {
        super.update();

        if (!canStartTransfer() || isTransferring()) {
            return;
        }

        List<Message> selected = knapsackSelection(getUtilities(), transferOpportunity.get(packetManifest));

        if (!selected.isEmpty()) {
            tryMessagesToConnections(selected, getConnections());
        }
    }

    private class UtilComparator implements Comparator<Tuple<Message, Double>> {
        @Override
        public int compare(Tuple<Message, Double> util1, Tuple<Message, Double> util2) {
            double u1 = util1.getValue();
            double u2 = util2.getValue();

            if (u2 - u1 == 0) {
                return compareByQueueMode(util1.getKey(), util2.getKey());   
            } else if (u2 - u1 < 0) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    @Override
    protected boolean makeRoomForMessage(int size) {
        for (String msgId : deliveredList.keySet()) {
            Message msg = getMessage(msgId);
            
            if (!isDeliveredMessage(msg)) {
                continue;
            }

            size += msg.getSize();
        }

        return super.makeRoomForMessage(size);
    }

    @Override
    public RapidRouter replicate() {
        return new RapidRouter(this);
    }
}
