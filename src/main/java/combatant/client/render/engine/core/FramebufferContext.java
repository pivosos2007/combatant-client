/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.core;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

public record FramebufferContext(@Nullable RenderTarget mainFramebuffer) {
    public static FramebufferContext capture() {
        Minecraft mc = Minecraft.getInstance();
        return new FramebufferContext(mc != null ? mc.gameRenderer.mainRenderTarget() : null);
    }
}
