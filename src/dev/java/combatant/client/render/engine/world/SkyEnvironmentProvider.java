/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world;

import combatant.client.render.engine.deferred.DeferredPrimaryViewSource;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import net.minecraft.resources.Identifier;

/**
 * Explicit producer of HDR sky/environment resources. Implementations own generation policy;
 * deferred BRDF/reflections only consume the resulting named resources.
 */
public interface SkyEnvironmentProvider {
    Identifier id();

    SkyEnvironmentDescriptor describe(WorldRenderState worldState, long frameId);

    /** Legacy sky-only producer surface retained for addon compatibility. */
    void render(RenderContext context, RhiStorageImage target);

    /**
     * Canonical multi-resource environment target. Existing sky-only providers automatically write
     * only SKY_RADIANCE and leave atmosphere resources neutral.
     */
    default void render(RenderContext context, TargetSet targets) {
        render(context, targets.skyRadiance());
    }

    /** Called on resource reload/backend switch. Providers must release RHI-owned state for owner. */
    default void releaseBackendResources(CombatantRhi owner) {
    }

    record RenderContext(CombatantRhi rhi,
                         WorldRenderState worldState,
                         DeferredPrimaryViewSource.FrameView view,
                         long frameId) {
    }

    /** Canonical targets allocated by the deferred environment boundary. */
    record TargetSet(
            RhiStorageImage skyRadiance,
            RhiStorageImage atmosphereTransmittance,
            RhiStorageImage atmosphereMultiScattering,
            RhiStorageImage aerialPerspective,
            RhiStorageImage aerialTransmittance
    ) {
    }
}
