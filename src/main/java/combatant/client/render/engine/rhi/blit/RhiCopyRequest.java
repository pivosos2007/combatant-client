/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.blit;

import com.mojang.blaze3d.textures.GpuTextureView;

/** Complete copy intent; backend selection must not infer scaling or conversion accidentally. */
public record RhiCopyRequest(
        GpuTextureView source,
        GpuTextureView destination,
        Region sourceRegion,
        Region destinationRegion,
        int sourceSamples,
        int destinationSamples,
        Filter filter,
        boolean materialConversion
) {
    public enum Filter { NEAREST, LINEAR }

    public record Region(int x, int y, int width, int height) {
        public Region {
            width = Math.max(0, width);
            height = Math.max(0, height);
        }
    }

    public RhiCopyRequest {
        sourceSamples = Math.max(1, sourceSamples);
        destinationSamples = Math.max(1, destinationSamples);
        filter = filter != null ? filter : Filter.NEAREST;
    }

    public static RhiCopyRequest exact(GpuTextureView source, GpuTextureView destination) {
        int sourceWidth = source != null ? source.getWidth(0) : 0;
        int sourceHeight = source != null ? source.getHeight(0) : 0;
        int destinationWidth = destination != null ? destination.getWidth(0) : 0;
        int destinationHeight = destination != null ? destination.getHeight(0) : 0;
        return new RhiCopyRequest(
                source,
                destination,
                new Region(0, 0, sourceWidth, sourceHeight),
                new Region(0, 0, destinationWidth, destinationHeight),
                1,
                1,
                Filter.NEAREST,
                false
        );
    }

    public boolean scales() {
        return sourceRegion != null && destinationRegion != null
                && (sourceRegion.width != destinationRegion.width || sourceRegion.height != destinationRegion.height);
    }

    public boolean sameFormat() {
        return source != null && destination != null
                && source.texture() != null && destination.texture() != null
                && source.texture().getFormat() == destination.texture().getFormat();
    }
}
