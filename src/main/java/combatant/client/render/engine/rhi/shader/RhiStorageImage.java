/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

import com.mojang.blaze3d.textures.GpuTextureView;

/** Storage-capable image still owned by Mojang's active graphics device. */
public interface RhiStorageImage extends AutoCloseable {
    StorageImageDescriptor descriptor();

    /** Ordinary Mojang texture view so the result can re-enter normal graphics/material paths. */
    GpuTextureView view();

    @Override
    void close();
}
