/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import org.jetbrains.annotations.Nullable;

/**
 * GPU mesh range prepared by CombatantRHI.
 */
public record GpuMeshHandle(GpuBuffer vertexBuffer,
                            GpuBuffer indexBuffer,
                            long vertexOffsetBytes,
                            int vertexBytes,
                            int vertexStride,
                            int baseVertex,
                            int firstIndex,
                            int indexCount,
                            int indexBytes,
                            IndexType indexType,
                            MeshOwnership ownership) implements AutoCloseable {
    public GpuMeshHandle(GpuBuffer vertexBuffer,
                         GpuBuffer indexBuffer,
                         long vertexOffsetBytes,
                         int baseVertex,
                         int firstIndex,
                         int indexCount,
                         IndexType indexType,
                         MeshOwnership ownership) {
        this(vertexBuffer,
                indexBuffer,
                vertexOffsetBytes,
                inferVertexBytes(vertexBuffer, vertexOffsetBytes),
                0,
                baseVertex,
                firstIndex,
                indexCount,
                safeIndexBytes(indexCount, indexType),
                indexType,
                ownership);
    }

    public GpuMeshHandle(GpuBuffer vertexBuffer,
                         GpuBuffer indexBuffer,
                         long vertexOffsetBytes,
                         int firstIndex,
                         int indexCount,
                         IndexType indexType,
                         MeshOwnership ownership) {
        this(vertexBuffer, indexBuffer, vertexOffsetBytes, 0, firstIndex, indexCount, indexType, ownership);
    }

    public GpuMeshHandle {
        if (indexType == null) throw new IllegalArgumentException("indexType");
        if (ownership == null) throw new IllegalArgumentException("ownership");
    }

    public static GpuMeshHandle arena(GpuBuffer vertexBuffer,
                                      GpuBuffer indexBuffer,
                                      int vertexOffsetBytes,
                                      int vertexBytes,
                                      int vertexStride,
                                      int baseVertex,
                                      int indexOffsetBytes,
                                      int indexBytes,
                                      int indexCount,
                                      MeshOwnership ownership) {
        return new GpuMeshHandle(
                vertexBuffer,
                indexBuffer,
                vertexOffsetBytes,
                vertexBytes,
                vertexStride,
                baseVertex,
                indexOffsetBytes / Integer.BYTES,
                indexCount,
                indexBytes,
                IndexType.INT,
                ownership
        );
    }

    private static int inferVertexBytes(@Nullable GpuBuffer buffer, long vertexOffsetBytes) {
        if (buffer == null || buffer.isClosed()) return 0;
        long size = buffer.size();
        long remaining = Math.max(0L, size - vertexOffsetBytes);
        return remaining > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remaining;
    }

    private static int safeIndexBytes(int indexCount, IndexType indexType) {
        if (indexCount <= 0 || indexType == null) return 0;
        long bytes = (long) indexCount * indexType.bytes;
        return bytes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) bytes;
    }

    private static void closeQuietly(@Nullable GpuBuffer buffer) {
        if (buffer == null || buffer.isClosed()) return;
        buffer.close();
    }

    /**
     * Current RenderPass API has no vertex byte offset on setVertexBuffer; baseVertex carries the offset for indexed draws.
     */
    public boolean hasNonZeroVertexOffset() {
        return vertexOffsetBytes != 0L;
    }

    public void validateForDraw(String label) {
        String owner = label != null ? label : "RHI draw";
        if (vertexBuffer == null || vertexBuffer.isClosed()) {
            throw new IllegalStateException(owner + ": mesh vertex buffer is null/closed: " + debugString());
        }
        if (indexBuffer == null || indexBuffer.isClosed()) {
            throw new IllegalStateException(owner + ": mesh index buffer is null/closed: " + debugString());
        }
        if (vertexOffsetBytes < 0L) {
            throw new IllegalStateException(owner + ": negative vertex offset: " + debugString());
        }
        if (vertexBytes < 0 || vertexStride < 0 || baseVertex < 0 || firstIndex < 0 || indexCount < 0 || indexBytes < 0) {
            throw new IllegalStateException(owner + ": negative mesh draw range: " + debugString());
        }
        if (indexType == null || indexType.bytes <= 0) {
            throw new IllegalStateException(owner + ": invalid index type: " + debugString());
        }

        long vertexSize = vertexBuffer.size();
        long vertexEnd = vertexOffsetBytes + (long) vertexBytes;
        if (vertexEnd > vertexSize) {
            throw new IndexOutOfBoundsException(owner + ": mesh vertex range exceeds GPU buffer: end=" + vertexEnd
                    + ", bufferSize=" + vertexSize + ", " + debugString());
        }

        long firstIndexByte = (long) firstIndex * indexType.bytes;
        long expectedIndexBytes = (long) indexCount * indexType.bytes;
        if (indexBytes != 0 && indexBytes != expectedIndexBytes) {
            throw new IllegalStateException(owner + ": mesh index byte count does not match indexCount/type: expected="
                    + expectedIndexBytes + ", actual=" + indexBytes + ", " + debugString());
        }
        long indexEnd = firstIndexByte + expectedIndexBytes;
        long indexSize = indexBuffer.size();
        if (indexEnd > indexSize) {
            throw new IndexOutOfBoundsException(owner + ": mesh index range exceeds GPU buffer: end=" + indexEnd
                    + ", bufferSize=" + indexSize + ", " + debugString());
        }

        if (vertexStride > 0) {
            if (vertexOffsetBytes % vertexStride != 0L) {
                throw new IllegalStateException(owner + ": mesh vertex offset is not stride-aligned: " + debugString());
            }
            if (vertexBytes % vertexStride != 0) {
                throw new IllegalStateException(owner + ": mesh vertex byte count is not stride-aligned: " + debugString());
            }
            long expectedOffset = (long) baseVertex * vertexStride;
            if (expectedOffset != vertexOffsetBytes) {
                throw new IllegalStateException(owner + ": mesh baseVertex does not match vertex offset: expectedOffset="
                        + expectedOffset + ", " + debugString());
            }
        }
    }

    /**
     * Blaze3D 26.2 RenderPass.drawIndexed argument order is:
     *     (indexCount, instanceCount, firstIndex, baseVertex, firstInstance)
     * - Vulkan forwards directly to vkCmdDrawIndexed(indexCount, instanceCount, firstIndex, baseVertex, firstInstance).
     * - OpenGL forwards to glDrawElementsInstancedBaseVertex(..., indexCount, firstIndex * indexType.bytes, instanceCount, baseVertex).
     * Keeping the call centralized prevents accidentally passing baseVertex as
     * instanceCount, which makes first arena meshes disappear and later arena
     * meshes draw thousands of instances from unrelated vertex ranges.
     */
    public void drawIndexed(RenderPass pass, String label) {
        validateForDraw(label);
        pass.drawIndexed(indexCount, 1, firstIndex, baseVertex, 0);
    }

    public String debugString() {
        return "GpuMeshHandle{" +
                "vertexOffsetBytes=" + vertexOffsetBytes +
                ", vertexBytes=" + vertexBytes +
                ", vertexStride=" + vertexStride +
                ", baseVertex=" + baseVertex +
                ", firstIndex=" + firstIndex +
                ", indexCount=" + indexCount +
                ", indexBytes=" + indexBytes +
                ", indexType=" + indexType +
                ", ownership=" + ownership +
                '}';
    }

    @Override
    public void close() {
        if (ownership != MeshOwnership.TEMPORARY_OWNED) return;
        closeQuietly(vertexBuffer);
        closeQuietly(indexBuffer);
    }
}
