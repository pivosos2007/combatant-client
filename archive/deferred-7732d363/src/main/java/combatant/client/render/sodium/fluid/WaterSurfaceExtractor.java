/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.sodium.fluid;

import combatant.client.render.engine.material.MaterialDomain;
import combatant.client.render.engine.material.MaterialSurfaceDescriptor;
import combatant.client.render.engine.material.MaterialTessellationMode;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sodium-meshing-time extraction of patch-topology surfaces.
 *
 * <p>Water is extracted only from explicit WATER producers. Ordinary terrain is extracted only
 * when an explicit material descriptor requests HEIGHT_DISPLACEMENT. A height map by itself never
 * changes Sodium's shared triangle topology.</p>
 */
public final class WaterSurfaceExtractor {
    private static final ThreadLocal<BuildCapture> BUILD = new ThreadLocal<>();
    private static final Map<Long, SectionPatchMesh> SECTIONS = new ConcurrentHashMap<>();
    private static final Map<Object, PendingSectionPatchMesh> PENDING_UPLOADS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final AtomicLong WORLD_EPOCH = new AtomicLong();
    private static volatile Object worldOwner;

    private WaterSurfaceExtractor() {
    }

    /**
     * Publishes the authoritative world owner at the renderer world boundary. In-flight builds from
     * the previous world are epoch-rejected before staged build output is published.
     */
    public static void beginWorld(Object owner) {
        Object previous = worldOwner;
        if (previous == owner) return;
        worldOwner = owner;
        if (previous != null) clear();
    }

    public static void beginSection(long sectionKey) {
        BUILD.remove();
        BUILD.set(new BuildCapture(
                sectionKey,
                WORLD_EPOCH.get(),
                SurfacePatchRouting.waterReplacementActive(),
                new ArrayList<>(),
                new ArrayList<>()
        ));
    }

    /** Stable routing snapshot for the section currently being meshed on this worker thread. */
    public static boolean currentBuildOwnsWaterReplacement() {
        BuildCapture capture = BUILD.get();
        return capture != null && capture.waterReplacementOwned;
    }

    public static boolean replacementEligible(FluidSurfaceData surface) {
        return surface != null
                && surface.domain() == MaterialDomain.WATER
                && surface.material() != null;
    }

    public static void capture(FluidSurfaceData surface) {
        if (!replacementEligible(surface) || surface.reversed()) return;
        BuildCapture capture = BUILD.get();
        if (capture == null) return;
        if (SectionPos.asLong(surface.blockPos()) != capture.sectionKey) return;
        // One producer-facing quad is enough: the replacement raster path is explicitly two-sided.
        // Capturing top, side and bottom boundaries ensures no vanilla water geometry survives.
        capture.waterPatches.add(WaterPatch.from(surface));
    }

    /** Extracts a normal block-model quad only when topology was explicitly requested by its descriptor. */
    public static boolean captureHeight(BlockPos worldPos,
                                        BlockPos renderOrigin,
                                        MutableQuadViewImpl quad,
                                        MaterialSurfaceDescriptor material,
                                        net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder.Vertex[] vertices) {
        if (worldPos == null || renderOrigin == null || quad == null || material == null) return false;
        if (material.tessellation().mode() != MaterialTessellationMode.HEIGHT_DISPLACEMENT) return false;
        BuildCapture capture = BUILD.get();
        if (capture == null || SectionPos.asLong(worldPos) != capture.sectionKey) return false;

        float[] positions = new float[12];
        float[] uvs = new float[8];
        int[] color = new int[4];
        float[] ao = new float[4];
        int[] light = new int[4];
        for (int i = 0; i < 4; i++) {
            positions[i * 3] = worldPos.getX() + quad.getX(i);
            positions[i * 3 + 1] = worldPos.getY() + quad.getY(i);
            positions[i * 3 + 2] = worldPos.getZ() + quad.getZ(i);
            uvs[i * 2] = quad.getTexU(i);
            uvs[i * 2 + 1] = quad.getTexV(i);
            if (vertices != null && i < vertices.length && vertices[i] != null) {
                color[i] = vertices[i].color;
                ao[i] = vertices[i].ao;
                light[i] = vertices[i].light;
            } else {
                color[i] = 0xFFFFFFFF;
                ao[i] = 1.0f;
                light[i] = 0x00F000F0;
            }
        }
        var tess = material.tessellation();
        capture.heightPatches.add(new HeightPatch(
                worldPos.asLong(), material.stableId(), positions, uvs, color, ao, light,
                combatant.client.render.engine.material.MaterialRegistry.global().gpuPresenceMask(material),
                material.gpuFeatureMask16(), material.packScalarSurface(),
                tess.displacementScale(), tess.minFactor(), tess.maxFactor(),
                tess.distanceFadeStart(), tess.distanceFadeEnd()
        ));
        return true;
    }

