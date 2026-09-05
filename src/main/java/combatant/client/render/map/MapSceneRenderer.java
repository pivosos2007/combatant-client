/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import combatant.client.render.engine.text.TextRenderer;

import java.util.List;

/** Owns the terrain and overlay passes for a map scene, independent of any GUI screen. */
public final class MapSceneRenderer implements AutoCloseable {
    private final MapTileRenderer terrainRenderer;
    private final MapOverlayProjector overlayProjector = new MapOverlayProjector();
    private final MapOverlayRenderer overlayRenderer = new MapOverlayRenderer();
    private final int maxUploadsPerFrame;
    private final long maxUploadBytesPerFrame;

    public MapSceneRenderer(MapTileRenderer terrainRenderer,
                            int maxUploadsPerFrame,
                            long maxUploadBytesPerFrame) {
        if (terrainRenderer == null) throw new NullPointerException("terrainRenderer");
        if (maxUploadsPerFrame <= 0 || maxUploadBytesPerFrame <= 0L) {
            throw new IllegalArgumentException("Per-frame upload budgets must be positive.");
        }
        this.terrainRenderer = terrainRenderer;
        this.maxUploadsPerFrame = maxUploadsPerFrame;
        this.maxUploadBytesPerFrame = maxUploadBytesPerFrame;
    }

    public FrameResult render(long frameId,
                              MapViewport viewport,
                              MapSceneDataSnapshot snapshot,
                              TextRenderer textRenderer) {
        if (viewport == null) throw new NullPointerException("viewport");
        MapSceneDataSnapshot scene = snapshot == null ? MapSceneDataSnapshot.EMPTY : snapshot;
        terrainRenderer.beginFrame(frameId);
        List<MapTileDrawBatch> terrain = terrainRenderer.prepare(scene.terrain());
        int immediateUploads = terrainRenderer.processUploads(maxUploadsPerFrame, maxUploadBytesPerFrame);
        if (immediateUploads > 0) {
            terrain = terrainRenderer.prepare(scene.terrain());
        }
        terrainRenderer.render(viewport, terrain);

        MapOverlayDrawList overlays = overlayProjector.project(viewport, scene.overlays(), scene.grid());
        overlayRenderer.render(viewport.screenBounds(), overlays, textRenderer);
        return new FrameResult(
                scene.generation(),
                terrain.stream().mapToInt(batch -> batch.entries().size()).sum(),
                immediateUploads,
                overlays.hitIndex(),
                terrainRenderer.residencyStats(),
                terrainRenderer.uploadStats()
        );
    }

    @Override
    public void close() {
        terrainRenderer.close();
    }

    public record FrameResult(long sceneGeneration,
                              int terrainTilesDrawn,
                              int immediateUploads,
                              MapHitIndex hitIndex,
                              MapTileResidencyCache.Stats residency,
                              MapTileUploadQueue.Stats uploadQueue) {
    }
}
