package movement;

import core.Coord;
import core.Settings;

public class GridBasedRWPMovement extends RandomWaypoint {
    public static final String GRID_SIZE = "gridSize";
    public static final String GRID_LOCATION = "gridLoc";

    private double domainMinX;
    private double domainMaxX;
    private double domainMinY;
    private double domainMaxY;

    public GridBasedRWPMovement(Settings s) {
        super(s);

        // Ambil ukuran map utama
        double worldX = super.getMaxX();
        double worldY = super.getMaxY();

        // Cek setting grid global pada MovementModel, default 1x1 jika tidak diset
        Settings globalSettings = new Settings("MovementModel");
        int[] gridSize = globalSettings.contains(GRID_SIZE) ? 
                         globalSettings.getCsvInts(GRID_SIZE, 2) : new int[]{1, 1};
        int cols = gridSize[0];
        int rows = gridSize[1];

        // Cek posisi grid untuk grup ini, default 0,0 (pojok kiri atas) jika tidak diset
        int[] gridLoc = s.contains(GRID_LOCATION) ? 
                        s.getCsvInts(GRID_LOCATION, 2) : new int[]{0, 0};
        int colIndex = gridLoc[0];
        int rowIndex = gridLoc[1];

        // Hitung luas area berdasarkan porsi grid
        double cellWidth = worldX / cols;
        double cellHeight = worldY / rows;

        // Otomatis tentukan batas X dan Y
        this.domainMinX = colIndex * cellWidth;
        this.domainMaxX = this.domainMinX + cellWidth;
        this.domainMinY = rowIndex * cellHeight;
        this.domainMaxY = this.domainMinY + cellHeight;
    }

    protected GridBasedRWPMovement(GridBasedRWPMovement lrwp) {
        super(lrwp);
        this.domainMinX = lrwp.domainMinX;
        this.domainMaxX = lrwp.domainMaxX;
        this.domainMinY = lrwp.domainMinY;
        this.domainMaxY = lrwp.domainMaxY;
    }

    @Override
    public RandomWaypoint replicate() {
        return new GridBasedRWPMovement(this);
    }

    @Override
    protected Coord randomCoord() {
        double randomX = this.domainMinX + (rng.nextDouble() * (this.domainMaxX - this.domainMinX));
        double randomY = this.domainMinY + (rng.nextDouble() * (this.domainMaxY - this.domainMinY));
        return new Coord(randomX, randomY);
    }
}