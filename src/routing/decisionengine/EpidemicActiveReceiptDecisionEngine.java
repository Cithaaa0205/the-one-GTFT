package routing.decisionengine;

import java.util.HashSet;
import java.util.Set;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import routing.DecisionEngineRouter;
import routing.RoutingDecisionEngine;

/**
 * Implementasi Epidemic dengan mekanisme Active Receipt (ACK/Vaccine).
 * Pesan ACK akan menyebar secara epidemik untuk menghapus pesan asli
 * di seluruh node dalam jaringan guna mengosongkan buffer.
 */
public class EpidemicActiveReceiptDecisionEngine implements RoutingDecisionEngine {

    public static final String ACK_PREFIX = "ACK_";
    private static final int ACK_SIZE = 1;

    // Menyimpan daftar ID ACK yang diketahui node ini 
    // agar lebih cepat mengecek re-infeksi tanpa harus membaca buffer fisik
    private Set<String> knownAcks;

    public EpidemicActiveReceiptDecisionEngine(Settings s) {
        this.knownAcks = new HashSet<String>();
    }

    public EpidemicActiveReceiptDecisionEngine(EpidemicActiveReceiptDecisionEngine engine) {
        this.knownAcks = new HashSet<String>(); // Node baru mulai dengan set kosong
    }

    @Override
    public RoutingDecisionEngine replicate() {
        return new EpidemicActiveReceiptDecisionEngine(this);
    }

    // --- FASE 1: MANAJEMEN KONEKSI ---

    @Override
    public void connectionUp(DTNHost thisHost, DTNHost peer) {}

    @Override
    public void connectionDown(DTNHost thisHost, DTNHost peer) {}

    @Override
    public void doExchangeForNewConnection(Connection con, DTNHost peer) {
        // (Opsional) Anda bisa mempercepat penyebaran vaksin dengan saling bertukar 
        // isi variabel knownAcks di sini. Tetapi untuk meniru persis router 
        // aslinya yang menggunakan pertukaran "Pesan", kita biarkan kosong.
    }

    // --- FASE 2 & 3: PENERIMAAN PESAN & PEMBUATAN ACK ---

    @Override
    public boolean newMessage(Message m) {
        // Jika node ini yang membuat ACK, catat di database internal
        if (m.getId().startsWith(ACK_PREFIX)) {
            knownAcks.add(m.getId());
        }
        return true; 
    }

    @Override
    public boolean isFinalDest(Message m, DTNHost aHost) {
        return m.getTo() == aHost;
    }

    @Override
    public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost) {
        String msgId = m.getId();

        // 1. KASUS A: Pesan yang diterima adalah pesan ACK
        if (msgId.startsWith(ACK_PREFIX)) {
            knownAcks.add(msgId);
            String originalId = msgId.substring(ACK_PREFIX.length());
            
            // Hapus pesan asli dari buffer jika masih ada
            purgeOriginalMessage(originalId, thisHost);
            
            return true; // Simpan ACK di buffer untuk disebar (epidemik)
        }

        // 2. KASUS B: Anti Re-infeksi
        // Jika ini pesan biasa, tapi kita sudah punya ACK-nya, tolak pesannya!
        if (knownAcks.contains(ACK_PREFIX + msgId)) {
            return false; 
        }

        // 3. KASUS C: Kita adalah tujuan akhir pesan ini
        if (m.getTo() == thisHost) {
            generateActiveReceipt(m, thisHost);
            return true; 
        }

        // 4. KASUS D: Pesan biasa, simpan untuk di-forward
        return true; 
    }

    // --- FASE 4: INTI ALGORITMA (FORWARDING) ---

    @Override
    public boolean shouldSendMessageToHost(Message m, DTNHost otherHost, DTNHost thisHost) {
        DecisionEngineRouter otherRouter = (DecisionEngineRouter) otherHost.getRouter();
        EpidemicActiveReceiptDecisionEngine otherEngine = 
            (EpidemicActiveReceiptDecisionEngine) otherRouter.getDecisionEngine();

        // UPGRADE OPTIMASI DARI ACTIVEROUTER:
        // Jika ini pesan biasa, dan kita tahu otak (engine) tetangga sudah punya ACK-nya,
        // jangan repot-repot memulai transfer fisik. Langsung batalkan!
        if (!m.getId().startsWith(ACK_PREFIX) && otherEngine.hasAck(ACK_PREFIX + m.getId())) {
            return false; 
        }

        return true; // Epidemic: Kirim semuanya (Pesan Biasa maupun ACK)
    }

    // --- FASE 5: PASCA PENGIRIMAN ---

    @Override
    public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost) {
        return false; // Epidemic selalu mempertahankan salinan setelah mengirim
    }

    @Override
    public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld) {
        return false;
    }

    // --- FASE 6: SIKLUS PERIODIK ---

    @Override
    public void update(DTNHost thisHost) {}

    // ==========================================================
    // CUSTOM HELPER METHODS
    // ==========================================================

    /**
     * Mengecek apakah engine ini sudah mengetahui keberadaan suatu ACK
     */
    public boolean hasAck(String ackId) {
        return knownAcks.contains(ackId);
    }

    /**
     * Menghapus pesan asli secara aman tanpa memicu SimError
     */
    private void purgeOriginalMessage(String originalId, DTNHost host) {
        boolean hasMsg = false;
        // Gunakan getMessageCollection() karena method hasMessage() berstatus protected
        for (Message bm : host.getRouter().getMessageCollection()) {
            if (bm.getId().equals(originalId)) {
                hasMsg = true;
                break;
            }
        }
        
        // Hapus (drop) pesan dari buffer Router
        if (hasMsg) {
            host.getRouter().deleteMessage(originalId, false);
        }
    }

    /**
     * Membuat pesan ACK baru layaknya di ActiveRouter lama
     */
    private void generateActiveReceipt(Message originalMsg, DTNHost host) {
        String ackId = ACK_PREFIX + originalMsg.getId();

        if (!knownAcks.contains(ackId)) {
            knownAcks.add(ackId);
            
            // Buat objek pesan ACK dengan tujuan source asli (walaupun epidemic tidak peduli tujuan)
            Message ackMsg = new Message(host, originalMsg.getFrom(), ackId, ACK_SIZE);
            
            // Suntikkan ke buffer Router
            host.getRouter().createNewMessage(ackMsg);
        }
    }
}