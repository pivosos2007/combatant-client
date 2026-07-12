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

    boolean render(GpuTextureView src, GpuTextureView dst, float tickDelta);

    default boolean render(PostProcessContext context, GpuTextureView src, GpuTextureView dst) {
        return render(src, dst, context != null ? context.tickDelta() : 0.0f);
    }

    default int getPriority() {
        return 0;
    }

    default Phase getPhase() {
        return Phase.PRE_HAND;
    }

    enum Phase {PRE_HAND, POST_HAND}
}
