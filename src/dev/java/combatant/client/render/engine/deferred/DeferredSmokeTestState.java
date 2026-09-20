/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Renderer-side runtime smoke/debug state. It is intentionally independent from module/UI config.
 * Mutations are atomic and become visible at normal frame boundaries.
 */
public final class DeferredSmokeTestState {
    private static final DeferredSmokeTestState GLOBAL = new DeferredSmokeTestState();

    private final AtomicReference<Snapshot> current = new AtomicReference<>(Snapshot.defaults());
    private final AtomicReference<Snapshot> frame = new AtomicReference<>(Snapshot.defaults());
    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong frameId = new AtomicLong(Long.MIN_VALUE);
    private final AtomicReference<EnumSet<DeferredFeature>> pendingTemporalResets =
            new AtomicReference<>(EnumSet.noneOf(DeferredFeature.class));
    private final AtomicReference<DeferredHistoryResetReason> lastResetReason =
            new AtomicReference<>(DeferredHistoryResetReason.NONE);
    private final AtomicReference<String> unavailableDebugReason = new AtomicReference<>("");

    private DeferredSmokeTestState() { }

    public static DeferredSmokeTestState global() { return GLOBAL; }
    public Snapshot snapshot() { return current.get(); }
    /** Immutable smoke state frozen at the current deferred frame boundary. */
    public Snapshot frameSnapshot() { return frame.get(); }
    public long generation() { return generation.get(); }

    void beginFrame(long id) {
        if (frameId.get() == id) return;
        frame.set(current.get());
        frameId.set(id);
    }

    public void setFeatureOverride(DeferredFeature feature, DeferredFeatureOverride override) {
        if (feature == null) throw new IllegalArgumentException("feature");
        if (override == null) override = DeferredFeatureOverride.DEFAULT;
        Snapshot previous;
        Snapshot next;
        do {
            previous = current.get();
            if (previous.override(feature) == override) return;
            EnumMap<DeferredFeature, DeferredFeatureOverride> map = new EnumMap<>(DeferredFeature.class);
            map.putAll(previous.overrides());
            if (override == DeferredFeatureOverride.DEFAULT) map.remove(feature); else map.put(feature, override);
            next = previous.withOverrides(map);
        } while (!current.compareAndSet(previous, next));
        generation.incrementAndGet();
        requestTemporalReset(feature);
        syncWaterRouting(next);
    }

    public void clearFeatureOverrides() {
        Snapshot previous;
        do {
            previous = current.get();
            if (previous.overrides().isEmpty()) return;
        } while (!current.compareAndSet(previous, previous.withOverrides(Map.of())));
        generation.incrementAndGet();
        for (DeferredFeature feature : previous.overrides().keySet()) requestTemporalReset(feature);
        syncWaterRouting(current.get());
    }

    public void setIsolationMode(boolean enabled) {
        Snapshot before = current.get();
        if (before.isolationMode() == enabled) return;
        update(snapshot -> snapshot.isolationMode() == enabled ? snapshot : snapshot.withIsolationMode(enabled));
        // Isolation changes the effective enabled-state of every hooked temporal subsystem.
        for (DeferredFeature feature : DeferredFeature.values()) {
            if (isHookedFeature(feature)) requestTemporalReset(feature);
        }
        syncWaterRouting(current.get());
    }

    public void setDebugView(DeferredDebugView view) {
        DeferredDebugView resolved = view == null ? DeferredDebugView.OFF : view;
        update(snapshot -> snapshot.debugView() == resolved ? snapshot : snapshot.withDebugView(resolved));
        if (resolved == DeferredDebugView.OFF) unavailableDebugReason.set("");
    }

    public void setVolumeSlice(DeferredDebugVolumeAxis axis, float normalizedPosition) {
        DeferredDebugVolumeAxis resolvedAxis = axis == null ? DeferredDebugVolumeAxis.Z : axis;
        float position = Float.isFinite(normalizedPosition)
                ? Math.max(0.0f, Math.min(1.0f, normalizedPosition)) : 0.5f;
        update(snapshot -> snapshot.withVolumeSlice(resolvedAxis, position));
    }

    public boolean featureEnabled(DeferredFeature feature, DeferredRuntimeConfig.Snapshot production) {
        return resolveFeature(feature, production, current.get());
    }

    boolean featureEnabledForFrame(DeferredFeature feature, DeferredRuntimeConfig.Snapshot production) {
        return resolveFeature(feature, production, frame.get());
    }

    boolean cloudWorkEnabledForFrame() {
        return resolveFeature(DeferredFeature.CLOUDS, DeferredRuntimeConfig.current(), frame.get());
    }

