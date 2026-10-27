package report;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import core.Coord;
import core.DTNHost;
import core.MovementListener;

public class MobilityTraceReport extends Report implements MovementListener {
    private List<String> logs = new ArrayList<>();

    public MobilityTraceReport() {
        init();
    }

    @Override
    protected void init() {
        // TODO Auto-generated method stub
        super.init();
        logs = new ArrayList<>();
    }

    @Override
    public void initialLocation(DTNHost host, Coord location) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void newDestination(DTNHost host, Coord destination, double speed) {
        // TODO Auto-generated method stub
        logs.add(String.format("Time: %s | Node: %s | NewDest: %s | Speed: %.2f", 
            getSimTime(),
            host,
            destination,
            speed
        ));
    }

    @Override
    public void done() {
        // TODO Auto-generated method stub
        write("Mobility Trace Report - " + getScenarioName());
        Iterator<String> log = logs.iterator();
        while(log.hasNext()) {
            write(log.next());
        }
        super.done();
    }
}
