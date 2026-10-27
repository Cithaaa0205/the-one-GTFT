package routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;

public class SprayAndFocusTransitivityRouter extends ActiveRouter {
    public static final String NROF_COPIES_S = "nrofCopies";
    public static final String BINARY_MODE_S = "binaryMode";
    public static final String P_INIT_S = "probInit";
    public static final String GAMMA_S = "gamma";
    public static final String BETA_S = "beta";
    
    public static final String SNFT_NS = "SprayAndFocusTransitivityRouter";
    public static final String MSG_COUNT_PROP = SNFT_NS + "." + "copies";
    
    // protected static final double THRESHOLD = 0.1;
    
    protected int initalNrofCopies;
    protected boolean isBinary;
    protected double probInit;
    protected double gamma;
    protected double beta;
    
    protected Map<DTNHost, Double> preds;
    protected Map<DTNHost, Double> lastSeenTime;
    
    public SprayAndFocusTransitivityRouter(Settings s) {
        super(s);
        Settings snftSettings = new Settings(SNFT_NS);
        
        initalNrofCopies = snftSettings.getInt(NROF_COPIES_S);
        isBinary = snftSettings.getBoolean(BINARY_MODE_S);
        probInit = snftSettings.getDouble(P_INIT_S);
        gamma = snftSettings.getDouble(GAMMA_S);
        beta = snftSettings.getDouble(BETA_S);
        
        preds = new HashMap<DTNHost, Double>();
        lastSeenTime = new HashMap<DTNHost, Double>();
    }
    
    public SprayAndFocusTransitivityRouter(SprayAndFocusTransitivityRouter r) {
        super(r);
        
        initalNrofCopies = r.initalNrofCopies;
        isBinary = r.isBinary;
        probInit = r.probInit;
        gamma = r.gamma;
        beta = r.beta;
        
        preds = new HashMap<DTNHost, Double>(r.preds);
        lastSeenTime = new HashMap<DTNHost, Double>(r.lastSeenTime);
    }
    
    protected double getPredFor(DTNHost host) {
        if (!preds.containsKey(host) || !lastSeenTime.containsKey(host)) {
            return 0d;
        }
        
        double oldProb = preds.get(host);
        double lastTime = lastSeenTime.get(host);
        
        double timeDiff = SimClock.getTime() - lastTime;
        
        if (timeDiff < 0) {
            return oldProb;   
        }
        
        // P(a,b)new = P(a,b)old * gamma^k (k is last time contact each other node)
        return oldProb * Math.pow(gamma, timeDiff);
    }
    
    // direct delivery and using p_init to make new or increased probs each other node
    // P(a,b) = P(a,b)old + (1 - P(a,b)old) * P(init) 
    @Override
    public void changedConnection(Connection con) {
        super.changedConnection(con);
        
        if (con.isUp()) {
            DTNHost otherNode = con.getOtherNode(getHost());
            
            double oldPred = getPredFor(otherNode);
            double newPred = oldPred + (1 - oldPred) * probInit;
            
            preds.put(otherNode, newPred);
            lastSeenTime.put(otherNode, SimClock.getTime());
            
            SprayAndFocusTransitivityRouter neighborRouter = 
            (SprayAndFocusTransitivityRouter) otherNode.getRouter();
            
            Map<DTNHost, Double> neighborPreds = neighborRouter.preds; // snapshot
            
            for (DTNHost hostDestination : neighborPreds.keySet()) {
                if (hostDestination == getHost()) {
                    continue;
                }
                
                // P(Me -> destination) A -> C
                double myProbToDest = getPredFor(hostDestination);
                
                // P(my neighbor -> destination) B -> C
                double neighborToDest = neighborRouter.getPredFor(hostDestination);
                
                // P(Me -> my neighbor) A -> B
                double myProbToNeighbor = getPredFor(otherNode);
                
                // P(a,c) = P(a,b) * P(b, c) * beta 
                double transitiveProb = myProbToNeighbor * neighborToDest * beta;
                
                if (transitiveProb > myProbToDest) {
                    preds.put(hostDestination, transitiveProb);   
                }
                
                // P(a,c) = P(a,b) * P(b, c) * beta 
                // double transitiveProb = myProbToNeighbor * neighborToDest * beta;
                // P(a,c)​=P(a,c)old ​​+ (1−P(a,c)old​​) × P(a,b) ​× P(b,c) ​× β
                // double probTransitiveImpact = myProbToDest + (1 - myProbToDest) * transitiveProb;
                
                // preds.put(hostDestination, probTransitiveImpact);
                // lastSeenTime.put(hostDestination, SimClock.getTime());
            }
        }
    }
    
