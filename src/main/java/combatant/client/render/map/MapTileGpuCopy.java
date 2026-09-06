/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import com.mojang.blaze3d.textures.GpuTexture;

/** Provider-neutral GPU tile payload copied into Combatant-owned atlas storage. */
public record MapTileGpuCopy(GpuTexture source,
                             int sourceX,
                             int sourceY,
                             int width,
                             int height) {
    public MapTileGpuCopy {
        if (source == null) throw new NullPointerException("source");
        if (sourceX < 0 || sourceY < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid GPU tile copy region.");
        }
    }

    public int byteSize() {
        return Math.multiplyExact(Math.multiplyExact(width, height), 4);
    }
}
