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

public class SprayAndFocusLETRouter extends ActiveRouter {
    public static final String NROF_COPIES_S = "nrofCopies";
    public static final String BINARY_MODE_S = "binaryMode";
    public static final String SPRAYANDFOCUS_NS = "SprayAndFocusLETRouter";
    public static final String MSG_COUNT_PROP = SPRAYANDFOCUS_NS + "." + "copies";

    protected int initialNrofCopies; 
    protected boolean isBinary;
    protected Map<DTNHost, Double> lastEncounterTime;
    
    public SprayAndFocusLETRouter(Settings s) {
        super(s);
        
        Settings snfSettings= new Settings(SPRAYANDFOCUS_NS);

        initialNrofCopies = snfSettings.getInt(NROF_COPIES_S);
        isBinary = snfSettings.getBoolean(BINARY_MODE_S);
        lastEncounterTime = new HashMap<DTNHost, Double>();
    }

    public SprayAndFocusLETRouter(SprayAndFocusLETRouter r) {
        super(r);
        initialNrofCopies = r.initialNrofCopies;
        isBinary = r.isBinary;
        lastEncounterTime = new HashMap<DTNHost, Double>(r.lastEncounterTime);
    }

    @Override
    public void changedConnection(Connection con) {
        super.changedConnection(con);
        
        if (con.isUp()) {
            DTNHost otherNode = con.getOtherNode(getHost());
            lastEncounterTime.put(otherNode, SimClock.getTime());
        }
    }

    protected double getUtility(DTNHost host, DTNHost dest) {
        if (host.equals(dest)) {
           return Double.MAX_VALUE; 
        }

        // my utility count
        if (host == getHost()) {
            if (lastEncounterTime.containsKey(dest)) {
                return lastEncounterTime.get(dest);
            }
        }
        
        // neighbor / other node utilities
        else if(host.getRouter() instanceof SprayAndFocusLETRouter) {
            SprayAndFocusLETRouter nHost = (SprayAndFocusLETRouter) host.getRouter();

            if (nHost.lastEncounterTime.containsKey(dest)) {
                return nHost.lastEncounterTime.get(dest);
            }
        }

        return -1.0;
    }
    
    @Override
    public boolean createNewMessage(Message m) {
        makeRoomForNewMessage(m.getSize());
        boolean isNewMessage = true;
        
        m.setTtl(this.msgTtl);
        m.addProperty(MSG_COUNT_PROP, initialNrofCopies);
        addToMessages(m, isNewMessage);

        return isNewMessage;
    }

    @Override
    public int receiveMessage(Message m, DTNHost host) {
        return super.receiveMessage(m, host);
    }

    @Override
    public Message messageTransferred(String id, DTNHost from) {
        Message m = super.messageTransferred(id, from);
        Integer nc = (Integer)m.getProperty(MSG_COUNT_PROP);

        if (isBinary) {
            nc = (int)Math.ceil(nc/2.0);
        } else {
            nc = 1;
        }

        m.updateProperty(MSG_COUNT_PROP, nc);
        return m;
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
        super.tryMessagesToConnections(messages, getConnections());        
    }

    @Override
    protected int startTransfer(Message msg, Connection con) {
        DTNHost other = con.getOtherNode(getHost());
        Integer nc = (Integer)msg.getProperty(MSG_COUNT_PROP);

        // spray phase
        if (nc == null) {
            return super.startTransfer(msg, con);
        }

        if (nc > 1) {
            return super.startTransfer(msg, con);
        }

        // nc == 1
        // focus phase
        else {
            if (msg.getTo() == other) {
                return super.startTransfer(msg, con);
            }

            double myUtility = getUtility(getHost(), msg.getTo());
            double neighborUtility = getUtility(other, msg.getTo());
            
            boolean neighborIsBetter = false;

            if(neighborUtility > myUtility) {
                neighborIsBetter = true;
            }
            if(myUtility == -1 && neighborUtility != -1) neighborIsBetter = true;
            if(myUtility != -1 && neighborUtility == -1) neighborIsBetter = false;

            if (neighborIsBetter) {
                return super.startTransfer(msg, con);
            }
        }

        return TRY_LATER_BUSY;
    }

    @Override
    protected void transferDone(Connection con) {
        String msgId = con.getMessage().getId();
        Message msg = getMessage(msgId);
        
        if (msg == null) {
            return;
        }

        Integer nc = (Integer) msg.getProperty(MSG_COUNT_PROP);
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
    public SprayAndFocusLETRouter replicate() {
        return new SprayAndFocusLETRouter(this);
    }
}