    String featureDisableReasonForFrame(DeferredFeature feature, DeferredRuntimeConfig.Snapshot production) {
        if (feature == null) return "";
        Snapshot state = frame.get();
        DeferredFeatureOverride override = state.override(feature);
        if (override == DeferredFeatureOverride.FORCE_ENABLED) return "";
        if (override == DeferredFeatureOverride.FORCE_DISABLED) return "forced disabled by debug override";
        if (state.isolationMode() && isHookedFeature(feature)) return "disabled by smoke isolation";
        if (!feature.productionEnabled(production)) return "disabled by production setting";
        return "";
    }

    private static boolean resolveFeature(DeferredFeature feature, DeferredRuntimeConfig.Snapshot production, Snapshot state) {
        if (feature == null) return true;
        DeferredFeatureOverride override = state.override(feature);
        if (override == DeferredFeatureOverride.FORCE_ENABLED) return true;
        if (override == DeferredFeatureOverride.FORCE_DISABLED) return false;
        if (state.isolationMode() && isHookedFeature(feature)) return false;
        return feature.productionEnabled(production);
    }

    /** Sky/weather remain available in isolation; clouds are a real expensive subsystem and are isolation-gated. */
    private static boolean isHookedFeature(DeferredFeature feature) {
        return feature != DeferredFeature.SKY && feature != DeferredFeature.WEATHER;
    }

    Set<DeferredFeature> consumePendingTemporalResetFeatures() {
        EnumSet<DeferredFeature> result = pendingTemporalResets.getAndSet(EnumSet.noneOf(DeferredFeature.class));
        if (!result.isEmpty()) lastResetReason.set(DeferredHistoryResetReason.SUBSYSTEM_REENABLED);
        return Set.copyOf(result);
    }

    public DeferredHistoryResetReason lastHistoryResetReason() { return lastResetReason.get(); }
    public String unavailableDebugResourceReason() { return unavailableDebugReason.get(); }
    void setUnavailableDebugResourceReason(String reason) { unavailableDebugReason.set(reason == null ? "" : reason); }

    private void requestTemporalReset(DeferredFeature feature) {
        if (feature.temporalHistories().isEmpty()) return;
        pendingTemporalResets.getAndUpdate(currentSet -> {
            EnumSet<DeferredFeature> next = currentSet.isEmpty()
                    ? EnumSet.noneOf(DeferredFeature.class) : EnumSet.copyOf(currentSet);
            next.add(feature);
            return next;
        });
    }

    private static void syncWaterRouting(Snapshot state) {
        // Sodium replacement must be released before the next terrain submission whenever WATER
        // becomes effectively disabled. FORCE_ENABLED still wins while isolation mode is active.
        if (!resolveFeature(DeferredFeature.WATER, DeferredRuntimeConfig.current(), state)) {
            combatant.client.render.sodium.fluid.WaterSurfacePatchRouting.setReplacementActive(false);
        }
    }

    private void update(java.util.function.UnaryOperator<Snapshot> editor) {
        Snapshot previous;
        Snapshot next;
        do {
            previous = current.get();
            next = editor.apply(previous);
            if (next == previous || next.equals(previous)) return;
        } while (!current.compareAndSet(previous, next));
        generation.incrementAndGet();
    }

    public record Snapshot(Map<DeferredFeature, DeferredFeatureOverride> overrides,
                           boolean isolationMode,
                           DeferredDebugView debugView,
                           DeferredDebugVolumeAxis volumeAxis,
                           float volumeSlice) {
        public Snapshot {
            EnumMap<DeferredFeature, DeferredFeatureOverride> copy = new EnumMap<>(DeferredFeature.class);
            if (overrides != null) copy.putAll(overrides);
            overrides = Collections.unmodifiableMap(copy);
            if (debugView == null) debugView = DeferredDebugView.OFF;
            if (volumeAxis == null) volumeAxis = DeferredDebugVolumeAxis.Z;
            volumeSlice = Float.isFinite(volumeSlice) ? Math.max(0.0f, Math.min(1.0f, volumeSlice)) : 0.5f;
        }

        static Snapshot defaults() {
            return new Snapshot(Map.of(), false, DeferredDebugView.OFF, DeferredDebugVolumeAxis.Z, 0.5f);
        }

        public DeferredFeatureOverride override(DeferredFeature feature) {
            return overrides.getOrDefault(feature, DeferredFeatureOverride.DEFAULT);
        }

        Snapshot withOverrides(Map<DeferredFeature, DeferredFeatureOverride> value) {
            return new Snapshot(value, isolationMode, debugView, volumeAxis, volumeSlice);
        }
        Snapshot withIsolationMode(boolean value) {
            return new Snapshot(overrides, value, debugView, volumeAxis, volumeSlice);
        }
        Snapshot withDebugView(DeferredDebugView value) {
            return new Snapshot(overrides, isolationMode, value, volumeAxis, volumeSlice);
        }
        Snapshot withVolumeSlice(DeferredDebugVolumeAxis axis, float position) {
            return new Snapshot(overrides, isolationMode, debugView, axis, position);
        }
    }
}
