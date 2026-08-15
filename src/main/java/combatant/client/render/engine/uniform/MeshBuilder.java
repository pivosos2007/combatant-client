/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on, adapted from, or implemented
 * with reference to Meteor Client
 * (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Licensed under the GNU General Public License v3.0.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.render.engine.uniform;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.guard.LegacyRenderPath;
import combatant.client.render.engine.guard.RenderArchitectureGuard;
import combatant.client.render.engine.renderer.RenderWarp;
import combatant.client.render.engine.renderer.RenderWarpStack;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

import static org.lwjgl.system.MemoryUtil.*;

/**
 * Reusable CPU-side mesh builder backed by native direct buffers.
 *
 * <p>Geometry is written between {@link #begin()} and {@link #end()}, then consumed through
 * {@link #vertexBufferView()} and {@link #indexBufferView()} by the RHI dynamic-mesh upload path.
 * The builder owns only its CPU buffers; GPU buffer allocation and lifetime are managed by the renderer.</p>
 *
 * <p>Reserve or ensure enough capacity before writing each primitive. Call {@link #close()} when the
 * builder itself is no longer needed to release its native memory.</p>
 */
public final class MeshBuilder implements AutoCloseable {
    private static final boolean DEBUG =
            FabricLoader.getInstance().isDevelopmentEnvironment() || Boolean.getBoolean("combatant.render.debug");
    private static final int DEFAULT_VERTEX_CAPACITY = Integer.getInteger("combatant.render.mesh.defaultVertices", 4096);
    private static final int DEFAULT_INDEX_CAPACITY = Integer.getInteger("combatant.render.mesh.defaultIndices", 8192);
    private static final int MAX_BUFFER_BYTES = Integer.MAX_VALUE - 8;
    private static final FrameStats FRAME_STATS = new FrameStats();
    private final VertexFormat format;
    private final int vertexStride;
    private final int primitiveIndicesCount;
    private final double[] warped2 = new double[2];
    public double alpha = 1.0;
    private ByteBuffer vertices;
    private long verticesPtrStart, verticesPtr;
    private ByteBuffer indices;
    private long indicesPtr;
    private int vertexI;
    private int indicesCount;
    private boolean building;
    private boolean worldCameraAnchored;
    private double cameraX, cameraZ;

    public MeshBuilder(RenderPipeline pipeline) {
        this(Objects.requireNonNull(pipeline.getVertexFormatBinding(0)), pipeline.getPrimitiveTopology());
    }

    public MeshBuilder(VertexFormat format, com.mojang.blaze3d.PrimitiveTopology drawMode) {
        this.format = format;
        this.vertexStride = format.getVertexSize();
        this.primitiveIndicesCount = drawMode.primitiveLength;
    }

    public MeshBuilder(VertexFormat format, com.mojang.blaze3d.PrimitiveTopology drawMode, int vertexCount, int indexCount) {
        this(format, drawMode);
        reserve(vertexCount, indexCount);
    }

    private static ByteBuffer allocateDirect(int bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("MeshBuilder buffer size must be positive: " + bytes);
        }
        if (bytes > MAX_BUFFER_BYTES) {
            throw new OutOfMemoryError("MeshBuilder buffer request is too large: " + bytes);
        }
        return memAlloc(bytes);
    }

    private static void freeDirect(ByteBuffer buffer) {
        if (buffer != null) {
            memFree(buffer);
        }
    }

    public static void resetFrameStats() {
        FRAME_STATS.reset();
    }

    public static FrameStatsSnapshot frameStatsSnapshot() {
        return FRAME_STATS.snapshot();
    }

    public void begin() {
        if (RenderState.rendering3D) {
            beginWorld(resolveRenderCameraPos());
        } else {
            beginScreen();
        }
    }

    /**
     * Begin a 2D/screen-space mesh. No world camera anchoring is applied.
     */
    public void beginScreen() {
        beginInternal(false, Vec3.ZERO);
    }

    /**
     * Begin a world-space mesh with an explicit camera anchor.
     *
     * <p>World vertices keep full Y, but X/Z are written camera-relative to avoid
     * float precision loss. The RHI world pass then applies the matching -cameraY
     * translation immediately before writing the MeshData UBO. Do not make this
     * depend on ambient RenderState in world command paths: the caller must pass
     * the camera that belongs to the current frame.</p>
     */
    public void beginWorld(Vec3 cameraPos) {
        if (cameraPos == null) {
            throw new IllegalArgumentException("MeshBuilder.beginWorld() requires a camera position");
        }
        beginInternal(true, cameraPos);
    }

    private void beginInternal(boolean worldCameraAnchored, Vec3 cameraPos) {
        if (building) throw new IllegalStateException("Mesh.begin() called while already building.");

        if (vertices == null || indices == null) reserve(DEFAULT_VERTEX_CAPACITY, DEFAULT_INDEX_CAPACITY);

        verticesPtr = verticesPtrStart;
        vertexI = 0;
        indicesCount = 0;

        this.worldCameraAnchored = worldCameraAnchored;
        this.cameraX = worldCameraAnchored ? cameraPos.x : 0.0;
        this.cameraZ = worldCameraAnchored ? cameraPos.z : 0.0;

        building = true;
    }

    private static Vec3 resolveRenderCameraPos() {
        if (RenderState.cameraPos != null && RenderState.cameraPos != Vec3.ZERO) {
            return RenderState.cameraPos;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null) {
            return mc.gameRenderer.mainCamera().position();
        }
        return Vec3.ZERO;
    }

    public MeshBuilder vec3(double x, double y, double z) {
        debugVertexWriteCapacity(12, "Position3");

        long p = verticesPtr;

        memPutFloat(p, (float) (x - cameraX));
        memPutFloat(p + 4, (float) y);
        memPutFloat(p + 8, (float) (z - cameraZ));

        verticesPtr += 12;
        return this;
    }

    public MeshBuilder vec2(double x, double y) {
        debugVertexWriteCapacity(8, "Position2");

        RenderWarp warp = RenderWarpStack.current();
        if (warp.active()) {
            warp.map(x, y, warped2);
            x = warped2[0];
            y = warped2[1];
        }

        long p = verticesPtr;

        memPutFloat(p, (float) x);
        memPutFloat(p + 4, (float) y);

        verticesPtr += 8;
        return this;
    }

    public MeshBuilder raw2(double x, double y) {
        debugVertexWriteCapacity(8, "Raw2");

        long p = verticesPtr;

        memPutFloat(p, (float) x);
        memPutFloat(p + 4, (float) y);

        verticesPtr += 8;
        return this;
    }

    public MeshBuilder local2(double x, double y) {
        debugVertexWriteCapacity(16, "Local");

        RenderWarp warp = RenderWarpStack.current();
        double invW = warp.active() ? warp.interpolationInvW(x, y) : 1.0;

        long p = verticesPtr;

        memPutFloat(p, (float) (x * invW));
        memPutFloat(p + 4, (float) (y * invW));
        memPutFloat(p + 8, (float) invW);
        memPutFloat(p + 12, 0.0f);

        verticesPtr += 16;
        return this;
    }

    /**
     * Writes source-space local coordinates without projective pre-weighting.
     * Used by tessellated warped SDF meshes where interpolation error is bounded
     * geometrically by the grid instead of encoded into a four-vertex quad.
     */
    public MeshBuilder rawLocal2(double x, double y) {
        debugVertexWriteCapacity(16, "Local");

        long p = verticesPtr;
        memPutFloat(p, (float) x);
        memPutFloat(p + 4, (float) y);
        memPutFloat(p + 8, 1.0f);
        memPutFloat(p + 12, 0.0f);

        verticesPtr += 16;
        return this;
    }

    public MeshBuilder vec4(float x, float y, float z, float w) {
        debugVertexWriteCapacity(16, "Vec4");

        long p = verticesPtr;

        memPutFloat(p, x);
        memPutFloat(p + 4, y);
        memPutFloat(p + 8, z);
        memPutFloat(p + 12, w);

        verticesPtr += 16;
        return this;
    }

    public MeshBuilder vec4(double x, double y, double z, double w) {
        return vec4((float) x, (float) y, (float) z, (float) w);
    }

    /**
     * Color as bytes 0..255
     */
    public MeshBuilder color(int r, int g, int b, int a) {
        debugVertexWriteCapacity(4, "Color");

        long p = verticesPtr;

        memPutByte(p, (byte) r);
        memPutByte(p + 1, (byte) g);
        memPutByte(p + 2, (byte) b);
        memPutByte(p + 3, (byte) Math.max(0, Math.min(255, (int) (a * alpha))));

        verticesPtr += 4;
        return this;
    }

    /**
     * Color as floats 0..1
     */
    public MeshBuilder color(float r, float g, float b, float a) {
        return color(
                (int) (r * 255f),
                (int) (g * 255f),
                (int) (b * 255f),
                (int) (a * 255f)
        );
    }

    /**
     * Color from RenderColor (0..255).
     */
    public MeshBuilder color(RenderColor color) {
        return color(color.r, color.g, color.b, color.a);
    }

    public int next() {
        debugCompletedVertex();
        return vertexI++;
    }

    public void line(int i1, int i2) {
        debugIndexWriteCapacity(2, "line");

        long p = indicesPtr + indicesCount * 4L;

        memPutInt(p, i1);
        memPutInt(p + 4, i2);

        indicesCount += 2;
    }

    public void triangle(int i1, int i2, int i3) {
        debugIndexWriteCapacity(3, "triangle");

        long p = indicesPtr + indicesCount * 4L;

        memPutInt(p, i1);
        memPutInt(p + 4, i2);
        memPutInt(p + 8, i3);

        indicesCount += 3;
    }

    public void quad(int i1, int i2, int i3, int i4) {
        debugIndexWriteCapacity(6, "quad");

        long p = indicesPtr + indicesCount * 4L;

        memPutInt(p, i1);
        memPutInt(p + 4, i2);
        memPutInt(p + 8, i3);

        memPutInt(p + 12, i3);
        memPutInt(p + 16, i4);
        memPutInt(p + 20, i1);

        indicesCount += 6;
    }

    public void ensureLineCapacity() {
        ensureCapacity(2, 2);
    }

    public void ensureTriCapacity() {
        ensureCapacity(3, 3);
    }

    public void ensureQuadCapacity() {
        ensureCapacity(4, 6);
    }

    public void ensureCapacity(int addVertices, int addIndices) {
        if (DEBUG && (addIndices % primitiveIndicesCount != 0)) {
            throw new IllegalArgumentException("Unexpected amount of indices written to MeshBuilder.");
        }

        if (vertices == null || indices == null) {
            reserve(Math.max(DEFAULT_VERTEX_CAPACITY, vertexI + addVertices),
                    Math.max(DEFAULT_INDEX_CAPACITY, indicesCount + addIndices));
            return;
        }

        int vNeedBytes = (vertexI + addVertices) * vertexStride;
        if (vNeedBytes > vertices.capacity()) {
            int offset = getVerticesOffset();
            int newSize = Math.max(vertices.capacity() * 2, vertices.capacity() + addVertices * vertexStride);

            ByteBuffer oldVertices = vertices;
            ByteBuffer newVertices = allocateDirect(newSize);
            memCopy(memAddress0(oldVertices), memAddress0(newVertices), offset);

            FRAME_STATS.vertexReallocs++;
            FRAME_STATS.allocatedBytes += newSize;
            FRAME_STATS.maxVertexBytes = Math.max(FRAME_STATS.maxVertexBytes, newSize);

            vertices = newVertices;
            verticesPtrStart = memAddress0(vertices);
            verticesPtr = verticesPtrStart + offset;
            freeDirect(oldVertices);
        }

        int iNeedBytes = (indicesCount + addIndices) * Integer.BYTES;
        if (iNeedBytes > indices.capacity()) {
            int newSize = Math.max(indices.capacity() * 2, indices.capacity() + addIndices * Integer.BYTES);

            ByteBuffer oldIndices = indices;
            ByteBuffer newIndices = allocateDirect(newSize);
            memCopy(memAddress0(oldIndices), memAddress0(newIndices), (long) indicesCount * 4L);

            FRAME_STATS.indexReallocs++;
            FRAME_STATS.allocatedBytes += newSize;
            FRAME_STATS.maxIndexBytes = Math.max(FRAME_STATS.maxIndexBytes, newSize);

            indices = newIndices;
            indicesPtr = memAddress0(indices);
            freeDirect(oldIndices);
        }
    }

    /**
     * Ensures that the CPU-side direct buffers can hold at least the requested amount of vertices/indices.
     *
     * <p>This method is intentionally separate from {@link #begin()}: render code that owns a long-lived
     * MeshBuilder can pre-warm it during initialization instead of paying ByteBuffer.allocateDirect on the
     * render-thread hot path. If the current buffers are already large enough, this is a no-op.</p>
     */
    public void reserve(int vertexCount, int indexCount) {
        int vertexBytes = vertexStride * Math.max(1, vertexCount);
        int indexBytes = Math.max(1, indexCount) * Integer.BYTES;
        boolean initialAllocation = vertices == null && indices == null;

        if (vertices == null || vertexBytes > vertices.capacity()) {
            int offset = vertices != null ? getVerticesOffset() : 0;
            ByteBuffer oldVertices = vertices;
            ByteBuffer newVertices = allocateDirect(vertexBytes);
            if (oldVertices != null && offset > 0) {
                memCopy(memAddress0(oldVertices), memAddress0(newVertices), offset);
            }

            vertices = newVertices;
            verticesPtrStart = memAddress0(vertices);
            verticesPtr = verticesPtrStart + offset;
            freeDirect(oldVertices);

            if (!initialAllocation) {
                FRAME_STATS.vertexReallocs++;
            }
            FRAME_STATS.allocatedBytes += vertexBytes;
            FRAME_STATS.maxVertexBytes = Math.max(FRAME_STATS.maxVertexBytes, vertexBytes);
        }

        if (indices == null || indexBytes > indices.capacity()) {
            long offset = indices != null ? (long) indicesCount * Integer.BYTES : 0L;
            ByteBuffer oldIndices = indices;
            ByteBuffer newIndices = allocateDirect(indexBytes);
            if (oldIndices != null && offset > 0L) {
                memCopy(memAddress0(oldIndices), memAddress0(newIndices), offset);
            }

            indices = newIndices;
            indicesPtr = memAddress0(indices);
            freeDirect(oldIndices);

            if (!initialAllocation) {
                FRAME_STATS.indexReallocs++;
            }
            FRAME_STATS.allocatedBytes += indexBytes;
            FRAME_STATS.maxIndexBytes = Math.max(FRAME_STATS.maxIndexBytes, indexBytes);
        }

        if (initialAllocation && vertices != null && indices != null) {
            FRAME_STATS.initialAllocations++;
        }
    }

    public void end() {
        validateComplete("MeshBuilder.end");
        building = false;
    }

    public boolean isBuilding() {
        return building;
    }

    /**
     * Append an already-built mesh into this builder, rebasing INT indices by the current vertex count.
     * This is used by higher level batchers (UI/text) to preserve draw order without flushing per element.
     */
    public void appendMesh(MeshBuilder source) {
        if (source == null || source.getVertexCount() <= 0 || source.getIndicesCount() <= 0) return;
        if (source.isBuilding()) source.end();
        if (!building) {
            throw new IllegalStateException("appendMesh() requires destination mesh to be building.");
        }
        if (this.vertexStride != source.vertexStride || !this.format.equals(source.format)) {
            throw new IllegalArgumentException("Cannot append mesh with different vertex format/stride.");
        }

        int sourceVertices = source.getVertexCount();
        int sourceIndices = source.getIndicesCount();
        int baseVertex = this.vertexI;
        ensureCapacity(sourceVertices, sourceIndices);

        ByteBuffer vertexSource = source.vertexBufferView();
        long vertexBytes = source.getVertexBytes();
        memCopy(memAddress0(vertexSource), verticesPtr, vertexBytes);
        verticesPtr += vertexBytes;
        vertexI += sourceVertices;

        ByteBuffer indexSource = source.indexBufferView();
        long indexIn = memAddress0(indexSource);
        long indexOut = indicesPtr + (long) indicesCount * Integer.BYTES;
        for (int i = 0; i < sourceIndices; i++) {
            memPutInt(indexOut + (long) i * Integer.BYTES, memGetInt(indexIn + (long) i * Integer.BYTES) + baseVertex);
        }
        indicesCount += sourceIndices;
    }

    public ByteBuffer vertexBufferView() {
        validateComplete("MeshBuilder.vertexBufferView");
        ByteBuffer copy = vertices.duplicate().order(ByteOrder.nativeOrder());
        copy.position(0);
        copy.limit(getVerticesOffset());
        return copy.slice().order(ByteOrder.nativeOrder());
    }

    public ByteBuffer indexBufferView() {
        validateComplete("MeshBuilder.indexBufferView");
        ByteBuffer copy = indices.duplicate().order(ByteOrder.nativeOrder());
        copy.position(0);
        copy.limit(indicesCount * Integer.BYTES);
        return copy.slice().order(ByteOrder.nativeOrder());
    }

    public int getVertexBytes() {
        return getVerticesOffset();
    }

    public int getVertexStride() {
        return vertexStride;
    }

    public boolean isWorldCameraAnchored() {
        return worldCameraAnchored;
    }

    public double cameraAnchorX() {
        return cameraX;
    }

    public double cameraAnchorZ() {
        return cameraZ;
    }

    public int getIndexBytes() {
        return indicesCount * Integer.BYTES;
    }

    /**
     * Emergency compatibility upload. This path is locked after the RHI migration and is not allowed as production
     * behavior. Use CombatantRHI.dynamicMeshes().upload(mesh) instead.
     */
    @Deprecated
    public GpuBuffer getVertexBuffer() {
        RenderArchitectureGuard.requireAllowed(LegacyRenderPath.IMMEDIATE_MESH_UPLOAD, "MeshBuilder#getVertexBuffer");
        return RenderSystem.getDevice().createBuffer(
                () -> "combatant-immediate-vertices",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                vertexBufferView()
        );
    }

    /**
     * Emergency compatibility upload. This path is locked after the RHI migration and is not allowed as production
     * behavior. Use CombatantRHI.dynamicMeshes().upload(mesh) instead.
     */
    @Deprecated
    public GpuBuffer getIndexBuffer() {
        RenderArchitectureGuard.requireAllowed(LegacyRenderPath.IMMEDIATE_MESH_UPLOAD, "MeshBuilder#getIndexBuffer");
        return RenderSystem.getDevice().createBuffer(
                () -> "combatant-immediate-indices",
                GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST,
                indexBufferView()
        );
    }

    public com.mojang.blaze3d.IndexType getIndexType() {
        // we write ints in indices()
        return com.mojang.blaze3d.IndexType.INT;
    }

    public int getIndicesCount() {
        return indicesCount;
    }

    public int getVertexCount() {
        return vertexI;
    }

    private int getVerticesOffset() {
        return (int) (verticesPtr - verticesPtrStart);
    }

    public void validateComplete(String owner) {
        debugHasBuffers(owner);
        int vertexBytes = getVerticesOffset();
        int expectedVertexBytes = vertexI * vertexStride;
        if (vertexBytes != expectedVertexBytes) {
            throw new IllegalStateException(owner + ": MeshBuilder vertex stride mismatch: vertexCount=" + vertexI
                    + ", expectedVertexBytes=" + expectedVertexBytes
                    + ", actualVertexBytes=" + vertexBytes
                    + ", stride=" + vertexStride
                    + ", format=" + format);
        }
        if (vertexBytes < 0 || vertexBytes > vertices.capacity()) {
            throw new IndexOutOfBoundsException(owner + ": MeshBuilder vertex range outside CPU buffer: bytes="
                    + vertexBytes + ", capacity=" + vertices.capacity() + ", format=" + format);
        }
        int indexBytes = indicesCount * Integer.BYTES;
        if (indexBytes < 0 || indexBytes > indices.capacity()) {
            throw new IndexOutOfBoundsException(owner + ": MeshBuilder index range outside CPU buffer: bytes="
                    + indexBytes + ", capacity=" + indices.capacity() + ", format=" + format);
        }
        if (primitiveIndicesCount > 0 && indicesCount % primitiveIndicesCount != 0) {
            throw new IllegalStateException(owner + ": MeshBuilder incomplete primitive payload: indices=" + indicesCount
                    + ", primitiveLength=" + primitiveIndicesCount + ", format=" + format);
        }
        debugValidateIndices(owner);
    }


    /**
     * Releases CPU-side native buffers owned by this builder. The builder may be reused later;
     * the next begin()/reserve() call will allocate fresh buffers.
     */
    @Override
    public void close() {
        if (building) {
            building = false;
        }
        freeDirect(vertices);
        freeDirect(indices);
        vertices = null;
        indices = null;
        verticesPtrStart = 0L;
        verticesPtr = 0L;
        indicesPtr = 0L;
        vertexI = 0;
        indicesCount = 0;
        worldCameraAnchored = false;
        cameraX = 0.0;
        cameraZ = 0.0;
    }

    private void debugHasBuffers(String owner) {
        if (vertices == null || indices == null) {
            throw new IllegalStateException(owner + ": MeshBuilder has no CPU buffers; call begin()/reserve() before writing.");
        }
    }

    private void debugVertexWriteCapacity(int bytes, String attribute) {
        if (!DEBUG) return;
        debugHasBuffers("MeshBuilder." + attribute);
        if (!building) {
            throw new IllegalStateException("MeshBuilder." + attribute + " write outside begin()/end(): format=" + format);
        }
        if (bytes <= 0) {
            throw new IllegalArgumentException("MeshBuilder." + attribute + " invalid write size: " + bytes);
        }
        int vertexBytes = getVerticesOffset();
        int currentVertexBytes = vertexBytes - vertexI * vertexStride;
        if (currentVertexBytes < 0 || currentVertexBytes > vertexStride) {
            throw new IllegalStateException("MeshBuilder." + attribute + " cursor is not inside current vertex: vertex=" + vertexI
                    + ", cursorBytes=" + vertexBytes
                    + ", currentVertexBytes=" + currentVertexBytes
                    + ", stride=" + vertexStride
                    + ", format=" + format);
        }
        if (currentVertexBytes + bytes > vertexStride) {
            throw new IllegalStateException("MeshBuilder." + attribute + " overruns vertex stride: vertex=" + vertexI
                    + ", currentVertexBytes=" + currentVertexBytes
                    + ", writeBytes=" + bytes
                    + ", stride=" + vertexStride
                    + ", format=" + format);
        }
        if (vertexBytes + bytes > vertices.capacity()) {
            throw new IndexOutOfBoundsException("MeshBuilder." + attribute + " write exceeds vertex CPU buffer: vertex=" + vertexI
                    + ", cursorBytes=" + vertexBytes
                    + ", writeBytes=" + bytes
                    + ", capacity=" + vertices.capacity()
                    + ", format=" + format);
        }
    }

    private void debugCompletedVertex() {
        if (!DEBUG) return;
        debugHasBuffers("MeshBuilder.next");
        int vertexBytes = getVerticesOffset();
        int expected = (vertexI + 1) * vertexStride;
        if (vertexBytes != expected) {
            throw new IllegalStateException("MeshBuilder.next() called with incomplete/overwritten vertex: vertex=" + vertexI
                    + ", expectedBytes=" + expected
                    + ", actualBytes=" + vertexBytes
                    + ", stride=" + vertexStride
                    + ", format=" + format);
        }
    }

    private void debugIndexWriteCapacity(int addIndices, String primitive) {
        if (!DEBUG) return;
        debugHasBuffers("MeshBuilder." + primitive);
        if (!building) {
            throw new IllegalStateException("MeshBuilder." + primitive + " index write outside begin()/end(): format=" + format);
        }
        if (addIndices <= 0) {
            throw new IllegalArgumentException("MeshBuilder." + primitive + " invalid index count: " + addIndices);
        }
        int indexBytes = indicesCount * Integer.BYTES;
        int writeBytes = addIndices * Integer.BYTES;
        if (indexBytes + writeBytes > indices.capacity()) {
            throw new IndexOutOfBoundsException("MeshBuilder." + primitive + " write exceeds index CPU buffer: indices=" + indicesCount
                    + ", add=" + addIndices
                    + ", capacityBytes=" + indices.capacity()
                    + ", format=" + format);
        }
    }

    private void debugValidateIndices(String owner) {
        if (!DEBUG || indicesCount <= 0) return;
        long p = indicesPtr;
        for (int i = 0; i < indicesCount; i++) {
            int index = memGetInt(p + (long) i * Integer.BYTES);
            if (index < 0 || index >= vertexI) {
                throw new IndexOutOfBoundsException(owner + ": MeshBuilder index references missing vertex: indexOrdinal=" + i
                        + ", index=" + index
                        + ", vertexCount=" + vertexI
                        + ", indicesCount=" + indicesCount
                        + ", format=" + format);
            }
        }
    }

    public record FrameStatsSnapshot(int initialAllocations,
                                     int vertexReallocs,
                                     int indexReallocs,
                                     long allocatedBytes,
                                     int maxVertexBytes,
                                     int maxIndexBytes) {
    }

    private static final class FrameStats {
        private int initialAllocations;
        private int vertexReallocs;
        private int indexReallocs;
        private long allocatedBytes;
        private int maxVertexBytes;
        private int maxIndexBytes;

        private void reset() {
            initialAllocations = 0;
            vertexReallocs = 0;
            indexReallocs = 0;
            allocatedBytes = 0L;
            maxVertexBytes = 0;
            maxIndexBytes = 0;
        }

        private FrameStatsSnapshot snapshot() {
            return new FrameStatsSnapshot(initialAllocations, vertexReallocs, indexReallocs, allocatedBytes, maxVertexBytes, maxIndexBytes);
        }
    }
}
