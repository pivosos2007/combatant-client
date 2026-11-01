/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model.render;

public record ImportedAssetCompatibilityRenderStats(
        long frameId,
        int visibleInstances,
        int submittedPrimitives,
        int skippedSkinnedPrimitives,
        int skippedTranslucentPrimitives
) {
    public static final ImportedAssetCompatibilityRenderStats EMPTY = new ImportedAssetCompatibilityRenderStats(-1L, 0, 0, 0, 0);
}
