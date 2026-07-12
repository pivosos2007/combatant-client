/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.blit;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.rhi.RhiStats;

public final class Blaze3dTextureBlitter implements TextureBlitter {
    private final RhiStats stats;

    public Blaze3dTextureBlitter(RhiStats stats) {
        this.stats = stats;
    }

    @Override
    public boolean copyFast(GpuTextureView src, GpuTextureView dst) {
        if (src == null || dst == null || src == dst) return true;
        GpuTexture srcTexture = src.texture();
        GpuTexture dstTexture = dst.texture();
        if (srcTexture == null || dstTexture == null || srcTexture == dstTexture) return true;
        if (src.getWidth(0) != dst.getWidth(0) || src.getHeight(0) != dst.getHeight(0)) return false;
        if (srcTexture.getFormat() != dstTexture.getFormat()) return false;
        if ((srcTexture.usage() & GpuTexture.USAGE_COPY_SRC) == 0) return false;
        if ((dstTexture.usage() & GpuTexture.USAGE_COPY_DST) == 0) return false;

        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                srcTexture,
                dstTexture,
                src.baseMipLevel(),
                0,
                0,
                0,
                0,
                src.getWidth(0),
                src.getHeight(0)
        );
        stats.textureFastCopy();
        return true;
    }
}
