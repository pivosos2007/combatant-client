/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model.gpu;

public record ModelGpuResidencyStats(
        int residentAssets,
        int residentPrimitives,
        int residentTextureVariants,
        long textureGpuBytesEstimate,
        long uploads,
        long cacheHits,
        long invalidations
) {
}
