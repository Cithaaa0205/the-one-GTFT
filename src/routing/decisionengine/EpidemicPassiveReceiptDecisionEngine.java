package routing.decisionengine; // Sesuaikan dengan package Anda

import java.util.HashSet;
import java.util.Set;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import routing.DecisionEngineRouter;
import routing.RoutingDecisionEngine;

/**
 * Implementasi Epidemic dengan mekanisme Passive Receipt (Tombstone/Vaccine).
 * Node tidak membuat pesan ACK fisik, melainkan saling menukar "daftar ID pesan 
 * yang sudah mencapai tujuan" saat koneksi baru terjadi.
 */
public class EpidemicPassiveReceiptDecisionEngine implements RoutingDecisionEngine {

    // Menyimpan daftar ID pesan yang diketahui SUDAH MENCAPAI TUJUAN
    private Set<String> passiveReceipts;

    public EpidemicPassiveReceiptDecisionEngine(Settings s) {
        this.passiveReceipts = new HashSet<String>();
    }

    public EpidemicPassiveReceiptDecisionEngine(EpidemicPassiveReceiptDecisionEngine engine) {
        this.passiveReceipts = new HashSet<String>();
    }

    @Override
    public RoutingDecisionEngine replicate() {
        return new EpidemicPassiveReceiptDecisionEngine(this);
    }

    // --- FASE 1: MANAJEMEN KONEKSI & PERTUKARAN (INTI PASSIVE RECEIPT) ---

    @Override
    public void connectionUp(DTNHost thisHost, DTNHost peer) {}

    @Override
    public void connectionDown(DTNHost thisHost, DTNHost peer) {}

    @Override
    public void doExchangeForNewConnection(Connection con, DTNHost peer) {
        DecisionEngineRouter peerRouter = (DecisionEngineRouter) peer.getRouter();
        EpidemicPassiveReceiptDecisionEngine peerEngine = 
            (EpidemicPassiveReceiptDecisionEngine) peerRouter.getDecisionEngine();
        
        DTNHost thisHost = con.getOtherNode(peer);

        // 1. SINKRONISASI METADATA: Salin data dari tetangga yang tidak kita miliki
        Set<String> newReceiptsForMe = new HashSet<String>(peerEngine.getPassiveReceipts());
        newReceiptsForMe.removeAll(this.passiveReceipts); 
        
        // Salin data dari kita yang tidak dimiliki tetangga
        Set<String> newReceiptsForPeer = new HashSet<String>(this.passiveReceipts);
        newReceiptsForPeer.removeAll(peerEngine.getPassiveReceipts());

        // Gabungkan (Union) daftar tersebut
        this.passiveReceipts.addAll(newReceiptsForMe);
        peerEngine.getPassiveReceipts().addAll(newReceiptsForPeer);

        // 2. BERSIHKAN BUFFER: Jika ada pesan di buffer yang ternyata 
        // baru saja kita ketahui sudah sampai tujuan, segera hapus!
        for (String msgId : newReceiptsForMe) {
            purgeMessage(msgId, thisHost);
        }
        for (String msgId : newReceiptsForPeer) {
            peerEngine.purgeMessage(msgId, peer);
        }
    }

    // --- FASE 2 & 3: PENERIMAAN PESAN ---

    @Override
    public boolean newMessage(Message m) {
        return true; 
    }

    @Override
    public boolean isFinalDest(Message m, DTNHost aHost) {
        return m.getTo() == aHost;
    }

    @Override
    public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost) {
        String msgId = m.getId();

        // KASUS A: Jika kita adalah tujuan akhir pesannya!
        if (m.getTo() == thisHost) {
            // Tambahkan ke daftar Passive Receipt kita
            passiveReceipts.add(msgId);
            return true; // Simpan ke kotak masuk (deliveredMessages)
        }

        // KASUS B: Jika pesan ini masuk daftar Receipt (mungkin karena delay), buang.
        if (passiveReceipts.contains(msgId)) {
            return false;
        }

        return true; // Simpan untuk di-forward secara epidemik
    }

    // --- FASE 4: INTI ALGORITMA (FORWARDING) ---

    @Override
    public boolean shouldSendMessageToHost(Message m, DTNHost otherHost, DTNHost thisHost) {
        DecisionEngineRouter otherRouter = (DecisionEngineRouter) otherHost.getRouter();
        EpidemicPassiveReceiptDecisionEngine otherEngine = 
            (EpidemicPassiveReceiptDecisionEngine) otherRouter.getDecisionEngine();

        // OPTIMASI: Jangan buang bandwidth untuk mengirim pesan 
        // jika kita tahu otak tetangga mencatat pesan ini sudah Delivered.
        if (otherEngine.getPassiveReceipts().contains(m.getId())) {
            return false; 
        }

        return true; // Epidemic
    }

    // --- FASE 5 & 6: PASCA PENGIRIMAN & SIKLUS ---

    @Override
    public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost) {
        return false; 
    }

    @Override
    public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld) {
        return false;
    }

    @Override
    public void update(DTNHost thisHost) {}

    // ==========================================================
    // CUSTOM HELPER METHODS
    // ==========================================================

    public Set<String> getPassiveReceipts() {
        return this.passiveReceipts;
    }

    /**
     * Menghapus pesan dari antrean router jika ternyata pesan tersebut 
     * sudah terkonfirmasi sampai tujuan melalui pertukaran metadata.
     */
    public void purgeMessage(String msgId, DTNHost host) {
        boolean hasMsg = false;
        for (Message bm : host.getRouter().getMessageCollection()) {
            if (bm.getId().equals(msgId)) {
                hasMsg = true;
                break;
            }
        }
        
        if (hasMsg) {
            // Hapus secara diam-diam (drop = false) karena tujuannya pembersihan memori
            host.getRouter().deleteMessage(msgId, false);
        }
    }
}