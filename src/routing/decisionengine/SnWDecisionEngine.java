/*
 * @(#)SnWDecisionEngine.java
 */
package routing.decisionengine;

import core.*;
import routing.*;

/**
 * An implementation of the Spray and Wait Routing protocol using the
 * Decision Engine framework.
 */
public class SnWDecisionEngine implements RoutingDecisionEngine
{
    /** identifier for the initial number of copies setting */
    public static final String SnW_NS = "SnWDecisionEngine";
    public static final String NROF_COPIES_S = "nrofCopies";
    /** Message property key for the remaining available copies of a message */
    public static final String MSG_COUNT_PROP = "SprayAndWait.copies";
    
    protected int initialNrofCopies;
    
    public SnWDecisionEngine(Settings s)
    {
        Settings snw = new Settings(SnW_NS);
        initialNrofCopies = snw.getInt(NROF_COPIES_S);
    }
    
    public SnWDecisionEngine(SnWDecisionEngine snw)
    {
        this.initialNrofCopies = snw.initialNrofCopies;
    }
    
    @Override
    public RoutingDecisionEngine replicate()
    {
        return new SnWDecisionEngine(this);
    }
    
    @Override
    public void connectionDown(DTNHost thisHost, DTNHost peer) {}

    @Override
    public void connectionUp(DTNHost thisHost, DTNHost peer) {}

    @Override
    public void doExchangeForNewConnection(Connection con, DTNHost peer)
    {
        // Spray and Wait tidak memerlukan pertukaran tabel routing/utilitas
    }

    @Override
    public boolean isFinalDest(Message m, DTNHost aHost)
    {
        Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROP);
        if (nrofCopies != null) {
            nrofCopies = (int)Math.ceil(nrofCopies / 2.0);
            m.updateProperty(MSG_COUNT_PROP, nrofCopies);
        }
        
        return m.getTo() == aHost;
    }

    @Override
    public boolean newMessage(Message m)
    {
        m.addProperty(MSG_COUNT_PROP, initialNrofCopies);
        return true;
    }

    @Override
    public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld)
    {
        return m.getTo() == hostReportingOld;
    }

    @Override
    public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost)
    {
        int nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROP);
        
        // Fase Spray: Kurangi jumlah copy (Binary Spray)
        if(nrofCopies > 1) {
            nrofCopies /= 2;
            m.updateProperty(MSG_COUNT_PROP, nrofCopies);
            return false;
        }
        
        // Fase Wait: Jika pesan sudah dikirim (hanya bisa dikirim ke tujuan akhir),
        // hapus dari buffer node ini.
        return true;
    }

    @Override
    public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost)
    {
        return m.getTo() != thisHost;
    }

    public boolean shouldSendMessageToHost(Message m, DTNHost otherHost)
    {
        // Selalu kirim jika host yang ditemui adalah tujuan akhir
        if(m.getTo() == otherHost) return true;
        
        // Fase Spray: Kirim jika kita masih punya lebih dari 1 copy
        int nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROP);
        if(nrofCopies > 1) return true;
        
        // Fase Wait: Karena host yang ditemui bukan tujuan akhir dan copy tinggal 1, jangan kirim.
        return false;
    }

    @Override
    public boolean shouldSendMessageToHost(Message m, DTNHost otherHost, DTNHost thisHost) {
        return shouldSendMessageToHost(m, otherHost);
    }

    @Override
    public void update(DTNHost thisHost) {
        // Spray and Wait tidak membutuhkan update periodik
    }
}