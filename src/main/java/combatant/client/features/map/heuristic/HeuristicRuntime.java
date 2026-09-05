/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.map.heuristic;

import combatant.client.config.subsystem.MapHeuristicConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class HeuristicRuntime {
    private static final HeuristicRuntime INSTANCE = new HeuristicRuntime();

    private final MapHeuristicConfig config = MapHeuristicConfig.get();
    private final ConcurrentHashMap<UUID, TargetState> states = new ConcurrentHashMap<>();
    private final ArrayBlockingQueue<UUID> queue = new ArrayBlockingQueue<>(256);
    private final AtomicReference<Map<UUID, HeuristicEstimate>> published = new AtomicReference<>(Map.of());
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread worker;

    private HeuristicRuntime() {
        worker = new Thread(this::loop, "Combatant-Map-Heuristic");
        worker.setDaemon(true);
        worker.start();
    }

    public static HeuristicRuntime get() { return INSTANCE; }
    public Map<UUID, HeuristicEstimate> snapshot() { return published.get(); }
    public HeuristicEstimate estimate(UUID id) { return id == null ? null : published.get().get(id); }

    public boolean offer(HeuristicObservation o) {
        if (o == null || !config.enabled()) return false;
        long now=System.currentTimeMillis();
        if (now-o.observedAtMs()>config.maxSampleAgeMs()) return false;
        TargetState s=states.computeIfAbsent(o.targetUuid(), k->new TargetState());
        synchronized (s) {
            prune(s,now);
            HeuristicObservation last=s.samples.peekLast();
            if (last!=null) {
                if (o.sourceRevision()==last.sourceRevision()) return false;
                double baseline=Math.hypot(o.observerX()-last.observerX(),o.observerZ()-last.observerZ());
                double angle=angleDiff(o.bearingRadians(),last.bearingRadians());
                if (baseline<config.minBaseline() && angle<Math.toRadians(config.minBearingDeltaDegrees())) return false;
            }
            s.samples.addLast(o);
            while(s.samples.size()>config.maxSamplesPerTarget()) s.samples.removeFirst();
            if(s.queued.compareAndSet(false,true) && !queue.offer(o.targetUuid())) s.queued.set(false);
            return true;
        }
    }

    public void clear(UUID id) {
        if(id==null)return;
        states.remove(id);
        updateMap(id,null);
    }

    public void shutdown() {
        if(!running.compareAndSet(true,false))return;
        worker.interrupt();
        states.clear();
        published.set(Map.of());
    }

    private void loop() {
        while(running.get()) {
            try {
                UUID id=queue.take();
                TargetState s=states.get(id);
                if(s==null) continue;
                List<HeuristicObservation> samples;
                long seg;
                synchronized(s) {
                    s.queued.set(false);
                    prune(s,System.currentTimeMillis());
                    samples=new ArrayList<>(s.samples);
                    seg=s.segmentId;
                }
                if(samples.size()<2) continue;
                HeuristicEstimate next=HeuristicSolver.solve(id,samples,seg,config.forwardRejectTolerance());
                if(next==null)continue;
                synchronized(s) {
                    if(s.estimate!=null && shouldReset(s.estimate,next)) {
                        s.segmentId++;
                        s.samples.clear();
                        for(int i=Math.max(0,samples.size()-3);i<samples.size();i++) s.samples.addLast(samples.get(i));
                        next=HeuristicSolver.solve(id,new ArrayList<>(s.samples),s.segmentId,config.forwardRejectTolerance());
                        if(next==null)continue;
                    }
                    s.estimate=next;
                }
                updateMap(id,next);
            } catch(InterruptedException e) {
                if(!running.get()) return;
            } catch(RuntimeException ignored) {}
        }
    }

    private boolean shouldReset(HeuristicEstimate a,HeuristicEstimate b) {
        double shift=Math.hypot(a.x()-b.x(),a.z()-b.z());
        double expected=Math.max(a.uncertaintyMajor(),b.uncertaintyMajor());
        return shift>Math.max(config.segmentResetDistance(),expected*config.segmentResetSigma())
                && b.confidence()>=config.segmentResetMinConfidence();
    }

    private void updateMap(UUID id,HeuristicEstimate value) {
        while(true) {
            Map<UUID,HeuristicEstimate> cur=published.get();
            Map<UUID,HeuristicEstimate> next=new LinkedHashMap<>(cur);
            if(value==null)next.remove(id); else next.put(id,value);
            if(published.compareAndSet(cur,Map.copyOf(next)))return;
        }
    }

    private void prune(TargetState s,long now) {
        while(!s.samples.isEmpty() && now-s.samples.peekFirst().observedAtMs()>config.maxSampleAgeMs()) s.samples.removeFirst();
    }

    private static double angleDiff(double a,double b) {
        double d=Math.abs(a-b)%(Math.PI*2.0);
        return d>Math.PI?Math.PI*2.0-d:d;
    }

    private static final class TargetState {
        final ArrayDeque<HeuristicObservation> samples=new ArrayDeque<>();
        final AtomicBoolean queued=new AtomicBoolean();
        long segmentId=1;
        HeuristicEstimate estimate;
    }
}
