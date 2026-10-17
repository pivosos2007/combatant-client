/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.iris.geometry;

import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

record IrisCanonicalMaterialAtlas(GpuTexture texture, GpuTextureView view, GpuSampler sampler) implements AutoCloseable {
    @Override public void close() {
        try { view.close(); } catch (Throwable ignored) { }
        try { texture.close(); } catch (Throwable ignored) { }
    }
}
