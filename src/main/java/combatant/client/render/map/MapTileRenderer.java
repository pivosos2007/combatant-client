/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.ui.UiDirectTexturedRenderer;
import combatant.client.render.engine.uniform.MeshBuilder;
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
    private final MeshBuilder litMesh = new MeshBuilder(CombatantRenderPipelines.UI_MAP_TILE_LIGHT);
    private final MeshBuilder opaqueMesh = new MeshBuilder(CombatantRenderPipelines.UI_MAP_TILE_OPAQUE);

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
        Map<BatchKey, List<MapTileDrawBatch.Entry>> batches = new LinkedHashMap<>();
        for (MapTerrainTile tile : visibleTiles) {
            if (tile == null) continue;
            MapTileResidencyCache.Acquisition acquisition = residency.acquire(tile.key(), tile.revision());
            if (!acquisition.available()) continue;
            if (acquisition.uploadRequired() || !atlas.isUploaded(acquisition.slot())) {
                if (tile.pixels() != null) {
                    uploads.offer(tile.key(), tile.revision(), acquisition.slot(), tile.pixels());
                } else if (tile.gpuCopy() != null) {
                    uploads.offer(tile.key(), tile.revision(), acquisition.slot(), tile.gpuCopy());
                }
            }
            if (!atlas.isUploaded(acquisition.slot())) continue;
            BatchKey batchKey = new BatchKey(
                    tile.key().coordinate().lod(), tile.material(), acquisition.slot().page());
            batches.computeIfAbsent(batchKey, ignored -> new ArrayList<>()).add(
                    new MapTileDrawBatch.Entry(
                            tile.key(),
                            tile.revision(),
                            acquisition.slot(),
                            tile.worldBounds(),
                            tile.textureUv(),
                            tile.tintArgb()
                    )
            );
        }
        return batches.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new MapTileDrawBatch(
                        entry.getKey().lod(), entry.getKey().material(), entry.getKey().page(), entry.getValue()))
                .toList();
    }

    public int processUploads(int maxUploads, long maxBytes) {
        int processed = 0;
        for (MapTileUploadQueue.Upload upload : uploads.drain(maxUploads, maxBytes)) {
            if (!residency.isCurrent(upload.key(), upload.slot())) continue;
            if (upload.pixels() != null) {
                atlas.upload(upload.slot(), upload.pixels());
            } else {
                atlas.copy(upload.slot(), upload.gpuCopy());
            }
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
                    .sorted(Comparator
                            .comparingInt(MapTileDrawBatch::lod).reversed()
                            .thenComparing(MapTileDrawBatch::material)
                            .thenComparingInt(MapTileDrawBatch::page))
                    .toList();
            for (MapTileDrawBatch batch : ordered) {
                MapTileAtlas.PageView page = atlas.pageView(batch.page());
                if (batch.material() == MapTileMaterial.RGBA) {
                    renderRgba(viewport, batch, page);
                } else {
                    renderEncoded(viewport, batch, page);
                }
            }
        }
    }

    private void renderRgba(MapViewport viewport, MapTileDrawBatch batch, MapTileAtlas.PageView page) {
        Renderer2D.TEXTURE.begin();
        for (MapTileDrawBatch.Entry entry : batch.entries()) {
            ProjectedTile tile = project(viewport, entry);
            Renderer2D.TEXTURE.texQuad(
                    tile.x(), tile.y(), tile.width(), tile.height(),
                    tile.minU(), tile.minV(), tile.maxU(), tile.maxV(), entry.tintArgb());
        }
        Renderer2D.TEXTURE.render(page.textureView(), page.sampler());
    }

    private void renderEncoded(MapViewport viewport, MapTileDrawBatch batch, MapTileAtlas.PageView page) {
        boolean lit = batch.material() == MapTileMaterial.LIGHT_IN_ALPHA;
        var pipeline = lit
                ? CombatantRenderPipelines.UI_MAP_TILE_LIGHT
                : CombatantRenderPipelines.UI_MAP_TILE_OPAQUE;
        MeshBuilder mesh = lit ? litMesh : opaqueMesh;
        mesh.beginScreen();
        for (MapTileDrawBatch.Entry entry : batch.entries()) {
            ProjectedTile tile = project(viewport, entry);
            appendQuad(mesh, tile, entry.tintArgb());
        }
        UiDirectTexturedRenderer.submit(mesh, pipeline, "u_Texture", page.textureView(), page.sampler());
    }

    private ProjectedTile project(MapViewport viewport, MapTileDrawBatch.Entry entry) {
        MapScreenPoint topLeft = viewport.project(entry.worldBounds().x(), entry.worldBounds().y());
        MapScreenPoint bottomRight = viewport.project(entry.worldBounds().maxX(), entry.worldBounds().maxY());
        MapTileAtlas.UvRect atlasUv = atlas.uv(entry.slot());
        MapTileUvRect localUv = entry.textureUv();
        double uSpan = atlasUv.maxU() - atlasUv.minU();
        double vSpan = atlasUv.maxV() - atlasUv.minV();
        return new ProjectedTile(
                topLeft.x(), topLeft.y(),
                bottomRight.x() - topLeft.x(), bottomRight.y() - topLeft.y(),
                atlasUv.minU() + uSpan * localUv.minU(),
                atlasUv.minV() + vSpan * localUv.minV(),
                atlasUv.minU() + uSpan * localUv.maxU(),
                atlasUv.minV() + vSpan * localUv.maxV()
        );
    }

    private static void appendQuad(MeshBuilder mesh, ProjectedTile tile, int argb) {
        mesh.ensureQuadCapacity();
        int a = argb >>> 24 & 0xFF;
        int r = argb >>> 16 & 0xFF;
        int g = argb >>> 8 & 0xFF;
        int b = argb & 0xFF;
        int i1 = mesh.vec2(tile.x(), tile.y()).raw2(tile.minU(), tile.minV()).color(r, g, b, a).next();
        int i2 = mesh.vec2(tile.x(), tile.y() + tile.height()).raw2(tile.minU(), tile.maxV()).color(r, g, b, a).next();
        int i3 = mesh.vec2(tile.x() + tile.width(), tile.y() + tile.height()).raw2(tile.maxU(), tile.maxV()).color(r, g, b, a).next();
        int i4 = mesh.vec2(tile.x() + tile.width(), tile.y()).raw2(tile.maxU(), tile.minV()).color(r, g, b, a).next();
        mesh.quad(i1, i2, i3, i4);
    }

    public MapTileResidencyCache.Stats residencyStats() {
        return residency.stats();
    }

    public MapTileUploadQueue.Stats uploadStats() {
        return uploads.stats();
    }

    private record BatchKey(int lod, MapTileMaterial material, int page) implements Comparable<BatchKey> {
        @Override
        public int compareTo(BatchKey other) {
            int lodOrder = Integer.compare(other.lod, lod);
            if (lodOrder != 0) return lodOrder;
            int materialOrder = material.compareTo(other.material);
            return materialOrder != 0 ? materialOrder : Integer.compare(page, other.page);
        }
    }

    private record ProjectedTile(double x, double y, double width, double height,
                                 double minU, double minV, double maxU, double maxV) {
    }

    @Override
    public void close() {
        atlas.close();
        litMesh.close();
        opaqueMesh.close();
    }
}
