package movement;

import core.Coord;
import core.Settings;

public class LocalRandomWaypoint extends RandomWaypoint {
    public static final String DOMAIN_MIN_X = "domainMinX";
    public static final String DOMAIN_MAX_X = "domainMaxX";
    public static final String DOMAIN_MIN_Y = "domainMinY";
    public static final String DOMAIN_MAX_Y = "domainMaxY";

    private double domainMinX;
    private double domainMaxX;
    private double domainMinY;
    private double domainMaxY;

    public LocalRandomWaypoint(Settings s) {
        super(s);

        domainMinX = s.contains(DOMAIN_MIN_X) ? s.getDouble(DOMAIN_MIN_X) : 0;
        domainMaxX = s.contains(DOMAIN_MAX_X) ? s.getDouble(DOMAIN_MAX_X) : super.getMaxX();
        domainMinY = s.contains(DOMAIN_MIN_Y) ? s.getDouble(DOMAIN_MIN_Y) : 0;
        domainMaxY = s.contains(DOMAIN_MAX_Y) ? s.getDouble(DOMAIN_MAX_Y) : super.getMaxY();
    }

    protected LocalRandomWaypoint(LocalRandomWaypoint lrwp) {
        super(lrwp);

        this.domainMinX = lrwp.domainMinX;
        this.domainMaxX = lrwp.domainMaxX;
        this.domainMinY = lrwp.domainMinY;
        this.domainMaxY = lrwp.domainMaxY;

    }

    @Override
    public RandomWaypoint replicate() {
        return new LocalRandomWaypoint(this);
    }

    @Override
    protected Coord randomCoord() {
        double randomX = this.domainMinX + (rng.nextDouble() * (this.domainMaxX - this.domainMinX));
        double randomY = this.domainMinY + (rng.nextDouble() * (this.domainMaxY - this.domainMinY));
        return new Coord(randomX, randomY);
    }
}
