package movement;

import core.Coord;
import core.Settings;

/**
 * Random Grid Waypoint Movement Model
 * Node bergerak secara acak ke grid yang berbeda dari lokasinya saat ini.
 */
public class RandomGridWaypoint extends RandomWaypoint {

    public static final String NROF_GRID_X = "nrofgridx";
    public static final String NROF_GRID_Y = "nrofgridy";

    private int nrofGridX;
    private int nrofGridY;
    
    // Variabel buatan sendiri untuk melacak lokasi terakhir node
    private Coord lastLocation;

    public RandomGridWaypoint(Settings settings) {
        super(settings);
        
        settings.setNameSpace(MOVEMENT_MODEL_NS);

        this.nrofGridX = settings.getInt(NROF_GRID_X);
        this.nrofGridY = settings.getInt(NROF_GRID_Y);
    
        settings.restoreNameSpace();
    }

    public RandomGridWaypoint(RandomGridWaypoint rgw) {
        super(rgw);
        this.nrofGridX = rgw.nrofGridX;
        this.nrofGridY = rgw.nrofGridY;
        
        // Clone lokasi terakhir jika sudah ada saat mereplikasi
        if (rgw.lastLocation != null) {
            this.lastLocation = rgw.lastLocation.clone();
        }
    }

    /**
     * Dijalankan sekali saat simulasi dimulai untuk menempatkan node.
     * Di sini kita inisialisasi lastLocation.
     */
    @Override
    public Coord getInitialLocation() {
        // Dapatkan lokasi acak awal dari RandomWaypoint
        Coord initialLoc = super.getInitialLocation(); 
        
        // Simpan titik ini sebagai lokasi awal node kita
        this.lastLocation = initialLoc.clone(); 
        
        return initialLoc;
    }

    @Override
    public Path getPath() {
        Path p = new Path(generateSpeed());
        
        // Pastikan lastLocation tidak null (sebagai fallback)
        if (this.lastLocation == null) {
            this.lastLocation = getInitialLocation();
        }
        
        // 1. Mulai path dari lokasi terakhir node berada
        p.addWaypoint(this.lastLocation.clone());

        // 2. Hitung dimensi grid (gunakan (double) agar pembagian akurat)
        double cellWidth = getMaxX() / (double) nrofGridX;
        double cellHeight = getMaxY() / (double) nrofGridY;

        // 3. Tentukan indeks Grid saat ini
        int currentGridX = (int) (this.lastLocation.getX() / cellWidth);
        int currentGridY = (int) (this.lastLocation.getY() / cellHeight);

        if (currentGridX >= nrofGridX) currentGridX = nrofGridX - 1;
        if (currentGridY >= nrofGridY) currentGridY = nrofGridY - 1;

        int newGridX;
        int newGridY;

        // 4. Cari grid tujuan yang berbeda
        do {
            newGridX = rng.nextInt(nrofGridX);
            newGridY = rng.nextInt(nrofGridY);
        } while (newGridX == currentGridX && newGridY == currentGridY);

        // 5. Tentukan titik spesifik (X,Y) di dalam grid tujuan
        double targetX = (newGridX * cellWidth) + (rng.nextDouble() * cellWidth);
        double targetY = (newGridY * cellHeight) + (rng.nextDouble() * cellHeight);

        Coord targetCoord = new Coord(targetX, targetY);
        p.addWaypoint(targetCoord);
        
        // 6. UPDATE lastLocation dengan targetCoord untuk dipanggil di getPath() berikutnya
        this.lastLocation = targetCoord;
        
        return p;
    }

    @Override
    public RandomGridWaypoint replicate() {
        return new RandomGridWaypoint(this);
    }
}