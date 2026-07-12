/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.core.policy;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.jetbrains.annotations.Nullable;
import combatant.client.mixins.accessors.GameRendererAccessor;

/**
 * Vanilla world fog provider used by world pass compilation until Stage 8 pipeline metadata is global.
 */
public final class VanillaWorldFogProvider implements FogProvider {
    public static final VanillaWorldFogProvider INSTANCE = new VanillaWorldFogProvider();

    private VanillaWorldFogProvider() {
    }

    @Override
    public @Nullable GpuBufferSlice fogUniformSlice() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !(mc.gameRenderer instanceof GameRendererAccessor accessor)) return null;
        FogRenderer fogRenderer = accessor.combatant$getFogRenderer();
        return fogRenderer != null ? fogRenderer.getBuffer(FogRenderer.FogMode.WORLD) : null;
    }
}
