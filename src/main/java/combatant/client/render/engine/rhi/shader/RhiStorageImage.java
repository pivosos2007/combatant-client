/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

import com.mojang.renderpearl.api.textures.GpuTextureView;

/** Storage-capable image still owned by Mojang's active graphics device. */
public interface RhiStorageImage extends AutoCloseable {
    StorageImageDescriptor descriptor();

    /** Full mip-chain view used when the image re-enters ordinary sampled graphics paths. */
    GpuTextureView view();

    /** Single-mip view used for native image load/store bindings. */
    default GpuTextureView storageView(int mipLevel) {
        if (mipLevel != 0) {
            throw new IndexOutOfBoundsException("Storage image exposes only mip 0");
        }
        return view();
    }

    @Override
    void close();
}
