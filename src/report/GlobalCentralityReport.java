package report;

import java.util.*;
import core.*;
import routing.*;
import routing.community.CentralityDetectionEngine;

public class GlobalCentralityReport extends Report {

    public GlobalCentralityReport() {
        init();
    }

    @Override
    public void done() {
        List<DTNHost> nodes = SimScenario.getInstance().getHosts();
        
        for (DTNHost h : nodes) {
            MessageRouter r = h.getRouter();
            if (!(r instanceof DecisionEngineRouter)) continue;

            RoutingDecisionEngine de = ((DecisionEngineRouter) r).getDecisionEngine();
            if (!(de instanceof CentralityDetectionEngine)) continue;

            CentralityDetectionEngine cd = (CentralityDetectionEngine) de;
            
            int[] history = cd.getGlobalArrayCentrality();
            
            if (history == null || history.length == 0) {
                write(h.toString() + ",0");
                continue;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(h.toString());
            
            for (double val : history) {
                sb.append(", ").append((int)val);
            }
            
            write(sb.toString());
        }
        super.done();
    }
}