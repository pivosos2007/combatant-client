/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuTexture;
import org.spongepowered.asm.mixin.Mixin;
import combatant.client.mixininterface.IGpuDevice;
import combatant.client.mixininterface.IMsaaDevice;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.rhi.scissor.GlobalScissorState;

@Mixin(GpuDevice.class)
public abstract class GpuDeviceMixin implements IGpuDevice, IMsaaDevice {
    @Override
    public void combatant$pushScissor(int x, int y, int width, int height) {
        GlobalScissorState.push(x, y, width, height);
    }

    @Override
    public void combatant$popScissor() {
        GlobalScissorState.pop();
    }

    @Override
    public GpuTexture combatant$createMsaaTexture(String label, int usage, GpuFormat format, int width, int height, int samples) {
        return CombatantRenderSystem.rhi().msaa().createTexture(label, usage, format, width, height, samples);
    }
}
