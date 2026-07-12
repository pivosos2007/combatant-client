/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text;

public final class TextCommandStats {
    private int recorded;
    private int submitted;
    private int vanilla;
    private int bitmap;
    private int msdf;
    private int worldPlacements;
    private int effect;
    private int clipped;
    private int glyphs;
    private int meshUploads;
    private int routerCacheHits;
    private int routerCacheMisses;
    private int routerFallbackMisses;
    private int adjacentBatchedCommands;
    private int directAdjacentBatchedCommands;
    private long uploadedVertexBytes;
    private long uploadedIndexBytes;

    public void reset() {
        recorded = submitted = vanilla = bitmap = msdf = worldPlacements = effect = clipped = glyphs = meshUploads = 0;
        routerCacheHits = routerCacheMisses = routerFallbackMisses = adjacentBatchedCommands = directAdjacentBatchedCommands = 0;
        uploadedVertexBytes = uploadedIndexBytes = 0L;
    }

    public void recorded() {
        recorded++;
    }

    public void submitted() {
        submitted++;
    }

    public void backend(TextBackendPreference backend) {
        if (backend == null) return;
        switch (backend) {
            case VANILLA_SODIUM -> vanilla++;
            case BITMAP_ATLAS -> bitmap++;
            case MSDF -> msdf++;
            default -> {
            }
        }
    }

    public void worldPlacement() {
        worldPlacements++;
    }

    public void effect() {
        effect++;
    }

    public void clipped() {
        clipped++;
    }

    public void glyphs(int count) {
        glyphs += Math.max(0, count);
    }

    public void meshUpload(int vertexBytes, int indexBytes) {
        meshUploads++;
        uploadedVertexBytes += Math.max(0, vertexBytes);
        uploadedIndexBytes += Math.max(0, indexBytes);
    }

    public void routerCacheHit() {
        routerCacheHits++;
    }

    public void routerCacheMiss() {
        routerCacheMisses++;
    }

    public void routerFallbackMiss() {
        routerFallbackMisses++;
    }

    public void adjacentTextBatch(int commands) {
        adjacentBatchedCommands += Math.max(0, commands);
    }

    public void directAdjacentTextBatch(int commands) {
        directAdjacentBatchedCommands += Math.max(0, commands);
    }

    public TextCommandStatsSnapshot snapshot() {
        return new TextCommandStatsSnapshot(recorded, submitted, vanilla, bitmap, msdf, worldPlacements, effect, clipped,
                glyphs, meshUploads, routerCacheHits, routerCacheMisses, routerFallbackMisses, adjacentBatchedCommands,
                directAdjacentBatchedCommands,
                uploadedVertexBytes, uploadedIndexBytes);
    }
}
