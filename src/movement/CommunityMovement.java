package movement;

import core.Coord;
import core.Settings;

public class CommunityMovement extends RandomWaypoint {
    public static final String GRID_SIZE = "gridSize";
    public static final String GRID_LOCATION = "gridLoc"; // Koordinat Home
    public static final String GATHERING_LOCATION = "gatheringLoc"; // Koordinat Gathering

    private int cols, rows;
    private double cellWidth, cellHeight;
    
    // Variabel penyimpan posisi Grid
    private int homeCol, homeRow;
    private int gatherCol, gatherRow;

    // Status Pergerakan
    private static final int STATE_HOME = 0;
    private static final int STATE_GATHERING = 1;
    private static final int STATE_ELSEWHERE = 2;
    
    private int currentState;
    private boolean isInitialPlacement; // Flag untuk mengatur lokasi awal

    public CommunityMovement(Settings s) {
        super(s);
        init(s);
    }

    protected CommunityMovement(CommunityMovement cm) {
        super(cm);
        this.cols = cm.cols;
        this.rows = cm.rows;
        this.cellWidth = cm.cellWidth;
        this.cellHeight = cm.cellHeight;
        this.homeCol = cm.homeCol;
        this.homeRow = cm.homeRow;
        this.gatherCol = cm.gatherCol;
        this.gatherRow = cm.gatherRow;
        
        this.currentState = STATE_HOME; 
        this.isInitialPlacement = true; // Set true setiap kali node baru di-copy/dibuat
    }

    private void init(Settings s) {
        double worldX = super.getMaxX();
        double worldY = super.getMaxY();

        Settings globalSettings = new Settings("MovementModel");
        
        // Setup Grid
        int[] gridSize = globalSettings.contains(GRID_SIZE) ? 
                         globalSettings.getCsvInts(GRID_SIZE, 2) : new int[]{4, 3};
        this.cols = gridSize[0];
        this.rows = gridSize[1];
        this.cellWidth = worldX / cols;
        this.cellHeight = worldY / rows;

        // Setup Home (Domisili grup ini)
        int[] gridLoc = s.contains(GRID_LOCATION) ? 
                        s.getCsvInts(GRID_LOCATION, 2) : new int[]{0, 0};
        this.homeCol = gridLoc[0];
        this.homeRow = gridLoc[1];

        // Setup Gathering Place (Berdasarkan gambar, defaultnya di ujung kanan bawah: 3, 2)
        int[] gatherLoc = globalSettings.contains(GATHERING_LOCATION) ? 
                          globalSettings.getCsvInts(GATHERING_LOCATION, 2) : new int[]{cols - 1, rows - 1};
        this.gatherCol = gatherLoc[0];
        this.gatherRow = gatherLoc[1];
    }

    @Override
    public RandomWaypoint replicate() {
        return new CommunityMovement(this);
    }

    // Fungsi bantuan untuk menghitung koordinat random di dalam sel Grid tertentu
    private Coord getCoordInGrid(int targetCol, int targetRow) {
        double minX = targetCol * cellWidth;
        double maxX = minX + cellWidth;
        double minY = targetRow * cellHeight;
        double maxY = minY + cellHeight;

        double randomX = minX + (rng.nextDouble() * (maxX - minX));
        double randomY = minY + (rng.nextDouble() * (maxY - minY));

        return new Coord(randomX, randomY);
    }

    @Override
    protected Coord randomCoord() {
        // Jika ini adalah penempatan pertama kali saat simulasi dimulai, langsung letakkan di Home
        if (isInitialPlacement) {
            isInitialPlacement = false;
            return getCoordInGrid(homeCol, homeRow);
        }

        // 1. Tentukan Tujuan Status Selanjutnya Berdasarkan Probabilitas
        double p = rng.nextDouble(); // Menghasilkan angka acak 0.0 - 1.0
        int nextState = currentState;

        switch (currentState) {
            case STATE_HOME:
                // Dari Home -> 0.8 ke Gathering, 0.2 ke Elsewhere
                if (p < 0.8) {
                    nextState = STATE_GATHERING;
                } else {
                    nextState = STATE_ELSEWHERE;
                }
                break;
            case STATE_ELSEWHERE:
                // Dari Elsewhere -> 0.9 kembali ke Home, 0.1 pindah ke Elsewhere lain
                if (p < 0.9) {
                    nextState = STATE_HOME;
                } else {
                    nextState = STATE_ELSEWHERE;
                }
                break;
            case STATE_GATHERING:
                // Selesai dari Gathering -> Kembali ke Home
                nextState = STATE_HOME;
                break;
        }

        this.currentState = nextState;

        // 2. Tentukan Koordinat Grid Berdasarkan Status Tujuan
        int targetCol = homeCol;
        int targetRow = homeRow;

        if (currentState == STATE_GATHERING) {
            targetCol = gatherCol;
            targetRow = gatherRow;
        } else if (currentState == STATE_ELSEWHERE) {
            // Cari grid acak yang BUKAN Home dan BUKAN Gathering Place
            boolean validGrid = false;
            while (!validGrid) {
                targetCol = rng.nextInt(cols);
                targetRow = rng.nextInt(rows);
                
                boolean isNotHome = (targetCol != homeCol || targetRow != homeRow);
                boolean isNotGathering = (targetCol != gatherCol || targetRow != gatherRow);
                
                if (isNotHome && isNotGathering) {
                    validGrid = true;
                }
            }
        }

        // 3. Kembalikan koordinat X dan Y spesifik di dalam Grid tujuan tersebut
        return getCoordInGrid(targetCol, targetRow);
    }
}