    @Override
    public int receiveMessage(Message msg, DTNHost host) {
        return super.receiveMessage(msg, host);
    } 
    
    @Override
    public boolean createNewMessage(Message msg) {
        makeRoomForNewMessage(msg.getSize());
        
        msg.setTtl(this.msgTtl);
        msg.addProperty(MSG_COUNT_PROP, initalNrofCopies);
        addToMessages(msg, true);
        return true;
    }
    
    @Override
    public Message messageTransferred(String id, DTNHost host) {
        Message msg = super.messageTransferred(id, host);
        Integer nc = (Integer) msg.getProperty(MSG_COUNT_PROP);
        
        if (isBinary) {
            nc = (int)Math.ceil(nc/2.0);
        } else {
            nc = 1;
        }
        
        msg.updateProperty(MSG_COUNT_PROP, nc);
        return msg;
    }
    
    // forwarding core (transivity forwarding) with using beta
    // P(a,c) = P(a,b) * P(b, c) * beta 
    // @Override
    // protected int startTransfer(Message msg, Connection con) {
    //     DTNHost otherNode = con.getOtherNode(getHost());
    //     Integer nc = (Integer) msg.getProperty(MSG_COUNT_PROP);
    
    //     if (nc == null) {
    //         return super.startTransfer(msg, con);
    //     }
    
    //     if (nc > 1) {
    //         return super.startTransfer(msg, con);
    //     } else {
        //         // focus phase with transitivity utility based
    //         if (msg.getTo() == otherNode) {
    //             return super.startTransfer(msg, con);
    //         }
    
    //         double myUtil = getPredFor(msg.getTo());
    
    //         SprayAndFocusTransitivityRouter neighborRouter = (SprayAndFocusTransitivityRouter) otherNode.getRouter();
    //         double neighborUtil = neighborRouter.getPredFor(msg.getTo());
    
    //         if (neighborUtil > myUtil) {
    //             return super.startTransfer(msg, con);
    //         }
    //     }
    
    //     return TRY_LATER_BUSY;
    // }
    
    @Override
    protected Message tryAllMessages(Connection con, List<Message> messages) {
        DTNHost other = con.getOtherNode(getHost());
        
        for (Message msg : messages) {
            Integer nc = (Integer) msg.getProperty(MSG_COUNT_PROP);
            if (nc == null) {
                if (super.startTransfer(msg, con) == RCV_OK) {
                    return msg;
                }
            }
            
            if (nc > 1) {
                if (super.startTransfer(msg, con) == RCV_OK) {
                    return msg;
                }
            } else {
                if (msg.getTo() == other) {
                    if (super.startTransfer(msg, con) == RCV_OK) {
                        return msg;
                    }
                }
                
                SprayAndFocusTransitivityRouter neighborRouter = (SprayAndFocusTransitivityRouter) other.getRouter();
                
                double myProbsToDest = getPredFor(msg.getTo());
                double neighborProbsToDest = neighborRouter.getPredFor(msg.getTo());
                
                if (neighborProbsToDest > myProbsToDest) {
                    if (super.startTransfer(msg, con) == RCV_OK) {
                        return msg;
                    }
                }
            }
            
        }
        
        return null;
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
        
        List<Message> messages = new ArrayList<Message>(getMessageCollection());
        messages = sortByQueueMode(messages);
        
        this.tryMessagesToConnections(messages, getConnections());
    }
    
    @Override
    protected void transferDone(Connection con) {
        String msgId = con.getMessage().getId();
        Message msg = getMessage(msgId);
        
        if (msg == null) {
            return;
        }
        
        Integer nc = (Integer)msg.getProperty(MSG_COUNT_PROP);
        if (isBinary) {
            nc /= 2;
        } else {
            nc--;
        }
        
        msg.updateProperty(MSG_COUNT_PROP, nc);
        
        if (nc < 1) {
            deleteMessage(msgId, false);
        }
    }
    
    @Override
    public SprayAndFocusTransitivityRouter replicate() {
        return new SprayAndFocusTransitivityRouter(this);
    }
}