    /**
     * Stages extracted geometry against the exact Sodium build result which produced it. The live
     * section map is intentionally not changed here: meshing can finish well before Sodium swaps
     * that result into its render-region storage.
     */
    public static void finishSection(Object buildOutput) {
        BuildCapture capture = BUILD.get();
        BUILD.remove();
        if (capture == null || buildOutput == null || capture.worldEpoch != WORLD_EPOCH.get()) return;
        PENDING_UPLOADS.put(buildOutput, new PendingSectionPatchMesh(
                capture.sectionKey, capture.worldEpoch, capture.waterReplacementOwned,
                List.copyOf(capture.waterPatches), List.copyOf(capture.heightPatches)
        ));
    }

    /**
     * Publishes only build results that Sodium has finished installing into render-region storage.
     * Exact build-output staging keeps publication tied to the result Sodium actually installed.
     */
    public static void publishUploadedBuildResults(Collection<?> buildOutputs) {
        if (buildOutputs == null || buildOutputs.isEmpty()) return;
        long activeEpoch = WORLD_EPOCH.get();
        synchronized (PENDING_UPLOADS) {
            for (Object buildOutput : buildOutputs) {
                PendingSectionPatchMesh pending = PENDING_UPLOADS.remove(buildOutput);
                if (pending == null || pending.worldEpoch != activeEpoch) continue;
                long generation = GENERATION.incrementAndGet();
                SECTIONS.put(pending.sectionKey, new SectionPatchMesh(
                        pending.sectionKey, generation, pending.waterReplacementOwned,
                        pending.waterPatches, pending.heightPatches
                ));
            }
        }
    }

    public static void discardSection(long sectionKey) {
        SECTIONS.remove(sectionKey);
        synchronized (PENDING_UPLOADS) {
            PENDING_UPLOADS.entrySet().removeIf(entry -> entry.getValue().sectionKey == sectionKey);
        }
    }

    public static SectionPatchMesh section(long sectionKey) {
        return SECTIONS.get(sectionKey);
    }

    public static Map<Long, SectionPatchMesh> snapshot() {
        return Map.copyOf(SECTIONS);
    }

    public static long generation() {
        return GENERATION.get();
    }

    public static boolean hasWaterPatches() {
        for (SectionPatchMesh section : SECTIONS.values()) {
            if (!section.waterPatches().isEmpty()) return true;
        }
        return false;
    }

    public static boolean hasReplacementOwnedWaterPatches() {
        for (SectionPatchMesh section : SECTIONS.values()) {
            if (section.waterReplacementOwned() && !section.waterPatches().isEmpty()) return true;
        }
        return false;
    }

    public static ExtractionStats stats() {
        int sectionsWithWater = 0;
        int replacementOwnedSections = 0;
        long waterPatches = 0L;
        long replacementOwnedPatches = 0L;
        for (SectionPatchMesh section : SECTIONS.values()) {
            int count = section.waterPatches().size();
            if (count == 0) continue;
            sectionsWithWater++;
            waterPatches += count;
            if (section.waterReplacementOwned()) {
                replacementOwnedSections++;
                replacementOwnedPatches += count;
            }
        }
        return new ExtractionStats(
                sectionsWithWater, replacementOwnedSections, waterPatches, replacementOwnedPatches, GENERATION.get()
        );
    }

    public static boolean hasHeightPatches() {
        for (SectionPatchMesh section : SECTIONS.values()) {
            if (!section.heightPatches().isEmpty()) return true;
        }
        return false;
    }


    /** Exact base extracted top-surface sample for a producer-known fluid column, when available. */
    public static SurfaceSample sampleTopSurface(BlockPos blockPos, int fluidTypeId, double worldX, double worldZ) {
        if (blockPos == null) return SurfaceSample.UNKNOWN;
        SectionPatchMesh section = SECTIONS.get(SectionPos.asLong(blockPos));
        if (section == null) return SurfaceSample.UNKNOWN;
        long packedPos = blockPos.asLong();
        for (WaterPatch patch : section.waterPatches()) {
            if ((patch.surfaceFlags() & WaterSurfaceContract.FLAG_TOP_SURFACE) == 0) continue;
            if (patch.blockPos() != packedPos || patch.fluidTypeId() != fluidTypeId) continue;
            float localX = (float) (worldX - blockPos.getX());
            float localZ = (float) (worldZ - blockPos.getZ());
            float y = samplePatchHeight(patch, localX, localZ);
            if (Float.isFinite(y)) return new SurfaceSample(y, true);
        }
        return SurfaceSample.UNKNOWN;
    }

    private static float samplePatchHeight(WaterPatch patch, float localX, float localZ) {
        float[] local = patch.localSurfaceCoordinates();
        float[] positions = patch.positions();
        float h00 = nearestCornerHeight(local, positions, 0.0f, 0.0f);
        float h10 = nearestCornerHeight(local, positions, 1.0f, 0.0f);
        float h11 = nearestCornerHeight(local, positions, 1.0f, 1.0f);
        float h01 = nearestCornerHeight(local, positions, 0.0f, 1.0f);
        float u = Math.max(0.0f, Math.min(1.0f, localX));
        float v = Math.max(0.0f, Math.min(1.0f, localZ));
        float a = h00 + (h10 - h00) * u;
        float b = h01 + (h11 - h01) * u;
        return BlockPos.of(patch.blockPos()).getY() + a + (b - a) * v;
    }

