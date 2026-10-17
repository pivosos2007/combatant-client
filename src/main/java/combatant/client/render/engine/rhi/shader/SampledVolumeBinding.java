/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

import com.mojang.renderpearl.api.textures.GpuSampler;

/** Combined sampler3D binding. The view may expose one mip or a sampled mip range. */
public record SampledVolumeBinding(int binding,
                                   RhiVolumeView view,
                                   GpuSampler sampler) {
    public SampledVolumeBinding {
        if (binding < 0) throw new IllegalArgumentException("binding");
        if (view == null || sampler == null) throw new IllegalArgumentException("view/sampler");
        view.requireValid();
        if (!view.descriptor().sampled()) {
            throw new IllegalArgumentException("Sampled volume binding requires a SAMPLED volume view");
        }
    }

    public SampledVolumeBinding(int binding, RhiStorageVolume volume, GpuSampler sampler) {
        this(binding, volume.sampledView(), sampler);
    }
}
