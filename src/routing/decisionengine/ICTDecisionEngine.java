package routing.decisionengine;

import java.util.HashMap;
import java.util.Map;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import routing.RoutingDecisionEngine;
import routing.DecisionEngineRouter;
import routing.MessageRouter;

public class ICTDecisionEngine implements RoutingDecisionEngine {  
    // Menyimpan waktu kontak terakhir dengan suatu node
    private Map<DTNHost, Double> lastContactTime;
    
    // Menyimpan rata-rata Inter-Contact Time (ICT) untuk setiap node
    private Map<DTNHost, Double> avgIct;
    
    // Menyimpan snapshot ICT dari peer (diupdate saat terjadi pertukaran/exchange)
    private Map<DTNHost, Map<DTNHost, Double>> peerIctCache;
    
    // Bobot EWMA (Exponential Weighted Moving Average), antara 0 dan 1
    // Semakin tinggi nilai alpha, semakin besar bobot pada waktu kontak terbaru
    private static final double ALPHA = 0.5; 
    private static final double BETA = 0.25;
    
    // Nilai default awal jika dua node belum pernah bertemu (diasumsikan sangat lama)
    private static final double INITIAL_ICT = 999999.0;
    
    public ICTDecisionEngine(Settings s) {}

    public ICTDecisionEngine(ICTDecisionEngine ict){
        this.lastContactTime = new HashMap<>();
        this.avgIct = new HashMap<>();
        this.peerIctCache = new HashMap<>();
    }
    
    @Override
    public void connectionUp(DTNHost thisHost, DTNHost peer) {
        // Mendapatkan waktu simulasi saat ini (bergantung pada implementasi core Anda,
        // pada The ONE simulator biasanya menggunakan SimClock.getTime())
        double currentTime = SimClock.getTime(); 
        
        if (lastContactTime.containsKey(peer)) {
            // Hitung Inter-Contact Time (jeda waktu sejak pertemuan terakhir)
            double currentIct = currentTime - lastContactTime.get(peer);
            
            // Update rata-rata ICT menggunakan formula EWMA
            double previousAvgIct = avgIct.getOrDefault(peer, INITIAL_ICT);
            
            // Jika ini pertemuan kedua (sebelumnya masih INITIAL), langsung gunakan currentIct
            if (previousAvgIct == INITIAL_ICT) {
                avgIct.put(peer, currentIct);
            } else {
                double newAvgIct = (ALPHA * currentIct) + ((1.0 - ALPHA) * previousAvgIct);
                avgIct.put(peer, newAvgIct);
            }
        }
        
        // Catat waktu kontak ini sebagai pertemuan terakhir
        lastContactTime.put(peer, currentTime);
    }
    
    @Override
    public void connectionDown(DTNHost thisHost, DTNHost peer) {
        // Hapus cache ICT peer karena koneksi sudah terputus
        peerIctCache.remove(peer);
    }
    
    @Override
    public void doExchangeForNewConnection(Connection con, DTNHost peer) {
        // Di sini kita mensimulasikan pertukaran tabel routing.
        // Kita meminta tabel avgIct milik peer untuk disimpan di cache lokal kita
        // agar bisa digunakan untuk mengambil keputusan forwarding.
        
        ICTDecisionEngine de = this.getOtherICTDecisionEngine(peer);
        DTNHost myHost = con.getOtherNode(peer);

        peerIctCache.put(peer, new HashMap<>(de.avgIct));
        
        double ictToPeer = this.avgIct.getOrDefault(peer, 1.0); // Hindari angka raksasa
        
        // Loop semua destinasi (Node C) yang diketahui oleh peer (Node B)
        for (Map.Entry<DTNHost, Double> entry : de.avgIct.entrySet()) {
            DTNHost nodeC = entry.getKey();
            double peerIctToC = entry.getValue();
            
            // Jangan lakukan transitivity untuk diri kita sendiri atau ke peer itu sendiri
            if (nodeC == myHost || nodeC == peer) {
                continue; 
            }
            
            // Hitung estimasi waktu jalur A -> B -> C
            double transitiveIct = ictToPeer + peerIctToC;
            
            // Ambil ICT kita ke C yang lama (jika belum ada, gunakan nilai initial)
            double myOldIctToC = this.avgIct.getOrDefault(nodeC, INITIAL_ICT);
            
            if (myOldIctToC == INITIAL_ICT) {
                // Jika kita belum pernah tahu soal Node C sama sekali, 
                // langsung gunakan nilai transitif
                this.avgIct.put(nodeC, transitiveIct);
            } else {
                // Jika kita sudah punya estimasi ke Node C, gabungkan dengan rumus EWMA Transitivity
                double myNewIctToC = (myOldIctToC * (1.0 - BETA)) + (BETA * transitiveIct);
                this.avgIct.put(nodeC, myNewIctToC);
            }
        }
        
    }
    
