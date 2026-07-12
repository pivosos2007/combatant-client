/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.postprocess;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;

public record PostProcessContext(
        PostProcessPass.Phase phase,
        float tickDelta,
        RenderTarget mainFramebuffer,
        GpuTextureView mainColor,
        @Nullable GpuTextureView mainDepth,
        @Nullable GpuTextureView preTranslucentDepth,
        @Nullable GpuTextureView staticWorldDepth,
        int width,
        int height
) {
}
