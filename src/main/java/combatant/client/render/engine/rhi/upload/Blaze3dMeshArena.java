/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.upload;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * Backend-owned dynamic mesh arena.
 * <p>
 * A frame can use several arenas. Persistent arenas are reused after their frame fence completes. Spill arenas
 * are allocated only when persistent arenas are exhausted or a single mesh is larger than the configured ring.
 */
final class Blaze3dMeshArena implements AutoCloseable {
    private final String name;
    private final boolean persistent;
    private final int vertexCapacity;
    private final int indexCapacity;
    private final GpuBuffer vertexBuffer;
    private final GpuBuffer indexBuffer;

    private int vertexCursor;
    private int indexCursor;
    private boolean usedThisFrame;
    private @Nullable Blaze3dFrameFence fence;

    Blaze3dMeshArena(String name, int vertexCapacity, int indexCapacity, boolean persistent) {
        this.name = name;
        this.persistent = persistent;
        this.vertexCapacity = vertexCapacity;
        this.indexCapacity = indexCapacity;
        this.vertexBuffer = RenderSystem.getDevice().createBuffer(named(name, " vertices"),
                GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_VERTEX, vertexCapacity);
        this.indexBuffer = RenderSystem.getDevice().createBuffer(named(name, " indices"),
                GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_INDEX, indexCapacity);
    }

    private static Supplier<String> named(String name, String suffix) {
        return () -> name + suffix;
    }

    private static void write(String owner, GpuBufferSlice slice, ByteBuffer src, int expectedBytes) {
        if (src == null) {
            throw new IllegalArgumentException(owner + ": source buffer is null");
        }
        if (src.remaining() != expectedBytes) {
            throw new IllegalStateException(owner + ": source byte count mismatch: expected=" + expectedBytes
                    + ", remaining=" + src.remaining());
        }
        try (GpuBufferSlice.MappedView view = slice.map(false, true)) {
            ByteBuffer dst = view.data();
            if (dst.remaining() < expectedBytes) {
                throw new IllegalStateException(owner + ": mapped destination is smaller than upload: dstRemaining="
                        + dst.remaining() + ", expected=" + expectedBytes);
            }
            dst.put(src.duplicate());
        }
    }

    /**
     * Aligns to an arbitrary positive byte alignment.
     * <p>
     * The previous implementation used the common bit-mask form:
     * (value + alignment - 1) & -alignment
     * which is only valid when {@code alignment} is a power of two. UI vertex
     * formats often have non-power-of-two strides (for example 20/44/60 bytes).
     * Using bit-mask alignment with those strides produced vertex offsets that
     * were not divisible by the active vertex stride, so drawIndexed(baseVertex, ...)
     * read vertices from the wrong location. That showed up as 2D flicker, missing
     * text/buttons and huge random triangles/polygons in HUD/menu rendering.
     */
    private static int align(int value, int alignment) {
        if (alignment <= 1) return value;
        int remainder = value % alignment;
        return remainder == 0 ? value : value + alignment - remainder;
    }

    GpuBuffer vertexBuffer() {
        return vertexBuffer;
    }

    GpuBuffer indexBuffer() {
        return indexBuffer;
    }

    int vertexCapacity() {
        return vertexCapacity;
    }

    int indexCapacity() {
        return indexCapacity;
    }

    boolean persistent() {
        return persistent;
    }

    boolean usedThisFrame() {
        return usedThisFrame;
    }

    boolean isRetired() {
        return fence != null;
    }

    boolean reclaimIfComplete() {
        if (fence == null) return true;
        if (!fence.isCompleted()) return false;
        fence.release();
        fence = null;
        resetForFrame();
        return true;
    }

    boolean canStartFrame() {
        return !usedThisFrame && reclaimIfComplete();
    }

    void resetForFrame() {
        vertexCursor = 0;
        indexCursor = 0;
        usedThisFrame = false;
    }

    boolean canAllocate(int vertexBytes, int indexBytes, int vertexStride) {
        validateRequest(vertexBytes, indexBytes, vertexStride);
        int alignedVertexCursor = align(vertexCursor, Math.max(4, vertexStride));
        int alignedIndexCursor = align(indexCursor, Integer.BYTES);
        return alignedVertexCursor + vertexBytes <= vertexCapacity && alignedIndexCursor + indexBytes <= indexCapacity;
    }