    @Override
    public boolean newMessage(Message m) {
        // Selalu terima pesan yang dibuat secara lokal oleh aplikasi di host ini
        return true;
    }
    
    @Override
    public boolean isFinalDest(Message m, DTNHost aHost) {
        // Cek apakah node ini adalah tujuan akhir dari pesan
        return m.getTo() == aHost; 
    }
    
    @Override
    public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost) {
        // Simpan pesan jika node kita memiliki kapasitas/buffer 
        // Logika kapasitas buffer biasanya diurus di router utama, di sini kita setuju untuk memprosesnya
        return true;
    }
    
    @Override
    public boolean shouldSendMessageToHost(Message m, DTNHost otherHost, DTNHost thisHost) {
        // 1. Jika otherHost adalah tujuan akhir pesan, PASTI KIRIM!
        if (isFinalDest(m, otherHost)) {
            return true;
        }
        
        DTNHost finalDestination = m.getTo();
        
        // Ambil nilai ICT kita menuju tujuan akhir
        double myIctToDest = this.avgIct.getOrDefault(finalDestination, INITIAL_ICT);
        
        // Ambil nilai ICT peer menuju tujuan akhir dari cache yang dipertukarkan
        double peerIctToDest = INITIAL_ICT;
        if (peerIctCache.containsKey(otherHost)) {
            peerIctToDest = peerIctCache.get(otherHost).getOrDefault(finalDestination, INITIAL_ICT);
        }
        
        // 2. KEPUTUSAN ROUTING: 
        // Forward pesan jika peer memiliki rata-rata waktu pertemuan yang LEBIH KECIL (lebih cepat)
        // dengan node tujuan dibandingkan diri kita sendiri.
        return peerIctToDest < myIctToDest;
    }
    
    @Override
    public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost) {
        // Hapus pesan dari buffer lokal hanya jika kita sudah mengirimnya langsung ke tujuan akhir.
        // Jika kita hanya meng-copy (forward) ke perantara, kita tetap menyimpan salinannya (Routing Epidemic-style).
        return isFinalDest(m, otherHost);
    }
    
    @Override
    public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld) {
        // Jika peer bilang pesan ini sudah kadaluarsa atau sudah terkirim di jaringan, hapus.
        return true;
    }
    
    @Override
    public void update(DTNHost thisHost) {
        // Dipanggil secara berkala. Tidak ada aksi spesifik yang perlu diupdate 
        // di background untuk engine ICT sederhana ini.
    }
    
    @Override
    public RoutingDecisionEngine replicate() {
        // Dipanggil oleh simulator untuk membuat instansi baru pada setiap node awal
        return new ICTDecisionEngine(this);
    }
    
    private ICTDecisionEngine getOtherICTDecisionEngine(DTNHost h)
    {
        MessageRouter otherRouter = h.getRouter();
        assert otherRouter instanceof DecisionEngineRouter : "This router only works " + 
        " with other routers of same type";
        
        return (ICTDecisionEngine) ((DecisionEngineRouter)otherRouter).getDecisionEngine();
    }
}