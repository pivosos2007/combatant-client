/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.resource;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;

/** RHI adapter over Mojang's sampler cache. */
public final class SamplerCache {
    public GpuSampler get(AddressMode u, AddressMode v, FilterMode min, FilterMode mag, boolean mipmap) {
        return RenderSystem.getSamplerCache().getSampler(u, v, min, mag, mipmap);
    }

    public GpuTextureView createTextureView(com.mojang.renderpearl.api.textures.GpuTexture texture) {
        return RenderSystem.getDevice().createTextureView(texture);
    }
}
