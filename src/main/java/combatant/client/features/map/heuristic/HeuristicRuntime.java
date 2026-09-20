/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.heuristic;

import combatant.client.config.subsystem.MapHeuristicConfig;
import combatant.client.config.subsystem.MapTriangulationConfig;
import combatant.client.features.map.location.LocationSessionKey;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class HeuristicRuntime {
    private static final HeuristicRuntime INSTANCE = new HeuristicRuntime();

    private final MapHeuristicConfig config = MapHeuristicConfig.get();
    private final MapTriangulationConfig modeConfig = MapTriangulationConfig.get();
    private final ConcurrentHashMap<UUID, TargetState> states = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> suppressedTargets = ConcurrentHashMap.newKeySet();
    private final ArrayBlockingQueue<Work> queue = new ArrayBlockingQueue<>(256);
    private final ConcurrentLinkedQueue<HeuristicLifecycleEvent> lifecycleEvents = new ConcurrentLinkedQueue<>();
    private final AtomicReference<Map<UUID, HeuristicEstimate>> published = new AtomicReference<>(Map.of());
    private final AtomicReference<LocationSessionKey> activeSession = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong rejectedMode = new AtomicLong();
    private final AtomicLong rejectedAge = new AtomicLong();
    private final AtomicLong rejectedDuplicate = new AtomicLong();
    private final AtomicLong rejectedInformationGain = new AtomicLong();
    private final AtomicLong rejectedCapacity = new AtomicLong();
    private final AtomicLong solved = new AtomicLong();
    private final AtomicLong segmentResets = new AtomicLong();

    private final Thread worker;

    private HeuristicRuntime() {
        worker = new Thread(this::loop, "Combatant-Map-Heuristic");
        worker.setDaemon(true);
        worker.start();
    }

    public static HeuristicRuntime get() { return INSTANCE; }

    public synchronized void beginSession(LocationSessionKey session) {
        if (session == null) {
            endSession();
            return;
        }
        LocationSessionKey previous = activeSession.get();
        if (session.equals(previous)) return;
        closeSession(previous, HeuristicLifecycleEventType.SESSION_ENDED);
        states.clear();
        queue.clear();
        suppressedTargets.clear();
        published.set(Map.of());
        activeSession.set(session);
    }

    public synchronized void endSession() {
        LocationSessionKey previous = activeSession.getAndSet(null);
        closeSession(previous, HeuristicLifecycleEventType.SESSION_ENDED);
        states.clear();
        queue.clear();
        suppressedTargets.clear();
        published.set(Map.of());
    }

    private void closeSession(LocationSessionKey session, HeuristicLifecycleEventType type) {
        if (session == null) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, TargetState> entry : states.entrySet()) {
            TargetState state = entry.getValue();
            synchronized (state) {
                if (state.samples.isEmpty() && state.estimate == null) continue;
                lifecycleEvents.offer(new HeuristicLifecycleEvent(entry.getKey(), state.targetName,
                        session, type, now, state.segmentId));
            }
        }
    }

    public Map<UUID, HeuristicEstimate> snapshot() {
        MapTriangulationMode mode = modeConfig.mode();
        if (mode == MapTriangulationMode.OFF) return Map.of();
        Map<UUID, HeuristicEstimate> current = published.get();
        if (current.isEmpty()) return current;
        long now = System.currentTimeMillis();
        Map<UUID, HeuristicEstimate> filtered = new LinkedHashMap<>();
        for (Map.Entry<UUID, HeuristicEstimate> entry : current.entrySet()) {
            HeuristicEstimate estimate = entry.getValue();
            if (estimate == null || now - estimate.updatedAtMs() > config.maxSampleAgeMs()) continue;
            if (mode == MapTriangulationMode.TARGETED && !modeConfig.isTargeted(entry.getKey())) continue;
            filtered.put(entry.getKey(), estimate);
        }
        return filtered.size() == current.size() ? current : Map.copyOf(filtered);
    }

    public HeuristicEstimate estimate(UUID id) {
        if (id == null || !modeConfig.accepts(id)) return null;
        return published.get().get(id);
    }

    public List<HeuristicLifecycleEvent> drainLifecycleEvents() {
        List<HeuristicLifecycleEvent> out = new ArrayList<>();
        for (HeuristicLifecycleEvent event; (event = lifecycleEvents.poll()) != null;) out.add(event);
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    public HeuristicRuntimeStats stats() {
        int queued = 0;
        for (TargetState state : states.values()) if (state.queued.get()) queued++;
        return new HeuristicRuntimeStats(modeConfig.mode(), accepted.get(), rejectedMode.get(), rejectedAge.get(),
                rejectedDuplicate.get(), rejectedInformationGain.get(), rejectedCapacity.get(), solved.get(),
                segmentResets.get(), states.size(), queued);
    }

    public Map<UUID, HeuristicTargetMetrics> telemetry() {
        MapTriangulationMode mode = modeConfig.mode();
        if (mode == MapTriangulationMode.OFF || states.isEmpty()) return Map.of();
        long now = System.currentTimeMillis();
        Map<UUID, HeuristicTargetMetrics> out = new LinkedHashMap<>();
        for (Map.Entry<UUID, TargetState> entry : states.entrySet()) {
            UUID id = entry.getKey();
            TargetState state = entry.getValue();
            synchronized (state) {
                prune(state, now);
                String name = state.targetName;
                if (name.isBlank()) name = modeConfig.nameForTarget(id);
                if (mode == MapTriangulationMode.TARGETED && !modeConfig.isTargeted(id, name)) continue;
                if (state.samples.isEmpty() && state.estimate == null) continue;
                out.put(id, new HeuristicTargetMetrics(id, name, List.copyOf(state.samples), state.estimate,
                        state.queued.get(), state.lastSolvedAt, state.segmentId));
            }
        }
        return out.isEmpty() ? Map.of() : Map.copyOf(out);
    }

    public boolean offer(HeuristicObservation observation) {
        LocationSessionKey session = activeSession.get();
        if (observation == null || observation.targetUuid() == null
                || session == null || !session.equals(observation.session())) {
            rejectedMode.incrementAndGet();
            return false;
        }
        UUID target = observation.targetUuid();
        if (!modeConfig.accepts(target, observation.targetName()) || suppressedTargets.contains(target)) {
            rejectedMode.incrementAndGet();
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - observation.observedAtMs() > config.maxSampleAgeMs()) {
            rejectedAge.incrementAndGet();
            return false;
        }

        if (modeConfig.mode() == MapTriangulationMode.DATA_MINING
                && !states.containsKey(target)
                && states.size() >= modeConfig.dataMiningMaxActiveTargets()) {
            rejectedCapacity.incrementAndGet();
            return false;
        }

        TargetState state = states.computeIfAbsent(target, ignored -> new TargetState(session));
        synchronized (state) {
            if (!state.session.equals(session)) {
                rejectedMode.incrementAndGet();
                return false;
            }
            prune(state, now);
            if (!observation.targetName().isBlank()) state.targetName = observation.targetName();

            if (state.sourceGeneration != 0L && state.sourceGeneration != observation.sourceGeneration()) {
                // Locator disappeared and appeared again: this is a hard continuity boundary. Never
                // combine pre-disappearance bearings with the new source generation.
                resetSegment(state, false, now, null);
                updateMap(target, null);
            }
            state.sourceGeneration = observation.sourceGeneration();

            HeuristicObservation last = state.samples.peekLast();
            if (last != null) {
                if (observation.sourceRevision() == last.sourceRevision()
                        && observation.sourceGeneration() == last.sourceGeneration()) {
                    rejectedDuplicate.incrementAndGet();
                    return false;
                }
                double baseline = Math.hypot(observation.observerX() - last.observerX(),
                        observation.observerZ() - last.observerZ());
                double angle = angleDiff(observation.bearingRadians(), last.bearingRadians());
                if (baseline < config.minBaseline()
                        && angle < Math.toRadians(config.minBearingDeltaDegrees())) {
                    rejectedInformationGain.incrementAndGet();
                    return false;
                }
            }

            if (state.estimate != null && inconsistentWithEstimate(observation, state.estimate)) {
                addBreakCandidate(state, observation, now);
                accepted.incrementAndGet();
                if (confirmTeleport(target, state, now)) return true;
                return true; // do not contaminate the stable segment with a suspicious sample
            }
            state.breakCandidates.clear();

            addSample(state, observation);
            accepted.incrementAndGet();
            boolean solveDue = state.estimate == null || now - state.lastSolvedAt >= modeConfig.minSolveIntervalMs();
            if (solveDue) enqueue(target, state);
            return true;
        }
    }

    /** Exact source is a segment boundary; old bearings are history, not resume state. */
    public void setSuppressedTargets(java.util.Set<UUID> targets) {
        java.util.Set<UUID> next = targets == null ? java.util.Set.of() : java.util.Set.copyOf(targets);
        long now = System.currentTimeMillis();
        for (UUID id : next) {
            if (suppressedTargets.contains(id)) continue;
            TargetState state = states.get(id);
            if (state != null) {
                synchronized (state) {
                    if (!state.samples.isEmpty() || state.estimate != null) {
                        lifecycleEvents.offer(new HeuristicLifecycleEvent(id, state.targetName, state.session,
                                HeuristicLifecycleEventType.EXACT_SOURCE_ACQUIRED, now, state.segmentId));
                    }
                    resetSegment(state, false, now, null);
                }
                updateMap(id, null);
            }
        }
        suppressedTargets.clear();
        suppressedTargets.addAll(next);
    }

    public boolean isSuppressed(UUID id) { return id != null && suppressedTargets.contains(id); }

    public void clear(UUID id) {
        if (id == null) return;
        suppressedTargets.remove(id);
        states.remove(id);
        updateMap(id, null);
        queue.removeIf(work -> id.equals(work.id));
    }

    public void shutdown() {
        if (!running.compareAndSet(true, false)) return;
        worker.interrupt();
        endSession();
    }

    private void loop() {
        while (running.get()) {
            try {
                Work work = queue.poll(100L, TimeUnit.MILLISECONDS);
                if (work == null) {
                    enqueueDueDirty();
                    continue;
                }
                LocationSessionKey session = activeSession.get();
                if (session == null || session.generation() != work.sessionGeneration) continue;
                TargetState state = states.get(work.id);
                if (state == null) continue;
                if (suppressedTargets.contains(work.id) || !modeConfig.accepts(work.id)) {
                    state.queued.set(false);
                    continue;
                }

                List<HeuristicObservation> samples;
                long segment;
                synchronized (state) {
                    state.queued.set(false);
                    if (!state.session.equals(session)) continue;
                    prune(state, System.currentTimeMillis());
                    samples = new ArrayList<>(state.samples);
                    segment = state.segmentId;
                    state.dirty = false;
                }
                if (samples.size() < 2) continue;

                HeuristicEstimate next = HeuristicSolver.solve(work.id, samples, segment,
                        config.forwardRejectTolerance(), Math.toRadians(config.bearingNoiseDegrees()));
                if (next == null) continue;

                synchronized (state) {
                    // Session/segment may have changed while solve was running. Discard stale result.
                    LocationSessionKey currentSession = activeSession.get();
                    if (currentSession == null || !state.session.equals(currentSession)
                            || state.segmentId != segment || suppressedTargets.contains(work.id)) continue;
                    if (state.estimate != null && shouldReset(state.estimate, next)) {
                        List<HeuristicObservation> recent = newestSamples(state.samples, config.teleportConfirmSamples());
                        resetSegment(state, true, System.currentTimeMillis(), HeuristicLifecycleEventType.TELEPORT_SEGMENT_BREAK);
                        for (HeuristicObservation sample : recent) addSample(state, sample);
                        next = HeuristicSolver.solve(work.id, new ArrayList<>(state.samples), state.segmentId,
                                config.forwardRejectTolerance(), Math.toRadians(config.bearingNoiseDegrees()));
                        if (next == null) continue;
                    }
                    state.estimate = next;
                    state.lastSolvedAt = System.currentTimeMillis();
                }
                solved.incrementAndGet();
                updateMap(work.id, next);
            } catch (InterruptedException interrupted) {
                if (!running.get()) return;
            } catch (RuntimeException ignored) {
            }
        }
    }

    private boolean inconsistentWithEstimate(HeuristicObservation observation, HeuristicEstimate estimate) {
        double residual = Math.abs(lineResidual(observation, estimate.x(), estimate.z()));
        double uncertainty = Math.max(estimate.uncertaintyMinor(), Math.min(estimate.uncertaintyMajor(), 64.0));
        double threshold = Math.max(config.teleportResidualFloor(), uncertainty * config.teleportResidualSigma());
        return residual > threshold
                || forward(observation, estimate.x(), estimate.z()) < -config.forwardRejectTolerance()
                || violatesMinimumRange(observation, estimate.x(), estimate.z());
    }

    private void addBreakCandidate(TargetState state, HeuristicObservation observation, long now) {
        state.breakCandidates.addLast(observation);
        while (!state.breakCandidates.isEmpty()
                && now - state.breakCandidates.peekFirst().observedAtMs() > config.teleportCandidateMaxAgeMs()) {
            state.breakCandidates.removeFirst();
        }
        while (state.breakCandidates.size() > Math.max(config.teleportConfirmSamples() * 2, 8)) {
            state.breakCandidates.removeFirst();
        }
    }

    private boolean confirmTeleport(UUID target, TargetState state, long now) {
        if (state.estimate == null || state.breakCandidates.size() < config.teleportConfirmSamples()) return false;
        List<HeuristicObservation> candidates = new ArrayList<>(state.breakCandidates);
        HeuristicEstimate candidate = HeuristicSolver.solve(target, candidates, state.segmentId + 1,
                config.forwardRejectTolerance(), Math.toRadians(config.bearingNoiseDegrees()));
        if (candidate == null) return false;
        // A teleport break must not require a precise new position. A far-away new target can have
        // weak baseline/confidence while its recent bearings are still mutually consistent and all
        // strongly contradict the old solution. Use inlier/residual agreement for the break gate;
        // precision/confidence is still required later before the new estimate is presented.
        if (candidate.inlierCount() < config.teleportConfirmSamples()
                || candidate.residualRms() > config.teleportResidualFloor() * 2.0) return false;
        double shift = Math.hypot(candidate.x() - state.estimate.x(), candidate.z() - state.estimate.z());
        double oldScale = Math.min(Math.max(0.0, state.estimate.uncertaintyMajor()), 128.0);
        if (shift <= Math.max(config.segmentResetDistance(), oldScale * config.segmentResetSigma())) return false;

        resetSegment(state, true, now, HeuristicLifecycleEventType.TELEPORT_SEGMENT_BREAK);
        for (HeuristicObservation sample : candidates) addSample(state, sample);
        state.breakCandidates.clear();
        state.dirty = true;
        enqueue(target, state);
        updateMap(target, null);
        return true;
    }

    private void addSample(TargetState state, HeuristicObservation observation) {
        state.samples.addLast(observation);
        while (state.samples.size() > config.maxSamplesPerTarget()) state.samples.removeFirst();
        state.dirty = true;
    }

    private void resetSegment(TargetState state, boolean countReset, long now, HeuristicLifecycleEventType eventType) {
        if (eventType != null) {
            lifecycleEvents.offer(new HeuristicLifecycleEvent(
                    state.samples.isEmpty() ? null : state.samples.peekLast().targetUuid(), state.targetName,
                    state.session, eventType, now, state.segmentId));
        }
        state.samples.clear();
        state.breakCandidates.clear();
        state.estimate = null;
        state.lastSolvedAt = 0L;
        state.dirty = false;
        state.queued.set(false);
        state.segmentId++;
        if (countReset) segmentResets.incrementAndGet();
    }

    private void enqueue(UUID id, TargetState state) {
        LocationSessionKey session = activeSession.get();
        if (session == null || !state.session.equals(session)) return;
        if (state.queued.compareAndSet(false, true) && !queue.offer(new Work(id, session.generation()))) {
            state.queued.set(false);
        }
    }

    private void enqueueDueDirty() {
        long now = System.currentTimeMillis();
        LocationSessionKey session = activeSession.get();
        if (session == null) return;
        for (Map.Entry<UUID, TargetState> entry : states.entrySet()) {
            UUID id = entry.getKey();
            TargetState state = entry.getValue();
            if (suppressedTargets.contains(id) || !modeConfig.accepts(id)) continue;
            synchronized (state) {
                if (!state.session.equals(session)) continue;
                prune(state, now);
                if (!state.dirty || state.samples.size() < 2) continue;
                if (state.estimate != null && now - state.lastSolvedAt < modeConfig.minSolveIntervalMs()) continue;
                enqueue(id, state);
            }
        }
    }

    private boolean shouldReset(HeuristicEstimate a, HeuristicEstimate b) {
        double shift = Math.hypot(a.x() - b.x(), a.z() - b.z());
        double expected = Math.max(a.uncertaintyMajor(), b.uncertaintyMajor());
        return shift > Math.max(config.segmentResetDistance(), expected * config.segmentResetSigma())
                && b.confidence() >= config.segmentResetMinConfidence();
    }

    private void updateMap(UUID id, HeuristicEstimate value) {
        if (id == null) return;
        while (true) {
            Map<UUID, HeuristicEstimate> current = published.get();
            Map<UUID, HeuristicEstimate> next = new LinkedHashMap<>(current);
            if (value == null) next.remove(id); else next.put(id, value);
            if (published.compareAndSet(current, Map.copyOf(next))) return;
        }
    }

    private void prune(TargetState state, long now) {
        while (!state.samples.isEmpty() && now - state.samples.peekFirst().observedAtMs() > config.maxSampleAgeMs()) {
            state.samples.removeFirst();
        }
        while (!state.breakCandidates.isEmpty()
                && now - state.breakCandidates.peekFirst().observedAtMs() > config.teleportCandidateMaxAgeMs()) {
            state.breakCandidates.removeFirst();
        }
    }

    private static List<HeuristicObservation> newestSamples(ArrayDeque<HeuristicObservation> samples, int count) {
        List<HeuristicObservation> all = new ArrayList<>(samples);
        return all.subList(Math.max(0, all.size() - Math.max(2, count)), all.size());
    }

    private static boolean violatesMinimumRange(HeuristicObservation o, double x, double z) {
        double minimum = o.minimumHorizontalRange();
        if (!(minimum > 0.0)) return false;
        // Small tolerance absorbs block-center / interpolation noise without weakening the source constraint.
        return Math.hypot(x - o.observerX(), z - o.observerZ()) + 1.5 < minimum;
    }

    private static double lineResidual(HeuristicObservation o, double x, double z) {
        double nx = -o.dirZ(), nz = o.dirX();
        return nx * (x - o.observerX()) + nz * (z - o.observerZ());
    }

    private static double forward(HeuristicObservation o, double x, double z) {
        return o.dirX() * (x - o.observerX()) + o.dirZ() * (z - o.observerZ());
    }

    private static double angleDiff(double a, double b) {
        double d = Math.abs(a - b) % (Math.PI * 2.0);
        return d > Math.PI ? Math.PI * 2.0 - d : d;
    }

    private static final class TargetState {
        final LocationSessionKey session;
        final ArrayDeque<HeuristicObservation> samples = new ArrayDeque<>();
        final ArrayDeque<HeuristicObservation> breakCandidates = new ArrayDeque<>();
        final AtomicBoolean queued = new AtomicBoolean();
        long segmentId = 1;
        long sourceGeneration;
        long lastSolvedAt;
        String targetName = "";
        HeuristicEstimate estimate;
        boolean dirty;

        private TargetState(LocationSessionKey session) { this.session = session; }
    }

    private record Work(UUID id, long sessionGeneration) {}
}
