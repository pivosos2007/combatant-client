/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.blit;

import com.mojang.renderpearl.api.textures.GpuTextureView;

/**
 * Attempts texture copies without a shader draw and reports whether the backend submitted them.
 */
public interface TextureBlitter {
    /** Attempts the selected non-shader path and reports whether it was submitted. */
    boolean copy(RhiCopyRequest request);

    /**
     * Returns true if the copy was handled by a backend texture-copy command.
     */
    default boolean copyFast(GpuTextureView src, GpuTextureView dst) {
        return copy(RhiCopyRequest.exact(src, dst));
    }
}
