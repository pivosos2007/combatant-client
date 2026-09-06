/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

/** Immutable terrain input supplied by a data-backend adapter. Pixels may be absent while loading. */
public record MapTerrainTile(MapTileResidencyKey key,
                             long revision,
                             MapRect worldBounds,
                             MapTilePixels pixels,
                             MapTileGpuCopy gpuCopy,
                             MapTileMaterial material,
                             MapTileUvRect textureUv,
                             int tintArgb) {
    public MapTerrainTile {
        if (key == null || worldBounds == null) throw new NullPointerException();
        if (pixels != null && gpuCopy != null) {
            throw new IllegalArgumentException("A terrain tile can have only one upload payload.");
        }
        material = material == null ? MapTileMaterial.RGBA : material;
        textureUv = textureUv == null ? MapTileUvRect.FULL : textureUv;
    }

    public MapTerrainTile(MapTileResidencyKey key,
                          long revision,
                          MapRect worldBounds,
                          MapTilePixels pixels,
                          MapTileGpuCopy gpuCopy,
                          int tintArgb) {
        this(key, revision, worldBounds, pixels, gpuCopy,
                MapTileMaterial.RGBA, MapTileUvRect.FULL, tintArgb);
    }

    public MapTerrainTile(MapTileResidencyKey key,
                          long revision,
                          MapRect worldBounds,
                          MapTilePixels pixels,
                          int tintArgb) {
        this(key, revision, worldBounds, pixels, null,
                MapTileMaterial.RGBA, MapTileUvRect.FULL, tintArgb);
    }

    public static MapTerrainTile loading(MapTileResidencyKey key, long revision, MapRect worldBounds) {
        return new MapTerrainTile(key, revision, worldBounds, null, null,
                MapTileMaterial.RGBA, MapTileUvRect.FULL, 0xFFFFFFFF);
    }

    public static MapTerrainTile gpuCopy(MapTileResidencyKey key,
                                         long revision,
                                         MapRect worldBounds,
                                         MapTileGpuCopy copy,
                                         int tintArgb) {
        return gpuCopy(key, revision, worldBounds, copy,
                MapTileMaterial.RGBA, MapTileUvRect.FULL, tintArgb);
    }

    public static MapTerrainTile gpuCopy(MapTileResidencyKey key,
                                         long revision,
                                         MapRect worldBounds,
                                         MapTileGpuCopy copy,
                                         MapTileMaterial material,
                                         MapTileUvRect textureUv,
                                         int tintArgb) {
        return new MapTerrainTile(key, revision, worldBounds, null, copy,
                material, textureUv, tintArgb);
    }
}