    Blaze3dMeshAllocation allocate(int vertexBytes, int indexBytes, int vertexStride) {
        validateRequest(vertexBytes, indexBytes, vertexStride);
        int alignedVertexCursor = align(vertexCursor, Math.max(4, vertexStride));
        int alignedIndexCursor = align(indexCursor, Integer.BYTES);
        if (alignedVertexCursor + vertexBytes > vertexCapacity || alignedIndexCursor + indexBytes > indexCapacity) {
            throw new IllegalStateException("Dynamic mesh arena overflow: " + name
                    + ", vertexCursor=" + vertexCursor
                    + ", alignedVertexCursor=" + alignedVertexCursor
                    + ", vertexBytes=" + vertexBytes
                    + ", vertexCapacity=" + vertexCapacity
                    + ", indexCursor=" + indexCursor
                    + ", alignedIndexCursor=" + alignedIndexCursor
                    + ", indexBytes=" + indexBytes
                    + ", indexCapacity=" + indexCapacity
                    + ", stride=" + vertexStride);
        }

        vertexCursor = align(alignedVertexCursor + vertexBytes, 16);
        indexCursor = align(alignedIndexCursor + indexBytes, Integer.BYTES);
        usedThisFrame = true;

        return new Blaze3dMeshAllocation(
                this,
                alignedVertexCursor,
                alignedIndexCursor,
                vertexBytes,
                indexBytes,
                Blaze3dMeshAllocation.baseVertex(alignedVertexCursor, vertexStride)
        );
    }

    private void validateRequest(int vertexBytes, int indexBytes, int vertexStride) {
        if (vertexStride <= 0) {
            throw new IllegalArgumentException("Invalid dynamic mesh vertex stride: " + vertexStride + " in " + name);
        }
        if (vertexBytes <= 0 || indexBytes <= 0) {
            throw new IllegalArgumentException("Invalid dynamic mesh upload request in " + name
                    + ": vertexBytes=" + vertexBytes + ", indexBytes=" + indexBytes + ", stride=" + vertexStride);
        }
        if (vertexBytes % vertexStride != 0) {
            throw new IllegalStateException("Dynamic mesh vertex byte count is not stride-aligned in " + name
                    + ": vertexBytes=" + vertexBytes + ", stride=" + vertexStride);
        }
        if (indexBytes % Integer.BYTES != 0) {
            throw new IllegalStateException("Dynamic mesh index byte count is not int-aligned in " + name
                    + ": indexBytes=" + indexBytes);
        }
    }

    void writeVertices(int offsetBytes, int bytes, ByteBuffer src) {
        validateRange("vertex upload", offsetBytes, bytes, vertexCapacity);
        write(name + " vertex upload", vertexBuffer.slice(offsetBytes, bytes), src, bytes);
    }

    void writeIndices(int offsetBytes, int bytes, ByteBuffer src) {
        validateRange("index upload", offsetBytes, bytes, indexCapacity);
        write(name + " index upload", indexBuffer.slice(offsetBytes, bytes), src, bytes);
    }

    private void validateRange(String owner, int offsetBytes, int bytes, int capacity) {
        if (offsetBytes < 0 || bytes < 0 || offsetBytes + bytes < offsetBytes || offsetBytes + bytes > capacity) {
            throw new IndexOutOfBoundsException("Dynamic mesh arena " + owner + " range outside buffer: " + name
                    + ", offset=" + offsetBytes + ", bytes=" + bytes + ", capacity=" + capacity);
        }
    }

    void retire(Blaze3dFrameFence frameFence) {
        if (!usedThisFrame) return;
        if (fence != null) fence.release();
        fence = frameFence;
    }

    @Override
    public void close() {
        if (fence != null) {
            fence.release();
            fence = null;
        }
        if (!vertexBuffer.isClosed()) vertexBuffer.close();
        if (!indexBuffer.isClosed()) indexBuffer.close();
    }
}
