/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text;

public record TextCommandStatsSnapshot(int recorded,
                                       int submitted,
                                       int vanilla,
                                       int bitmap,
                                       int msdf,
                                       int worldPlacements,
                                       int effect,
                                       int clipped,
                                       int glyphs,
                                       int meshUploads,
                                       int routerCacheHits,
                                       int routerCacheMisses,
                                       int routerFallbackMisses,
                                       int adjacentBatchedCommands,
                                       int directAdjacentBatchedCommands,
                                       long uploadedVertexBytes,
                                       long uploadedIndexBytes) {
    /**
     * Compatibility alias for old profiler labels.
     */
    @Deprecated
    public int world() {
        return worldPlacements;
    }
}
