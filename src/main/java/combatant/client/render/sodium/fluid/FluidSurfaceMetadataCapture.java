/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.sodium.fluid;

import combatant.client.render.engine.material.MaterialDomain;
import combatant.client.render.engine.material.MaterialSurfaceDescriptor;
import combatant.client.render.engine.material.MaterialTessellationMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Read-only fluid/material metadata captured at Sodium meshing time.
 *
 * <p>This service never owns, suppresses, uploads or redraws Sodium geometry. It keeps only the
 * producer-known data required by camera-medium queries and future shaderpack material/water
 * bridges. Publication follows Sodium build-result upload so stale worker results cannot become
 * authoritative after a section rebuild or world transition.</p>
 */
public final class FluidSurfaceMetadataCapture {
    private static final ThreadLocal<BuildCapture> BUILD = new ThreadLocal<>();
    private static final Map<Long, SectionFluidMetadata> SECTIONS = new ConcurrentHashMap<>();
    private static final Map<Object, PendingSectionFluidMetadata> PENDING_UPLOADS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final AtomicLong WORLD_EPOCH = new AtomicLong();
    private static volatile Object worldOwner;

    private FluidSurfaceMetadataCapture() { }

    public static void beginWorld(Object owner) {
        Object previous = worldOwner;
        if (previous == owner) return;
        worldOwner = owner;
        if (previous != null) clear();
    }

    public static void beginSection(long sectionKey) {
        BUILD.remove();
        BUILD.set(new BuildCapture(sectionKey, WORLD_EPOCH.get(), new ArrayList<>(), new LinkedHashMap<>()));
    }

    public static void capture(FluidSurfaceData surface) {
        if (surface == null
                || surface.domain() != MaterialDomain.WATER
                || surface.material() == null
                || !surface.isTopSurface()) {
            return;
        }
        BuildCapture capture = BUILD.get();
        if (capture == null || SectionPos.asLong(surface.blockPos()) != capture.sectionKey) return;
        capture.waterSurfaces.add(WaterSurfaceMetadata.from(surface));
    }

    /** Captures only the explicit height-displacement material contract, never replacement geometry. */
    public static void captureHeight(BlockPos worldPos, MaterialSurfaceDescriptor material) {
        if (worldPos == null || material == null
                || material.tessellation().mode() != MaterialTessellationMode.HEIGHT_DISPLACEMENT) {
            return;
        }
        BuildCapture capture = BUILD.get();
        if (capture == null || SectionPos.asLong(worldPos) != capture.sectionKey) return;
        HeightSurfaceMetadata metadata = HeightSurfaceMetadata.from(worldPos, material);
        capture.heightSurfaces.put(new HeightSurfaceKey(metadata.blockPos(), metadata.materialId()), metadata);
    }

    public static void finishSection(Object buildOutput) {
        BuildCapture capture = BUILD.get();
        BUILD.remove();
        if (capture == null || buildOutput == null || capture.worldEpoch != WORLD_EPOCH.get()) return;
        PENDING_UPLOADS.put(buildOutput, new PendingSectionFluidMetadata(
                capture.sectionKey, capture.worldEpoch, List.copyOf(capture.waterSurfaces),
                List.copyOf(capture.heightSurfaces.values())
        ));
    }

    public static void publishUploadedBuildResults(Collection<?> buildOutputs) {
        if (buildOutputs == null || buildOutputs.isEmpty()) return;
        long activeEpoch = WORLD_EPOCH.get();
        synchronized (PENDING_UPLOADS) {
            for (Object buildOutput : buildOutputs) {
                PendingSectionFluidMetadata pending = PENDING_UPLOADS.remove(buildOutput);
                if (pending == null || pending.worldEpoch != activeEpoch) continue;
                long generation = GENERATION.incrementAndGet();
                SECTIONS.put(pending.sectionKey,
                        new SectionFluidMetadata(pending.sectionKey, generation, pending.waterSurfaces, pending.heightSurfaces));
            }
        }
    }

    public static void discardSection(long sectionKey) {
        SECTIONS.remove(sectionKey);
        synchronized (PENDING_UPLOADS) {
            PENDING_UPLOADS.entrySet().removeIf(entry -> entry.getValue().sectionKey == sectionKey);
        }
    }

    public static SectionFluidMetadata section(long sectionKey) {
        return SECTIONS.get(sectionKey);
    }

    public static Map<Long, SectionFluidMetadata> snapshot() {
        return Map.copyOf(SECTIONS);
    }

    public static long generation() {
        return GENERATION.get();
    }

    /** Exact producer-known top-surface sample for a fluid column, when that section is published. */
    public static SurfaceSample sampleTopSurface(BlockPos blockPos, int fluidTypeId, double worldX, double worldZ) {
        if (blockPos == null) return SurfaceSample.UNKNOWN;
        SectionFluidMetadata section = SECTIONS.get(SectionPos.asLong(blockPos));
        if (section == null) return SurfaceSample.UNKNOWN;
        long packedPos = blockPos.asLong();
        for (WaterSurfaceMetadata surface : section.waterSurfaces()) {
            if (surface.blockPos() != packedPos || surface.fluidTypeId() != fluidTypeId) continue;
            float localX = (float) (worldX - blockPos.getX());
            float localZ = (float) (worldZ - blockPos.getZ());
            float y = sampleSurfaceHeight(surface, localX, localZ);
            if (Float.isFinite(y)) return new SurfaceSample(y, true);
        }
        return SurfaceSample.UNKNOWN;
    }

