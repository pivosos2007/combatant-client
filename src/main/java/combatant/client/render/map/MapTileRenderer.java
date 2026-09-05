/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.helpers.ClipFunction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Page-batched terrain renderer backed by a bounded fixed-slot atlas. */
public final class MapTileRenderer implements AutoCloseable {
    private final MapTileAtlas atlas;
    private final MapTileResidencyCache residency;
    private final MapTileUploadQueue uploads;

    public MapTileRenderer(int tileResolution,
                           int atlasPages,
                           int atlasColumns,
                           int atlasRows,
                           int maxPendingUploads,
                           long maxPendingBytes) {
        this.atlas = new MapTileAtlas(tileResolution, atlasPages, atlasColumns, atlasRows);
        this.residency = new MapTileResidencyCache(atlasPages, atlas.pageCapacity());
        this.uploads = new MapTileUploadQueue(maxPendingUploads, maxPendingBytes);
    }

    public void beginFrame(long frameId) {
        residency.beginFrame(frameId);
    }

    public List<MapTileDrawBatch> prepare(List<MapTerrainTile> visibleTiles) {
        if (visibleTiles == null || visibleTiles.isEmpty()) return List.of();
        Map<Integer, List<MapTileDrawBatch.Entry>> byPage = new LinkedHashMap<>();
        for (MapTerrainTile tile : visibleTiles) {
            if (tile == null) continue;
            MapTileResidencyCache.Acquisition acquisition = residency.acquire(tile.key(), tile.revision());
            if (!acquisition.available()) continue;
            if ((acquisition.uploadRequired() || !atlas.isUploaded(acquisition.slot())) && tile.pixels() != null) {
                uploads.offer(tile.key(), tile.revision(), acquisition.slot(), tile.pixels());
            }
            if (!atlas.isUploaded(acquisition.slot())) continue;
            byPage.computeIfAbsent(acquisition.slot().page(), ignored -> new ArrayList<>()).add(
                    new MapTileDrawBatch.Entry(
                            tile.key(),
                            tile.revision(),
                            acquisition.slot(),
                            tile.worldBounds(),
                            tile.tintArgb()
                    )
            );
        }
        return byPage.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new MapTileDrawBatch(entry.getKey(), entry.getValue()))
                .toList();
    }

    public int processUploads(int maxUploads, long maxBytes) {
        int processed = 0;
        for (MapTileUploadQueue.Upload upload : uploads.drain(maxUploads, maxBytes)) {
            if (!residency.isCurrent(upload.key(), upload.slot())) continue;
            atlas.upload(upload.slot(), upload.pixels());
            processed++;
        }
        return processed;
    }

    public void render(MapViewport viewport, List<MapTileDrawBatch> batches) {
        if (viewport == null || batches == null || batches.isEmpty()) return;
        try (ClipFunction.Scope ignored = ClipFunction.rectScope(
                viewport.screenBounds().x(),
                viewport.screenBounds().y(),
                viewport.screenBounds().width(),
                viewport.screenBounds().height())) {
            List<MapTileDrawBatch> ordered = batches.stream()
                    .sorted(Comparator.comparingInt(MapTileDrawBatch::page))
                    .toList();
            for (MapTileDrawBatch batch : ordered) {
                MapTileAtlas.PageView page = atlas.pageView(batch.page());
                Renderer2D.TEXTURE.begin();
                for (MapTileDrawBatch.Entry entry : batch.entries()) {
                    MapScreenPoint topLeft = viewport.project(entry.worldBounds().x(), entry.worldBounds().y());
                    MapScreenPoint bottomRight = viewport.project(entry.worldBounds().maxX(), entry.worldBounds().maxY());
                    MapTileAtlas.UvRect uv = atlas.uv(entry.slot());
                    Renderer2D.TEXTURE.texQuad(
                            topLeft.x(),
                            topLeft.y(),
                            bottomRight.x() - topLeft.x(),
                            bottomRight.y() - topLeft.y(),
                            uv.minU(),
                            uv.minV(),
                            uv.maxU(),
                            uv.maxV(),
                            entry.tintArgb()
                    );
                }
                Renderer2D.TEXTURE.render(page.textureView(), page.sampler());
            }
        }
    }

    public MapTileResidencyCache.Stats residencyStats() {
        return residency.stats();
    }

    public MapTileUploadQueue.Stats uploadStats() {
        return uploads.stats();
    }

    @Override
    public void close() {
        atlas.close();
    }
}
