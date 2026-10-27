package routing.decisionengine; // Sesuaikan package Anda

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import routing.RoutingDecisionEngine;

/**
 * Implementasi Epidemic Routing menggunakan arsitektur Decision Engine.
 * Strategi: "Kirim salinan setiap pesan ke semua node yang ditemui."
 */
public class EpidemicDecisionEngine implements RoutingDecisionEngine {

    /**
     * Konstruktor utama: Membaca settings.txt
     * Epidemic tidak butuh parameter khusus, jadi konstruktornya kosong.
     */
    public EpidemicDecisionEngine(Settings s) {
        // Tidak ada variabel yang perlu diinisialisasi
    }

    /**
     * Copy-constructor untuk replikasi ke setiap node di peta
     */
    public EpidemicDecisionEngine(EpidemicDecisionEngine engine) {
        // Tidak ada state yang perlu disalin
    }

    @Override
    public RoutingDecisionEngine replicate() {
        return new EpidemicDecisionEngine(this);
    }

    // --- FASE 1: MANAJEMEN KONEKSI & PERTUKARAN ---

    @Override
    public void connectionUp(DTNHost thisHost, DTNHost peer) {
        // Kosong. Epidemic tidak melacak siapa terhubung dengan siapa.
    }

    @Override
    public void connectionDown(DTNHost thisHost, DTNHost peer) {
        // Kosong.
    }

    @Override
    public void doExchangeForNewConnection(Connection con, DTNHost peer) {
        // Kosong. Epidemic tidak melakukan pertukaran metadata/tabel routing.
        // Ia hanya menukar pesan mentah (yang diurus otomatis oleh Router).
    }

    // --- FASE 2 & 3: PENERIMAAN PESAN ---

    @Override
    public boolean newMessage(Message m) {
        return true; // Selalu terima pesan yang dibuat oleh node ini sendiri
    }

    @Override
    public boolean isFinalDest(Message m, DTNHost aHost) {
        return m.getTo() == aHost;
    }

    @Override
    public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost) {
        // Epidemic harus selalu menyimpan pesan yang diterima untuk ditularkan ke orang lain
        // (Kecuali jika node ini adalah tujuan akhirnya)
        return m.getTo() != thisHost;
    }

    // --- FASE 4: INTI ALGORITMA (FORWARDING) ---

    @Override
    public boolean shouldSendMessageToHost(Message m, DTNHost otherHost, DTNHost thisHost) {
        // LOGIKA UTAMA EPIDEMIC:
        // Selalu jawab "YA" (Kirim!) kepada siapa pun tetangganya.
        return true; 
    }

    // --- FASE 5: PASCA PENGIRIMAN ---

    @Override
    public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost) {
        // JANGAN DIHAPUS! 
        // Setelah node A mengirim ke B, node A harus tetap menyimpan pesan itu 
        // agar bisa mengirimnya lagi ke node C, D, dst.
        return false;
    }

    @Override
    public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld) {
        // Jangan dihapus secara sepihak. Biarkan pesan hilang secara natural 
        // ketika TTL (Time To Live) habis atau Buffer (antrean) penuh.
        return false;
    }

    // --- FASE 6: SIKLUS PERIODIK ---

    @Override
    public void update(DTNHost thisHost) {
        // Kosong. Tidak ada "aging" atau perhitungan periodik di Epidemic.
    }
}