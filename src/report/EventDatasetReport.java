package report;

import java.util.HashSet;
import java.util.Set;

import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.SimClock;
import routing.ProphetRouter;

public class EventDatasetReport extends Report implements MessageListener {
    private static String HEADER = "time, current_node, destination_node, neighbor_nodes, message_id, selected_forwarder, predictability_of_the_current_node, predictability_of_the_selected_forwarder"; 
    private Set<DTNHost> neighbors;

    public EventDatasetReport() {
        init();
    }

    @Override
    protected void init() {
        super.init();
        write(HEADER);
        neighbors = new HashSet<>();
    }

    @Override
    public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean firstDelivery) {
        double predCurrent = 0.0;
        double predForwarder = 0.0;
        
        if (from.getRouter() instanceof ProphetRouter) {
            predCurrent = ((ProphetRouter) from.getRouter()).getPredFor(m.getTo());
            neighbors = ((ProphetRouter) from.getRouter()).getNeighborList();
        }
        if (to.getRouter() instanceof ProphetRouter) {
            predForwarder = ((ProphetRouter) to.getRouter()).getPredFor(m.getTo());
        }

        write(String.format("%f, %s, %s, %s, %s, %s, %f, %f", 
            SimClock.getTime(),
            from.toString(),
            m.getTo().toString(),
            neighbors,
            m.getId(),
            to.toString(),
            predCurrent,
            predForwarder
        ));
    }

    @Override
    public void messageDeleted(Message m, DTNHost where, boolean dropped) {}

    @Override
    public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {}

    @Override
    public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {}

    @Override
    public void newMessage(Message m) {}

    @Override
    public void done() {
        super.done();
    }
}