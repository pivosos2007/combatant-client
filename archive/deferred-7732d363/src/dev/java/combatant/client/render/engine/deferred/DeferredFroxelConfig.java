/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

/** Numerical policy for the view-aligned participating-media froxel grid. */
record DeferredFroxelConfig(
        boolean enabled,
        int tileSizePixels,
        int depthSlices,
        float depthExponent,
        float minimumMaxDistanceBlocks,
        int maxLocalFogVolumes,
        int localFogBinSizeXY,
        int localFogBinSizeZ,
        int maxLocalFogVolumesPerBin,
        int localLightPathSamples
) {
    private static final DeferredFroxelConfig DEFAULT = new DeferredFroxelConfig(
            true, 16, 48, 2.0f, 64.0f,
            128, 4, 4, 32, 6
    );

    DeferredFroxelConfig {
        tileSizePixels = clamp(tileSizePixels, 4, 64);
        depthSlices = clamp(depthSlices, 8, 128);
        depthExponent = clamp(depthExponent, 1.0f, 4.0f);
        minimumMaxDistanceBlocks = clamp(minimumMaxDistanceBlocks, 16.0f, 4096.0f);
        maxLocalFogVolumes = clamp(maxLocalFogVolumes, 1, 1024);
        localFogBinSizeXY = clamp(localFogBinSizeXY, 1, 16);
        localFogBinSizeZ = clamp(localFogBinSizeZ, 1, 16);
        maxLocalFogVolumesPerBin = clamp(maxLocalFogVolumesPerBin, 1, maxLocalFogVolumes);
        localLightPathSamples = clamp(localLightPathSamples, 1, 16);
    }

    static DeferredFroxelConfig current() {
        return DEFAULT;
    }

    Grid grid(int fullWidth, int fullHeight, float farPlane) {
        int width = Math.max(1, (Math.max(1, fullWidth) + tileSizePixels - 1) / tileSizePixels);
        int height = Math.max(1, (Math.max(1, fullHeight) + tileSizePixels - 1) / tileSizePixels);
        float maxDistance = Float.isFinite(farPlane) && farPlane > 0.0f
                ? Math.max(minimumMaxDistanceBlocks, farPlane)
                : minimumMaxDistanceBlocks;
        return new Grid(width, height, depthSlices, maxDistance, depthExponent);
    }

    LocalFogBinGrid localFogBins(Grid froxelGrid) {
        if (froxelGrid == null) throw new IllegalArgumentException("froxelGrid");
        int width = divideRoundUp(froxelGrid.width(), localFogBinSizeXY);
        int height = divideRoundUp(froxelGrid.height(), localFogBinSizeXY);
        int depth = divideRoundUp(froxelGrid.depth(), localFogBinSizeZ);
        return new LocalFogBinGrid(width, height, depth, maxLocalFogVolumesPerBin);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int divideRoundUp(int value, int divisor) {
        return Math.max(1, (Math.max(1, value) + Math.max(1, divisor) - 1) / Math.max(1, divisor));
    }

    record Grid(int width, int height, int depth, float maxDistanceBlocks, float depthExponent) {
        Grid {
            width = Math.max(1, width);
            height = Math.max(1, height);
            depth = Math.max(1, depth);
            if (!Float.isFinite(maxDistanceBlocks) || maxDistanceBlocks <= 0.0f) maxDistanceBlocks = 64.0f;
            if (!Float.isFinite(depthExponent) || depthExponent < 1.0f) depthExponent = 1.0f;
        }

        int froxelCount() {
            return Math.multiplyExact(Math.multiplyExact(width, height), depth);
        }
    }

    record LocalFogBinGrid(int width, int height, int depth, int maxVolumesPerBin) {
        LocalFogBinGrid {
            width = Math.max(1, width);
            height = Math.max(1, height);
            depth = Math.max(1, depth);
            maxVolumesPerBin = Math.max(1, maxVolumesPerBin);
        }

        int binCount() {
            return Math.multiplyExact(Math.multiplyExact(width, height), depth);
        }

        int indexCapacity() {
            return Math.multiplyExact(binCount(), maxVolumesPerBin);
        }
    }
}
