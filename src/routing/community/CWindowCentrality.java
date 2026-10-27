/*
 * @(#)CWindowCentrality.java
 *
 * Copyright 2010 by University of Pittsburgh, released under GPLv3.
 * */
package routing.community;

import java.util.*;

import core.*;

public class CWindowCentrality implements Centrality
{
    public static final String CENTRALITY_WINDOW_SETTING = "timeWindow";
    public static final String COMPUTATION_INTERVAL_SETTING = "computeInterval";
    public static final String EPOCH_COUNT_SETTING = "nrOfEpochsToAvg";
    
    protected static int COMPUTE_INTERVAL = 600; 
    protected static int CENTRALITY_TIME_WINDOW = 21600; 
    protected static int EPOCH_COUNT = 787; 
    
    protected double globalCentrality;
    protected double localCentrality;
    
    protected int lastGlobalComputationTime;
    protected int lastLocalComputationTime;
    
    public CWindowCentrality(Settings s) 
    {
        if(s.contains(CENTRALITY_WINDOW_SETTING))
            CENTRALITY_TIME_WINDOW = s.getInt(CENTRALITY_WINDOW_SETTING);
        
        if(s.contains(COMPUTATION_INTERVAL_SETTING))
            COMPUTE_INTERVAL = s.getInt(COMPUTATION_INTERVAL_SETTING);
        
        if(s.contains(EPOCH_COUNT_SETTING))
            EPOCH_COUNT = s.getInt(EPOCH_COUNT_SETTING);
    }
    
    public CWindowCentrality(CWindowCentrality proto)
    {
        this.lastGlobalComputationTime = this.lastLocalComputationTime = 
            -COMPUTE_INTERVAL;
    }
    
    public double getGlobalCentrality(Map<DTNHost, List<Duration>> connHistory)
    {
        if(SimClock.getIntTime() - this.lastGlobalComputationTime < COMPUTE_INTERVAL)
            return globalCentrality;
        
        int[] centralities = new int[EPOCH_COUNT];
        int epoch, timeNow = SimClock.getIntTime();
        Map<Integer, Set<DTNHost>> nodesCountedInEpoch = new HashMap<Integer, Set<DTNHost>>();
        
        for(int i = 0; i < EPOCH_COUNT; i++)
            nodesCountedInEpoch.put(i, new HashSet<DTNHost>());
        
        int epochControl=0;
        for(Map.Entry<DTNHost, List<Duration>> entry : connHistory.entrySet())
        {
            DTNHost h = entry.getKey();
            for(Duration d : entry.getValue())
            {
                int timePassed = (int)(timeNow - d.end);
                
                // FIX 1: Ubah > menjadi >=
                if(timePassed >= CENTRALITY_TIME_WINDOW * EPOCH_COUNT)
                    break;
            
                epoch = timePassed / CENTRALITY_TIME_WINDOW;
                
                // FIX 2: Safety Check mencegah NullPointerException
                if (epoch >= EPOCH_COUNT) continue; 
                
                if(epoch>epochControl)
                    epochControl=epoch;
                
                Set<DTNHost> nodesAlreadyCounted = nodesCountedInEpoch.get(epoch);
                if(nodesAlreadyCounted.contains(h))
                    continue;

                centralities[epoch]++;
                nodesAlreadyCounted.add(h);
            }
        }

        int control = 0, sum = 0;
        for(int i = 0; i < epochControl+1; i++){ 
            sum += centralities[i];
            control++;
        }
        
        this.globalCentrality = ((double)sum) / control; 
        this.lastGlobalComputationTime = SimClock.getIntTime();
        
        return this.globalCentrality;
    }

    public double getLocalCentrality(Map<DTNHost, List<Duration>> connHistory,
            CommunityDetection cd)
    {
        if(SimClock.getIntTime() - this.lastLocalComputationTime < COMPUTE_INTERVAL)
            return localCentrality;
        
        int[] centralities = new int[EPOCH_COUNT];
        int epoch, timeNow = SimClock.getIntTime();
        Map<Integer, Set<DTNHost>> nodesCountedInEpoch = new HashMap<Integer, Set<DTNHost>>();
        
        int epochControl=0;
        
        for(int i = 0; i < EPOCH_COUNT; i++)
            nodesCountedInEpoch.put(i, new HashSet<DTNHost>());
        
        Set<DTNHost> community = cd.getLocalCommunity();
        
        for(Map.Entry<DTNHost, List<Duration>> entry : connHistory.entrySet())
        {
            DTNHost h = entry.getKey();
            
            if(!community.contains(h))
                continue;
            
            for(Duration d : entry.getValue())
            {
                int timePassed = (int)(timeNow - d.end);
                
                // FIX 1: Ubah > menjadi >=
                if(timePassed >= CENTRALITY_TIME_WINDOW * EPOCH_COUNT)
                    break;
                
                epoch = timePassed / CENTRALITY_TIME_WINDOW;
                
                // FIX 2: Safety Check mencegah NullPointerException
                if (epoch >= EPOCH_COUNT) continue;
                
                if(epoch>epochControl)
                    epochControl=epoch;
                
                Set<DTNHost> nodesAlreadyCounted = nodesCountedInEpoch.get(epoch);
                if(nodesAlreadyCounted.contains(h))
                    continue;
                
                centralities[epoch]++;
                nodesAlreadyCounted.add(h);
            }
        }
        
        int control = 0, sum = 0;
        for(int i = 0; i < epochControl+1; i++){ 
            sum += centralities[i];
            control++;
        }
        
        this.localCentrality = ((double)sum) / control; 
        this.lastLocalComputationTime = SimClock.getIntTime();
        
        return this.localCentrality;
    }

    @Override
    public int[] getGlobalArrayCentrality(Map<DTNHost, List<Duration>> connHistory) {
        int timeNow = SimClock.getIntTime();
        
        int totalHari = (timeNow / CENTRALITY_TIME_WINDOW) + 1;
        
        int[] centralities = new int[totalHari];
        Map<Integer, Set<DTNHost>> nodesCountedInEpoch = new HashMap<>();
        
        for(int i = 0; i < totalHari; i++) {
            nodesCountedInEpoch.put(i, new HashSet<DTNHost>());
        }
        
        for(Map.Entry<DTNHost, List<Duration>> entry : connHistory.entrySet()) {
            DTNHost h = entry.getKey();
            for(Duration d : entry.getValue()) {
                
                int hariKe = (int)(d.end / CENTRALITY_TIME_WINDOW);
                
                if (hariKe >= totalHari) hariKe = totalHari - 1;
                if (hariKe < 0) hariKe = 0;
                
                Set<DTNHost> nodesAlreadyCounted = nodesCountedInEpoch.get(hariKe);
                
                if(nodesAlreadyCounted != null && !nodesAlreadyCounted.contains(h)) {
                    centralities[hariKe]++;
                    nodesAlreadyCounted.add(h);
                }
            }
        }
        
        return centralities;
    }

    public Centrality replicate()
    {
        return new CWindowCentrality(this);
    }
}