/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;

/** Combined sampled image/sampler binding for native shader stages. */
public record SampledTextureBinding(int binding,
                                    GpuTextureView texture,
                                    GpuSampler sampler) {
    public SampledTextureBinding {
        if (binding < 0) throw new IllegalArgumentException("binding");
        if (texture == null || sampler == null) throw new IllegalArgumentException("texture/sampler");
    }
}
