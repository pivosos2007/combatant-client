/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.accessors;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.opengl.FrameBufferCache;
import com.mojang.blaze3d.opengl.GlTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GlTexture.class)
public interface GlTextureInvoker {
    @Invoker("<init>")
    static GlTexture combatant$create(int usage,
                                      String label,
                                      GpuFormat format,
                                      int width,
                                      int height,
                                      int depthOrLayers,
                                      int mipLevels,
                                      int glId,
                                      FrameBufferCache frameBufferCache) {
        throw new AssertionError();
    }
}


