/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.upload;

import com.mojang.blaze3d.systems.RenderSystem;
import combatant.client.render.engine.profiler.RenderCostProfiler;
import combatant.client.render.engine.rhi.GpuMeshHandle;
import combatant.client.render.engine.rhi.MeshOwnership;
import combatant.client.render.engine.rhi.RhiStats;
import combatant.client.render.engine.uniform.MeshBuilder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Production dynamic mesh upload path built on Blaze3D GPU buffers.
 * <p>
 * This backend does not use immediate uploads as the normal implementation. It owns GPU vertex/index arenas,
 * suballocates transient meshes inside those arenas, adjusts INT indices by arena-relative base vertex, and retires
 * used arenas behind one shared frame fence at {@link #framePresented()}.
 * <p>
 * Important properties:
 * - no per-mesh GpuBuffer allocation in the normal path;
 * - no MappableRingBuffer#getBlocking() as the normal path;
 * - no RenderSystem.flipFrame cleanup fan-out;
 * - spill arenas are GPU buffers too, not immediate uploads;
 * - immediate upload is disabled by default and exists only as an explicit emergency switch.
 */
public final class Blaze3dDynamicMeshBackend implements DynamicMeshBackend {
    private static final int DEFAULT_VERTEX_ARENA_BYTES = Integer.getInteger("combatant.rhi.vertexArenaBytes", 32 * 1024 * 1024);
    private static final int DEFAULT_INDEX_ARENA_BYTES = Integer.getInteger("combatant.rhi.indexArenaBytes", 8 * 1024 * 1024);
    private static final int DEFAULT_PERSISTENT_ARENAS = Integer.getInteger("combatant.rhi.meshArenas", 3);
    private static final boolean ALLOW_IMMEDIATE_FALLBACK = Boolean.getBoolean("combatant.rhi.allowImmediateFallback");

    private final RhiStats stats;
    private final List<Blaze3dMeshArena> persistentArenas = new ArrayList<>();
    private final List<Blaze3dMeshArena> retiredArenas = new ArrayList<>();
    private final List<Blaze3dMeshArena> activeArenas = new ArrayList<>();

    private Blaze3dMeshArena currentArena;
    private long frameId;
    private int spillArenaSequence;
    private boolean persistentArenasCreated;

    public Blaze3dDynamicMeshBackend(RhiStats stats) {
        this.stats = stats;
    }

    private static int align(int value, int alignment) {
        return (value + alignment - 1) & -alignment;
    }

    @Override
    public GpuMeshHandle upload(MeshBuilder mesh) {
        try (RenderCostProfiler.Scope ignored = RenderCostProfiler.rhiDraw("dynamic_mesh_upload")) {
            if (mesh == null || mesh.getIndicesCount() <= 0) {
                throw new IllegalArgumentException("Cannot upload empty mesh");
            }
            if (mesh.isBuilding()) {
                mesh.end();
            }

            mesh.validateComplete("dynamic mesh upload");
            int vertexBytes = mesh.getVertexBytes();
            int indexBytes = mesh.getIndexBytes();
            int vertexStride = mesh.getVertexStride();
            if (vertexBytes <= 0 || indexBytes <= 0 || vertexStride <= 0) {
                throw new IllegalArgumentException("Cannot upload mesh with invalid vertex/index payload: vertexBytes="
                        + vertexBytes + ", indexBytes=" + indexBytes + ", vertexStride=" + vertexStride);
            }
            if (vertexBytes % vertexStride != 0) {
                throw new IllegalStateException("Cannot upload mesh whose vertex payload is not stride-aligned: vertexBytes="
                        + vertexBytes + ", vertexStride=" + vertexStride);
            }

            try {
                ensurePersistentArenas();
                Blaze3dMeshArena arena = selectArena(vertexBytes, indexBytes, vertexStride);
                Blaze3dMeshAllocation allocation = arena.allocate(vertexBytes, indexBytes, vertexStride);
                allocation.write(mesh);
                stats.meshUpload(vertexBytes, indexBytes);
                stats.dynamicArenaAllocation(vertexBytes, indexBytes, arena.persistent());
                GpuMeshHandle handle = allocation.toHandle(mesh);
                handle.validateForDraw("dynamic mesh upload");
                return handle;
            } catch (RuntimeException ex) {
                if (!ALLOW_IMMEDIATE_FALLBACK) throw ex;
                return emergencyImmediateFallback(mesh, vertexBytes, indexBytes, ex);
            }
        }
    }

    private void ensurePersistentArenas() {
        if (persistentArenasCreated) return;
        int arenaCount = Math.max(2, DEFAULT_PERSISTENT_ARENAS);
        for (int i = 0; i < arenaCount; i++) {
            persistentArenas.add(new Blaze3dMeshArena(
                    "Combatant RHI Dynamic Mesh Arena #" + i,
                    DEFAULT_VERTEX_ARENA_BYTES,
                    DEFAULT_INDEX_ARENA_BYTES,
                    true
            ));
        }
        persistentArenasCreated = true;
        stats.dynamicArenaCreated(arenaCount, DEFAULT_VERTEX_ARENA_BYTES, DEFAULT_INDEX_ARENA_BYTES, false);
    }

    private Blaze3dMeshArena selectArena(int vertexBytes, int indexBytes, int vertexStride) {
        if (currentArena != null && currentArena.canAllocate(vertexBytes, indexBytes, vertexStride)) {
            return currentArena;
        }

        Blaze3dMeshArena available = findAvailablePersistentArena(vertexBytes, indexBytes, vertexStride);
        if (available != null) {
            currentArena = available;
            markActive(available);
            stats.dynamicArenaReuse();
            return available;
        }

        stats.dynamicArenaBacklog();
        currentArena = createSpillArena(vertexBytes, indexBytes);
        markActive(currentArena);
        return currentArena;
    }

    private Blaze3dMeshArena findAvailablePersistentArena(int vertexBytes, int indexBytes, int vertexStride) {
        reclaimRetired(false);
        for (Blaze3dMeshArena arena : persistentArenas) {
            if (activeArenas.contains(arena)) continue;
            stats.dynamicFenceCheck();
            if (!arena.canStartFrame()) continue;
            if (!arena.canAllocate(vertexBytes, indexBytes, vertexStride)) continue;
            return arena;
        }
        return null;
    }

    private Blaze3dMeshArena createSpillArena(int vertexBytes, int indexBytes) {
        int vertexCapacity = Math.max(DEFAULT_VERTEX_ARENA_BYTES, align(vertexBytes, 1024 * 1024));
        int indexCapacity = Math.max(DEFAULT_INDEX_ARENA_BYTES, align(indexBytes, 256 * 1024));
        Blaze3dMeshArena spill = new Blaze3dMeshArena(
                "Combatant RHI Dynamic Mesh Spill #" + spillArenaSequence++,
                vertexCapacity,
                indexCapacity,
                false
        );
        stats.dynamicArenaCreated(1, vertexCapacity, indexCapacity, true);
        return spill;
    }

    private void markActive(Blaze3dMeshArena arena) {
        if (!activeArenas.contains(arena)) {
            activeArenas.add(arena);
        }
    }

    private GpuMeshHandle emergencyImmediateFallback(MeshBuilder mesh, int vertexBytes, int indexBytes, RuntimeException cause) {
        stats.immediateFallbackUpload(vertexBytes, indexBytes);
        stats.temporaryOwnedMesh();
        return new GpuMeshHandle(
                mesh.getVertexBuffer(),
                mesh.getIndexBuffer(),
                0L,
                0,
                mesh.getIndicesCount(),
                mesh.getIndexType(),
                MeshOwnership.TEMPORARY_OWNED
        );
    }

    @Override
    public void beginFrame(long frameId) {
        // If a caller starts a new frame without passing through flipFrame tail, keep arena ownership safe.
        // Normal flow retires active arenas in framePresented().
        if (!activeArenas.isEmpty()) {
            framePresented();
        }
        this.frameId = frameId;
        this.currentArena = null;
        this.activeArenas.clear();
        reclaimRetired(false);
    }

    @Override
    public void endSubmission() {
        // Direct mapped writes are closed per allocation. No batch flush is required here.
        // This hook remains the explicit boundary for a future command-buffered GL path.
    }

    @Override
    public void framePresented() {
        if (!activeArenas.isEmpty()) {
            Blaze3dFrameFence frameFence = new Blaze3dFrameFence(RenderSystem.getDevice().createCommandEncoder().createFence());
            for (Blaze3dMeshArena arena : activeArenas) {
                arena.retire(frameFence.retain());
                retiredArenas.add(arena);
                stats.dynamicArenaRetired();
            }
            frameFence.release();
            activeArenas.clear();
            currentArena = null;
        }
        reclaimRetired(true);
    }

    private void reclaimRetired(boolean countCompleted) {
        Iterator<Blaze3dMeshArena> it = retiredArenas.iterator();
        while (it.hasNext()) {
            Blaze3dMeshArena arena = it.next();
            stats.dynamicFenceCheck();
            boolean complete = arena.reclaimIfComplete();
            if (!complete) continue;
            it.remove();
            if (countCompleted) stats.dynamicFenceCompleted();
            if (!arena.persistent()) {
                arena.close();
            }
        }
    }

    public long frameId() {
        return frameId;
    }

    @Override
    public void close() {
        for (Blaze3dMeshArena arena : activeArenas) {
            arena.close();
        }
        activeArenas.clear();
        for (Blaze3dMeshArena arena : retiredArenas) {
            arena.close();
        }
        retiredArenas.clear();
        for (Blaze3dMeshArena arena : persistentArenas) {
            arena.close();
        }
        persistentArenas.clear();
        currentArena = null;
        persistentArenasCreated = false;
    }
}
