/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

/** Chunk/region grid style and density limits. */
public record MapGridSpec(int chunkBlockSpan,
                          int regionChunkSpan,
                          double minimumChunkSpacingPixels,
                          int chunkLineArgb,
                          int regionLineArgb,
                          int coordinateLabelArgb,
                          int maxLines) {
    public static final MapGridSpec DEFAULT = new MapGridSpec(
            16,
            32,
            5.0,
            0x264A4A55,
            0x705F6070,
            0xB8E6E6EC,
            4096
    );

    public MapGridSpec {
        if (chunkBlockSpan <= 0 || regionChunkSpan <= 0 || maxLines <= 0) {
            throw new IllegalArgumentException("Grid spans and limits must be positive.");
        }
        if (!Double.isFinite(minimumChunkSpacingPixels) || minimumChunkSpacingPixels < 0.0) {
            throw new IllegalArgumentException("Grid pixel spacing must be finite and non-negative.");
        }
    }
}
