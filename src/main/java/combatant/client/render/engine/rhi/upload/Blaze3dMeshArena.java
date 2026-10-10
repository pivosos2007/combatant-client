/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.upload;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
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
    private final boolean persistentMappedWrites;

    private int vertexCursor;
    private int indexCursor;
    /** Write mappings kept open across uploads; closed (flushed + unmapped) by {@link DynamicMeshWrites}. */
    private @Nullable GpuBufferSlice.MappedView vertexMapping;
    private @Nullable GpuBufferSlice.MappedView indexMapping;
    boolean trackedForFlush;
    private boolean usedThisFrame;
    private @Nullable Blaze3dFrameFence fence;

    Blaze3dMeshArena(String name, int vertexCapacity, int indexCapacity, boolean persistent, boolean persistentMappedWrites) {
        this.name = name;
        this.persistent = persistent;
        this.vertexCapacity = vertexCapacity;
        this.indexCapacity = indexCapacity;
        this.persistentMappedWrites = persistentMappedWrites;
        // Without persistent mapping (Sodium 0.9.2 turns ARB_buffer_storage off on NVIDIA and old
        // Intel) uploads go through CommandEncoder.writeToBuffer, which demands USAGE_COPY_DST.
        int writeUsage = persistentMappedWrites ? GpuBuffer.USAGE_MAP_WRITE : GpuBuffer.USAGE_COPY_DST;
        this.vertexBuffer = RenderSystem.getDevice().createBuffer(named(name, " vertices"),
                writeUsage | GpuBuffer.USAGE_VERTEX, vertexCapacity);
        this.indexBuffer = RenderSystem.getDevice().createBuffer(named(name, " indices"),
                writeUsage | GpuBuffer.USAGE_INDEX, indexCapacity);
    }

    private static Supplier<String> named(String name, String suffix) {
        return () -> name + suffix;
    }

    private void write(String owner, GpuBufferSlice slice, ByteBuffer src, int expectedBytes, @Nullable CommandEncoder encoder) {
        if (src == null) {
            throw new IllegalArgumentException(owner + ": source buffer is null");
        }
        if (src.remaining() != expectedBytes) {
            throw new IllegalStateException(owner + ": source byte count mismatch: expected=" + expectedBytes
                    + ", remaining=" + src.remaining());
        }

        if (!persistentMappedWrites) {
            if (encoder == null) {
                throw new IllegalStateException(owner + ": non-persistent upload requires a Blaze3D CommandEncoder");
            }
            // Blaze3D's GL backend lowers this to DirectStateAccess.bufferSubData(). Avoid a
            // Combatant-specific map/unmap fallback when Mojang already owns the efficient path.
            encoder.writeToBuffer(slice, src.duplicate());
            return;
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
     * Aligns to an arbitrary positive byte boundary. Bit-mask alignment is invalid for
     * non-power-of-two vertex strides such as 20, 44, or 60 bytes.
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

    boolean persistentMappedWrites() {
        return persistentMappedWrites;
    }

    int vertexUsedBytes() {
        return vertexCursor;
    }

    int indexUsedBytes() {
        return indexCursor;
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

    /**
     * Persistent-mapping upload: copies the mesh straight into the arena's open mapping with a native
     * memcpy. The mapping stays open until {@link DynamicMeshWrites#flushPending()} runs before a draw,
     * so N meshes cost one map/flush/unmap per buffer instead of N.
     */
    void writeMapped(int vertexOffsetBytes, int vertexBytes, int indexOffsetBytes, int indexBytes,
                     combatant.client.render.engine.uniform.MeshBuilder mesh) {
        validateRange("vertex upload", vertexOffsetBytes, vertexBytes, vertexCapacity);
        validateRange("index upload", indexOffsetBytes, indexBytes, indexCapacity);
        if (mesh.getVertexBytes() != vertexBytes) {
            throw new IllegalStateException(name + " vertex upload: source byte count mismatch: expected="
                    + vertexBytes + ", actual=" + mesh.getVertexBytes());
        }
        if (mesh.getIndexBytes() != indexBytes) {
            throw new IllegalStateException(name + " index upload: source byte count mismatch: expected="
                    + indexBytes + ", actual=" + mesh.getIndexBytes());
        }
        if (vertexMapping == null) vertexMapping = vertexBuffer.slice().map(false, true);
        if (indexMapping == null) indexMapping = indexBuffer.slice().map(false, true);
        mesh.copyVerticesTo(vertexMapping.data(), vertexOffsetBytes);
        mesh.copyIndicesTo(indexMapping.data(), indexOffsetBytes);
        DynamicMeshWrites.track(this);
    }

    /** Flushes and unmaps the open write mappings, if any. */
    void closeMappings() {
        GpuBufferSlice.MappedView vertices = vertexMapping;
        GpuBufferSlice.MappedView indices = indexMapping;
        vertexMapping = null;
        indexMapping = null;
        try {
            if (vertices != null) vertices.close();
        } finally {
            if (indices != null) indices.close();
        }
    }

    void writeAllocation(int vertexOffsetBytes, int vertexBytes, ByteBuffer vertexSrc,
                         int indexOffsetBytes, int indexBytes, ByteBuffer indexSrc) {
        validateRange("vertex upload", vertexOffsetBytes, vertexBytes, vertexCapacity);
        validateRange("index upload", indexOffsetBytes, indexBytes, indexCapacity);

        // Compatibility-tier uploads use one Mojang encoder for the vertex+index pair instead of
        // constructing one wrapper per buffer write. Persistent mapping needs no encoder at all.
        CommandEncoder encoder = persistentMappedWrites ? null : RenderSystem.getDevice().createCommandEncoder();
        write(name + " vertex upload", vertexBuffer.slice(vertexOffsetBytes, vertexBytes), vertexSrc, vertexBytes, encoder);
        write(name + " index upload", indexBuffer.slice(indexOffsetBytes, indexBytes), indexSrc, indexBytes, encoder);
    }

    private void validateRange(String owner, int offsetBytes, int bytes, int capacity) {
        if (offsetBytes < 0 || bytes < 0 || offsetBytes + bytes < offsetBytes || offsetBytes + bytes > capacity) {
            throw new IndexOutOfBoundsException("Dynamic mesh arena " + owner + " range outside buffer: " + name
                    + ", offset=" + offsetBytes + ", bytes=" + bytes + ", capacity=" + capacity);
        }
    }

    void retire(Blaze3dFrameFence frameFence) {
        closeMappings();
        if (!usedThisFrame) return;
        if (fence != null) fence.release();
        fence = frameFence;
    }

    @Override
    public void close() {
        closeMappings();
        if (fence != null) {
            fence.release();
            fence = null;
        }
        if (!vertexBuffer.isClosed()) vertexBuffer.close();
        if (!indexBuffer.isClosed()) indexBuffer.close();
    }
}
