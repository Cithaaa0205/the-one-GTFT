// package report;

// import java.util.Arrays;
// import java.util.List;

// import core.DTNHost;
// import core.SimScenario;
// import routing.DecisionEngineRouter;
// import routing.MessageRouter;
// import routing.RoutingDecisionEngine;
// import routing.community.CentralityDetectionEngine;

// public class CentralityReport extends Report {
    
//     public CentralityReport() {
//         init();
//         // 1. PERUBAHAN: Tambahkan header untuk file TSV/CSV
//         write("NodeID\tGlobalCentrality\tLocalCentrality\tHistoryArray"); 
//     }

//     @Override
//     public void done() {
//         List<DTNHost> nodes = SimScenario.getInstance().getHosts();
        
//         for(DTNHost h : nodes) {
//             MessageRouter r = h.getRouter();
//             if(!(r instanceof DecisionEngineRouter) ) continue;
            
//             RoutingDecisionEngine de = ((DecisionEngineRouter)r).getDecisionEngine();
//             if(!(de instanceof CentralityDetectionEngine)) continue;
            
//             CentralityDetectionEngine cd = (CentralityDetectionEngine)de;
            
//             double nilaiGlobal = cd.getGlobalDegreeCentrality();
//             double nilaiLocal = cd.getLocalDegreeCentrality();
            
//             // 2. PERUBAHAN: Panggil method array yang baru dibuat
//             int[] historyArray = cd.getGlobalArrayCentrality();
            
//             // 3. PERUBAHAN: Ubah array jadi string dan cetak dengan format Tab (\t)
//             String arrayStr = Arrays.toString(historyArray);
//             write(h.toString() + "\t" + nilaiGlobal + "\t" + nilaiLocal + "\t" + arrayStr);
//         }
//         super.done();
//     }
// }