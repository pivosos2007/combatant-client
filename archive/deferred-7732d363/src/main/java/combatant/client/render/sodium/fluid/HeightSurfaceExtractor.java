/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.sodium.fluid;

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
 * <p>Terrain is extracted only when an explicit material descriptor requests HEIGHT_DISPLACEMENT.
 * A height map by itself never
 * changes Sodium's shared triangle topology.</p>
 */
public final class HeightSurfaceExtractor {
    private static final ThreadLocal<BuildCapture> BUILD = new ThreadLocal<>();
    private static final Map<Long, SectionPatchMesh> SECTIONS = new ConcurrentHashMap<>();
    private static final Map<Object, PendingSectionPatchMesh> PENDING_UPLOADS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final AtomicLong WORLD_EPOCH = new AtomicLong();
    private static volatile Object worldOwner;

    private HeightSurfaceExtractor() {
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
                new ArrayList<>()
        ));
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
                capture.sectionKey, capture.worldEpoch, List.copyOf(capture.heightPatches)
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
                        pending.sectionKey, generation, pending.heightPatches
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

    public static boolean hasHeightPatches() {
        for (SectionPatchMesh section : SECTIONS.values()) {
            if (!section.heightPatches().isEmpty()) return true;
        }
        return false;
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
                                ArrayList<HeightPatch> heightPatches) {
    }

    private record PendingSectionPatchMesh(long sectionKey,
                                           long worldEpoch,
                                           List<HeightPatch> heightPatches) {
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
                                   List<HeightPatch> heightPatches) {
        public boolean empty() {
            return heightPatches.isEmpty();
        }
    }
}
