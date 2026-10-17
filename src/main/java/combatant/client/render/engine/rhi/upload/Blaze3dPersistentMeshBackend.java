/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.upload;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import combatant.client.render.engine.profiler.RenderCostProfiler;
import combatant.client.render.engine.rhi.GpuMeshHandle;
import combatant.client.render.engine.rhi.MeshOwnership;
import combatant.client.render.engine.rhi.RhiStats;
import com.mojang.renderpearl.api.pipeline.IndexType;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Persistent mesh storage backed by Mojang GPU buffers.
 *
 * <p>This class deliberately contains no OpenGL or Vulkan calls. Mojang's active {@code Device}
 * creates the native buffer for the selected backend, so Combatant gets one residency contract for
 * GL and Vulkan instead of two asset upload implementations.</p>
 */
public final class Blaze3dPersistentMeshBackend implements PersistentMeshBackend {
    private final RhiStats stats;
    private final Set<PersistentGpuMesh> live = Collections.newSetFromMap(new IdentityHashMap<>());

    public Blaze3dPersistentMeshBackend(RhiStats stats) {
        this.stats = stats;
    }

    @Override
    public PersistentGpuMesh upload(String label, ByteBuffer vertices, int vertexStride,
                                    ByteBuffer indices, int indexCount, IndexType indexType) {
        String owner = label == null || label.isBlank() ? "Combatant Persistent Mesh" : label;
        try (RenderCostProfiler.Scope ignored = RenderCostProfiler.rhiDraw("persistent_mesh_upload")) {
            if (vertices == null || indices == null || indexType == null) {
                throw new IllegalArgumentException(owner + ": persistent mesh payload is incomplete");
            }
            int vertexBytes = vertices.remaining();
            int indexBytes = indices.remaining();
            if (vertexBytes <= 0 || indexBytes <= 0 || vertexStride <= 0 || indexCount <= 0) {
                throw new IllegalArgumentException(owner + ": invalid mesh payload: vertexBytes=" + vertexBytes
                        + ", indexBytes=" + indexBytes + ", vertexStride=" + vertexStride + ", indexCount=" + indexCount);
            }
            if (vertexBytes % vertexStride != 0) {
                throw new IllegalStateException(owner + ": vertex payload is not stride-aligned");
            }
            long expectedIndexBytes = (long) indexCount * indexType.bytes;
            if (expectedIndexBytes != indexBytes) {
                throw new IllegalArgumentException(owner + ": index payload mismatch: expected=" + expectedIndexBytes
                        + ", actual=" + indexBytes + ", type=" + indexType);
            }

            GpuBuffer vertexBuffer = null;
            GpuBuffer indexBuffer = null;
            try {
                vertexBuffer = RenderSystem.getDevice().createBuffer(
                        () -> owner + " VBO", GpuBuffer.USAGE_VERTEX, vertices.duplicate()
                );
                indexBuffer = RenderSystem.getDevice().createBuffer(
                        () -> owner + " IBO", GpuBuffer.USAGE_INDEX, indices.duplicate()
                );
                GpuMeshHandle draw = new GpuMeshHandle(
                        vertexBuffer,
                        indexBuffer,
                        0L,
                        vertexBytes,
                        vertexStride,
                        0,
                        0,
                        indexCount,
                        indexBytes,
                        indexType,
                        MeshOwnership.PERSISTENT
                );
                draw.validateForDraw(owner + " persistent upload");
                PersistentGpuMesh allocation = new PersistentGpuMesh(vertexBuffer, indexBuffer, draw, live::remove);
                live.add(allocation);
                stats.meshUpload(vertexBytes, indexBytes);
                return allocation;
            } catch (RuntimeException | Error failure) {
                if (vertexBuffer != null && !vertexBuffer.isClosed()) vertexBuffer.close();
                if (indexBuffer != null && !indexBuffer.isClosed()) indexBuffer.close();
                throw failure;
            }
        }
    }

    /** Releases one allocation and removes it from backend shutdown ownership. */
    public void release(PersistentGpuMesh mesh) {
        if (mesh == null) return;
        live.remove(mesh);
        mesh.close();
    }

    @Override
    public void close() {
        for (PersistentGpuMesh mesh : live.toArray(PersistentGpuMesh[]::new)) {
            mesh.close();
        }
        live.clear();
    }
}
