package routing.decisionengine;

import java.util.HashMap;
import java.util.Map;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import routing.DecisionEngineRouter;
import routing.RoutingDecisionEngine;

/**
 * Implementasi algoritma PeopleRank berdasarkan paper:
 * "PeopleRank: Social Opportunistic Forwarding" oleh Mtibaa dkk.
 * * Menggunakan interface (tersirat) RoutingDecisionEngine untuk bekerja 
 * sama dengan DecisionEngineRouter.
 */
public class PeopleRankDecisionEngine implements RoutingDecisionEngine {

    public static final String PEOPLERANK_NS = "PeopleRankDecisionEngine";
    public static final String DAMPING_SETTING = "dampingFactor";
    
    // Faktor redaman default jika tidak diatur di settings
    private double dampingFactor = 0.85;
    
    // Nilai PeopleRank saat ini untuk node ini
    private double peoRank = 0.0;
    
    // Menyimpan informasi terakhir yang diketahui dari tetangga sosial
    private Map<DTNHost, NeighborInfo> socialTable;

    public PeopleRankDecisionEngine(Settings s) {
        Settings prSettings = new Settings(PEOPLERANK_NS);
        if (prSettings.contains(DAMPING_SETTING)) {
            this.dampingFactor = prSettings.getDouble(DAMPING_SETTING);
        }
        
        this.peoRank = 0.0; // Inisialisasi awal seperti di Algoritma 1
        this.socialTable = new HashMap<DTNHost, NeighborInfo>();
    }

    public PeopleRankDecisionEngine(PeopleRankDecisionEngine pr) {
        this.dampingFactor = pr.dampingFactor;
        this.peoRank = pr.peoRank;
        this.socialTable = new HashMap<DTNHost, NeighborInfo>();
    }

    @Override
    public RoutingDecisionEngine replicate() {
        return new PeopleRankDecisionEngine(this);
    }

    @Override
    public boolean newMessage(Message m) {
        return true; // Terima semua pesan baru dari host ini
    }

    @Override
    public void connectionUp(DTNHost myHost, DTNHost otherNode) {
        // Tidak perlu logika kompleks di sini karena pertukaran data PeR 
        // akan di-handle oleh doExchangeForNewConnection.
    }

    @Override
    public void connectionDown(DTNHost myHost, DTNHost otherNode) {
        // Biarkan kosong
    }

    @Override
    public void doExchangeForNewConnection(Connection con, DTNHost otherHost) {
        /*
         * Berdasarkan Algoritma 1:
         * Saat node i dan j bertemu, mereka bertukar nilai PeR dan derajat |F|.
         * Lalu mereka memperbarui nilai PeR masing-masing.
         */
        DecisionEngineRouter otherRouter = (DecisionEngineRouter) otherHost.getRouter();
        PeopleRankDecisionEngine otherEngine = (PeopleRankDecisionEngine) otherRouter.getDecisionEngine();

        // 1. Simpan/Update informasi node j ke dalam social table node i
        this.socialTable.put(otherHost, new NeighborInfo(otherEngine.getPeoRank(), otherEngine.getDegree()));
        
        // 2. Simpan/Update informasi node i ke dalam social table node j
        otherEngine.updateNeighborInfo(con.getOtherNode(otherHost), this.getPeoRank(), this.getDegree());

        // 3. Kalkulasi ulang nilai PeR untuk kedua node berdasarkan data terbaru
        this.updatePeopleRank();
        otherEngine.updatePeopleRank();
    }

    @Override
    public boolean shouldSendMessageToHost(Message m, DTNHost otherNode, DTNHost myHost) {
        DecisionEngineRouter otherRouter = (DecisionEngineRouter) otherNode.getRouter();
        PeopleRankDecisionEngine otherEngine = (PeopleRankDecisionEngine) otherRouter.getDecisionEngine();

        // Berdasarkan Algoritma 1: Forward jika PeR(j) >= PeR(i) ATAU j == destination
        if (otherNode == m.getTo()) {
            return true;
        }
        
        if (otherEngine.getPeoRank() >= this.getPeoRank()) {
            return true;
        }

        return false;
    }

    @Override
    public boolean shouldDeleteOldMessage(Message m, DTNHost otherNode) {
        return false; // Serahkan pada mekanisme tombstone/router bawaan
    }

    @Override
    public boolean isFinalDest(Message m, DTNHost myHost) {
        return m.getTo() == myHost;
    }

    @Override
    public boolean shouldSaveReceivedMessage(Message m, DTNHost myHost) {
        return m.getTo() != myHost; // Simpan untuk di-forward kembali jika ini bukan tujuan akhir
    }

    @Override
    public boolean shouldDeleteSentMessage(Message m, DTNHost otherNode) {
        return false; // Karena ini mirip dengan varian replikasi, kita tidak menghapus pesan setelah dikirim ke node perantara
    }

    @Override
    public void update(DTNHost myHost) {
        // Tidak ada update periodik yang spesifik diperlukan selain saat kontak terjadi
    }

    // --- Helper Methods untuk PeopleRank ---

    public double getPeoRank() {
        return this.peoRank;
    }

    public int getDegree() {
        return this.socialTable.size(); // |F(i)| adalah jumlah tetangga yang pernah ditemui/berteman
    }

    protected void updateNeighborInfo(DTNHost neighbor, double neighborPeR, int neighborDegree) {
        this.socialTable.put(neighbor, new NeighborInfo(neighborPeR, neighborDegree));
    }

    /**
     * Mengkalkulasi nilai PeopleRank menggunakan Persamaan 2 dari paper.
     */
    private void updatePeopleRank() {
        double sum = 0.0;
        
        for (NeighborInfo info : socialTable.values()) {
            if (info.degree > 0) {
                sum += (info.peR / (double) info.degree);
            }
        }
        
        // Persamaan 2: PeR(Ni) = (1 - d) + d * Sum( PeR(Nj) / |F(Nj)| )
        this.peoRank = (1.0 - dampingFactor) + (dampingFactor * sum);
    }

    /**
     * Inner class untuk menyimpan Tuple {PeR(j), |F(j)|}
     */
    private class NeighborInfo {
        double peR;
        int degree;

        public NeighborInfo(double peR, int degree) {
            this.peR = peR;
            this.degree = degree;
        }
    }
}