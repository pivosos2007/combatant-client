/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.AdvancedShaderBackend;
import combatant.client.render.engine.world.WorldRenderState;

public record DeferredPassContext(
        DeferredStage stage,
        RenderFrameContext frame,
        CombatantRhi rhi,
        DeferredResourceBindings resources,
        DeferredSecondaryViewRegistry secondaryViews,
        DeferredPrimaryViewSource primaryView,
        DeferredTemporalHistoryRegistry temporalHistory,
        WorldRenderState worldState,
        DeferredRuntimeConfig.Snapshot settings
) {
    public DeferredPassContext {
        if (temporalHistory == null) temporalHistory = new DeferredTemporalHistoryRegistry();
        if (worldState == null) worldState = WorldRenderState.unknown(0L);
        if (settings == null) settings = DeferredRuntimeConfig.current();
    }

    public AdvancedShaderBackend advancedShaders() {
        return rhi.advancedShaders();
    }

    public boolean featureEnabled(DeferredFeature feature) {
        return DeferredSmokeTestState.global().featureEnabledForFrame(feature, settings);
    }

    public DeferredHistoryDescriptor history() {
        return primaryView.historyDescriptor();
    }

    public DeferredTemporalHistoryDescriptor history(DeferredTemporalHistoryId id) {
        return temporalHistory.descriptor(id);
    }

    /** Persistent history is false here until a successful producer has initialized it. */
    public boolean isValid(DeferredResource resource) {
        return resources.isValid(resource);
    }
}