    private static float sampleSurfaceHeight(WaterSurfaceMetadata surface, float localX, float localZ) {
        float[] local = surface.localSurfaceCoordinates();
        float[] heights = surface.cornerHeights();
        float h00 = nearestCornerHeight(local, heights, 0.0f, 0.0f);
        float h10 = nearestCornerHeight(local, heights, 1.0f, 0.0f);
        float h11 = nearestCornerHeight(local, heights, 1.0f, 1.0f);
        float h01 = nearestCornerHeight(local, heights, 0.0f, 1.0f);
        float u = Math.max(0.0f, Math.min(1.0f, localX));
        float v = Math.max(0.0f, Math.min(1.0f, localZ));
        float a = h00 + (h10 - h00) * u;
        float b = h01 + (h11 - h01) * u;
        return BlockPos.of(surface.blockPos()).getY() + a + (b - a) * v;
    }

    private static float nearestCornerHeight(float[] local, float[] heights, float targetX, float targetZ) {
        int best = 0;
        float bestDistance = Float.POSITIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            float dx = local[i * 2] - targetX;
            float dz = local[i * 2 + 1] - targetZ;
            float distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return heights[best];
    }

    public static void clear() {
        BUILD.remove();
        SECTIONS.clear();
        PENDING_UPLOADS.clear();
        WORLD_EPOCH.incrementAndGet();
        GENERATION.incrementAndGet();
    }

    public record SurfaceSample(float worldY, boolean valid) {
        public static final SurfaceSample UNKNOWN = new SurfaceSample(0.0f, false);
    }

    /** Producer-known fluid/material data; intentionally contains no render mesh, UV or lighting payload. */
    public record WaterSurfaceMetadata(
            long blockPos,
            int materialId,
            int fluidTypeId,
            float flowX,
            float flowZ,
            float flowStrength,
            int surfaceFlags,
            int fluidConnectivity,
            float surfaceNormalX,
            float surfaceNormalY,
            float surfaceNormalZ,
            float[] localSurfaceCoordinates,
            float[] cornerHeights,
            int mapMask,
            int featureMask,
            int packedSurface,
            float transmission,
            float fallbackThickness
    ) {
        public WaterSurfaceMetadata {
            localSurfaceCoordinates = localSurfaceCoordinates == null
                    ? new float[8] : localSurfaceCoordinates.clone();
            cornerHeights = cornerHeights == null ? new float[4] : cornerHeights.clone();
        }

        static WaterSurfaceMetadata from(FluidSurfaceData surface) {
            float[] local = new float[8];
            float[] heights = new float[4];
            for (int i = 0; i < 4; i++) {
                local[i * 2] = surface.x()[i];
                local[i * 2 + 1] = surface.z()[i];
                heights[i] = surface.y()[i];
            }
            float[] normal = surface.surfaceNormal();
            return new WaterSurfaceMetadata(
                    surface.blockPos().asLong(), surface.materialId(), surface.fluidTypeId(),
                    surface.flowX(), surface.flowZ(), surface.flowStrength(), surface.surfaceFlags(),
                    surface.fluidConnectivity(), normal[0], normal[1], normal[2], local, heights,
                    surface.mapMask(), surface.featureMask(), surface.packedSurface(),
                    surface.material().transmission(), surface.material().thickness()
            );
        }
    }

    /** Height-displacement intent retained as material metadata only; no vertices or patch mesh are stored. */
    public record HeightSurfaceMetadata(
            long blockPos,
            int materialId,
            int mapMask,
            int featureMask,
            int packedSurface,
            float displacementScale,
            float minTessFactor,
            float maxTessFactor,
            float distanceFadeStart,
            float distanceFadeEnd
    ) {
        static HeightSurfaceMetadata from(BlockPos worldPos, MaterialSurfaceDescriptor material) {
            var tessellation = material.tessellation();
            return new HeightSurfaceMetadata(
                    worldPos.asLong(), material.stableId(),
                    combatant.client.render.engine.material.MaterialRegistry.global().gpuPresenceMask(material),
                    material.gpuFeatureMask16(), material.packScalarSurface(),
                    tessellation.displacementScale(), tessellation.minFactor(), tessellation.maxFactor(),
                    tessellation.distanceFadeStart(), tessellation.distanceFadeEnd()
            );
        }
    }

    public record SectionFluidMetadata(long sectionKey,
                                       long generation,
                                       List<WaterSurfaceMetadata> waterSurfaces,
                                       List<HeightSurfaceMetadata> heightSurfaces) { }

    private record HeightSurfaceKey(long blockPos, int materialId) { }

    private record BuildCapture(long sectionKey,
                                long worldEpoch,
                                ArrayList<WaterSurfaceMetadata> waterSurfaces,
                                LinkedHashMap<HeightSurfaceKey, HeightSurfaceMetadata> heightSurfaces) { }

    private record PendingSectionFluidMetadata(long sectionKey,
                                               long worldEpoch,
                                               List<WaterSurfaceMetadata> waterSurfaces,
                                               List<HeightSurfaceMetadata> heightSurfaces) { }
}
