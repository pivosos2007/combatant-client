/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import java.util.Map;

/** Immutable renderer-side snapshot suitable for smoke tooling and a future UI adapter. */
public record DeferredDebugDiagnostics(
        DeferredWorldPipeline.LifecycleState lifecycleState,
        String backend,
        DeferredDebugView selectedDebugView,
        Map<DeferredFeature, DeferredFeatureOverride> featureOverrides,
        boolean isolationMode,
        DeferredDebugVolumeAxis volumeAxis,
        float volumeSlice,
        DeferredHistoryResetReason historyResetReason,
        String unavailableDebugResourceReason,
        DeferredEnvironmentCaptureDiagnostics environmentCapture,
        Map<DeferredResource, DeferredResourceProvenance> resourceProvenance
) {
    public DeferredDebugDiagnostics {
        backend = backend == null || backend.isBlank() ? "unknown" : backend;
        selectedDebugView = selectedDebugView == null ? DeferredDebugView.OFF : selectedDebugView;
        featureOverrides = featureOverrides == null ? Map.of() : Map.copyOf(featureOverrides);
        volumeAxis = volumeAxis == null ? DeferredDebugVolumeAxis.Z : volumeAxis;
        historyResetReason = historyResetReason == null ? DeferredHistoryResetReason.NONE : historyResetReason;
        unavailableDebugResourceReason = unavailableDebugResourceReason == null ? "" : unavailableDebugResourceReason;
        environmentCapture = environmentCapture == null
                ? DeferredEnvironmentCaptureDiagnostics.unknown(Long.MIN_VALUE) : environmentCapture;
        resourceProvenance = resourceProvenance == null ? Map.of() : Map.copyOf(resourceProvenance);
    }
}
