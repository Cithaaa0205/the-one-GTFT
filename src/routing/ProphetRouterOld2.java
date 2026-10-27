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

public class ProphetRouterOld2 extends ActiveRouter {
    public static final String P_INIT_S = "probInit";
    public static final String GAMMA_S =  "gamma";
    public static final String BETA_S =  "beta";
    public static final String PROPHET_NS = "ProphetRouter";
    
    protected double probInit;
    protected double gamma;
    protected double beta;
    
    protected Map<DTNHost, Double> probs;
    protected Map<DTNHost, Double> lastSeenTime;
    
    public ProphetRouterOld2(Settings s) {
        super(s);
        Settings prophetSettings = new Settings(PROPHET_NS);
        
        probInit = prophetSettings.getDouble(P_INIT_S);
        gamma = prophetSettings.getDouble(GAMMA_S);
        beta = prophetSettings.getDouble(BETA_S);
        
        probs = new HashMap<DTNHost, Double>();
        lastSeenTime = new HashMap<DTNHost, Double>();
    }
    
    public ProphetRouterOld2(ProphetRouterOld2 r) {
        super(r);
        probInit = r.probInit;
        gamma = r.gamma;
        beta = r.beta;
        
        probs = new HashMap<DTNHost, Double>(r.probs);
        lastSeenTime = new HashMap<DTNHost, Double>(r.lastSeenTime);
    }
    
    // P(a,b)​=P(a,b)old​​×γk
    protected double getPredFor(DTNHost host) {
        if (!probs.containsKey(host) || !lastSeenTime.containsKey(host)) {
            return 0d;
        }
        
        double probOld = probs.get(host);
        double lastSeen = lastSeenTime.get(host);
        
        double timeDiff = SimClock.getTime() - lastSeen;
        
        double lastAge = probOld * Math.pow(gamma, timeDiff);
        
        return lastAge;
    }
    
    @Override
    public void changedConnection(Connection con) {
        super.changedConnection(con);
        
        if (con.isUp()) {
            // P(a,b)​=P(a,b)old​​+(1−P(a,b)old​​)×Pinit​
            DTNHost other = con.getOtherNode(getHost());
            double probOld = getPredFor(other);
            double probNew = probOld + (1 - probOld) * probInit;
            
            probs.put(other, probNew);
            lastSeenTime.put(other, SimClock.getTime());
            
            ProphetRouterOld2 neighborRouter = (ProphetRouterOld2) other.getRouter();
            assert neighborRouter instanceof ProphetRouterOld2 : "PRoPHET only works with other routers of same type";

            Map<DTNHost, Double> neighborProbs = neighborRouter.probs; // snapshot
            
            for(DTNHost destination : neighborProbs.keySet()) {
                if (destination == getHost()) {
                    continue;
                }
                
                double myProbsToDest = getPredFor(destination);                
                double neighborProbsToDest = neighborRouter.getPredFor(destination);
                double myProbsToNeighbor = getPredFor(other);
                
                // P(a,c) = P(a,b) * P(b, c) * beta 
                double transitive = myProbsToNeighbor * neighborProbsToDest * beta;
                
                // P(a,c)​=P(a,c)old ​​+ (1−P(a,c)old​​) × P(a,b) ​× P(b,c) ​× β
                double probsTransitiveImpact = myProbsToDest + (1 - myProbsToDest) * transitive;
                
                probs.put(destination, probsTransitiveImpact);
                lastSeenTime.put(destination, SimClock.getTime());
            }
        }
    }
    
    @Override
    protected Message tryAllMessages(Connection con, List<Message> messages) {
        for(Message msg : messages) {
            DTNHost other = con.getOtherNode(getHost());
            if (msg.getTo() == other) {
                if (super.startTransfer(msg, con) == RCV_OK) {
                    return msg;
                }
            }
            
            ProphetRouterOld2 neighborRouter = (ProphetRouterOld2) other.getRouter();
            assert neighborRouter instanceof ProphetRouterOld2 : "PRoPHET only works with other routers of same type"; 
            
            double myProbsToDest = getPredFor(msg.getTo());
            double neighborProbsToDest = neighborRouter.getPredFor(msg.getTo());
            
            if (neighborProbsToDest > myProbsToDest) {
                if (super.startTransfer(msg, con) == RCV_OK) {
                    return msg;
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
        
        tryMessagesToConnections(messages, getConnections());
    }
    
    @Override
    public ProphetRouterOld2 replicate() {
        return new ProphetRouterOld2(this);
    }
}