    private static float nearestCornerHeight(float[] local, float[] positions, float targetX, float targetZ) {
        int best = 0;
        float bestDistance = Float.POSITIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            float dx = local[i * 2] - targetX;
            float dz = local[i * 2 + 1] - targetZ;
            float d = dx * dx + dz * dz;
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return positions[best * 3 + 1];
    }

    public record SurfaceSample(float worldY, boolean valid) {
        public static final SurfaceSample UNKNOWN = new SurfaceSample(0.0f, false);
    }

    public static void clear() {
        BUILD.remove();
        SECTIONS.clear();
        PENDING_UPLOADS.clear();
        WORLD_EPOCH.incrementAndGet();
        GENERATION.incrementAndGet();
    }

    private record BuildCapture(long sectionKey,
                                long worldEpoch,
                                boolean waterReplacementOwned,
                                ArrayList<WaterPatch> waterPatches,
                                ArrayList<HeightPatch> heightPatches) {
    }

    private record PendingSectionPatchMesh(long sectionKey,
                                           long worldEpoch,
                                           boolean waterReplacementOwned,
                                           List<WaterPatch> waterPatches,
                                           List<HeightPatch> heightPatches) {
    }

    public record ExtractionStats(int sectionsWithWater,
                                  int replacementOwnedSections,
                                  long waterPatches,
                                  long replacementOwnedPatches,
                                  long generation) {
    }

    /** Patch-compatible water geometry: four block-local control points plus exact producer UVs. */
    public record WaterPatch(
            long blockPos,
            int materialId,
            int fluidTypeId,
            float flowX,
            float flowZ,
            float flowStrength,
            int surfaceFlags,
            int fluidConnectivity,
            float cellBaseY,
            float surfaceNormalX,
            float surfaceNormalY,
            float surfaceNormalZ,
            float[] positions,
            float[] localSurfaceCoordinates,
            float[] uvs,
            int[] color,
            float[] ao,
            int[] light,
            int mapMask,
            int featureMask,
            int packedSurface,
            float transmission,
            float fallbackThickness,
            float displacementScale,
            float minTessFactor,
            float maxTessFactor,
            float distanceFadeStart,
            float distanceFadeEnd
    ) {
        static WaterPatch from(FluidSurfaceData surface) {
            float[] positions = new float[12];
            float[] localSurfaceCoordinates = new float[8];
            float[] uvs = new float[8];
            for (int i = 0; i < 4; i++) {
                // Keep block-local coordinates until camera-relative upload. Converting large block
                // coordinates to float here would destroy sub-block precision far from world origin.
                positions[i * 3] = surface.x()[i];
                positions[i * 3 + 1] = surface.y()[i];
                positions[i * 3 + 2] = surface.z()[i];
                localSurfaceCoordinates[i * 2] = surface.x()[i];
                localSurfaceCoordinates[i * 2 + 1] = surface.z()[i];
                uvs[i * 2] = surface.u()[i];
                uvs[i * 2 + 1] = surface.v()[i];
            }
            var tess = surface.material().tessellation();
            float[] normal = surface.surfaceNormal();
            return new WaterPatch(
                    surface.blockPos().asLong(), surface.materialId(), surface.fluidTypeId(),
                    surface.flowX(), surface.flowZ(), surface.flowStrength(), surface.surfaceFlags(),
                    surface.fluidConnectivity(), surface.blockPos().getY(), normal[0], normal[1], normal[2],
                    positions, localSurfaceCoordinates, uvs, surface.color(), surface.ao(), surface.light(),
                    surface.mapMask(), surface.featureMask(), surface.packedSurface(),
                    surface.material().transmission(), surface.material().thickness(),
                    tess.displacementScale(), tess.minFactor(), tess.maxFactor(),
                    tess.distanceFadeStart(), tess.distanceFadeEnd()
            );
        }
    }

    /** Explicit height-displacement patch. Presence of a height texture alone cannot create this record. */
    public record HeightPatch(
            long blockPos,
            int materialId,
            float[] positions,
            float[] uvs,
            int[] color,
            float[] ao,
            int[] light,
            int mapMask,
            int featureMask,
            int packedSurface,
            float displacementScale,
            float minTessFactor,
            float maxTessFactor,
            float distanceFadeStart,
            float distanceFadeEnd
    ) {
    }

    public record SectionPatchMesh(long sectionKey,
                                   long generation,
                                   boolean waterReplacementOwned,
                                   List<WaterPatch> waterPatches,
                                   List<HeightPatch> heightPatches) {
        public boolean empty() {
            return waterPatches.isEmpty() && heightPatches.isEmpty();
        }
    }
}
