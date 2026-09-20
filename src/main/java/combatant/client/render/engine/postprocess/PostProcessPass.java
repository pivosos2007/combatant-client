/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.postprocess;

import com.mojang.blaze3d.textures.GpuTextureView;

public interface PostProcessPass {
    boolean isActive();

    /** Compatibility entry point used by the production visual effects. */
    default boolean render(GpuTextureView source, GpuTextureView destination, float tickDelta) {
        return false;
    }

    default boolean render(PostProcessContext context, GpuTextureView source, GpuTextureView destination) {
        return render(source, destination, context != null ? context.tickDelta() : 0.0f);
    }

    /** Modern graph entry point. Legacy effects are adapted to the texture-view contract. */
    default boolean render(PostProcessExecutionContext execution) {
        if (execution == null) return false;
        return render(execution.context(), execution.source(), execution.destination());
    }

    /**
     * Whether this pass can profit from a storage-capable graph destination on the active backend.
     * This is a preference, not a requirement: graph allocation or compute failure must fall back to raster.
     */
    default boolean prefersStorageOutput(combatant.client.render.engine.rhi.CombatantRhi rhi) {
        return false;
    }

    default int getPriority() {
        return 0;
    }

    default Phase getPhase() {
        return Phase.PRE_HAND;
    }

    enum Phase {PRE_HAND, POST_HAND}
}
