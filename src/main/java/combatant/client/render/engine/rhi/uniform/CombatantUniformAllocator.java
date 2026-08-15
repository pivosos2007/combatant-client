/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.uniform;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single owner of Combatant dynamic uniform allocation.
 * every *Uniforms class is only a std140 writer. This allocator owns one large dynamic uniform ring,
 * suballocates aligned slices, rotates once at frame boundary only if any uniform was written, and clears
 * all current slices centrally.
 */
public final class CombatantUniformAllocator implements AutoCloseable {
    private static final int GPU_BUFFER_USAGE_MAP_WRITE = 130;
    private static final boolean DEBUG_UNIFORMS = Boolean.getBoolean("combatant.render.debug.uniforms");
    private static final int DEFAULT_CAPACITY_BYTES = 1024 * 1024;
    private static final boolean STRICT_UNIFORM_READS = Boolean.getBoolean("combatant.rhi.strictUniforms");

    private final Map<String, UniformStream> streams = new HashMap<>();
    private final List<MappableRingBuffer> oldRings = new ArrayList<>();
    private final UniformAllocatorStats stats = new UniformAllocatorStats();

    private MappableRingBuffer ring;
    private int capacityBytes;
    private int cursorBytes;
    private int alignment;
    private long frameId;
    private boolean usedThisFrame;

    public CombatantUniformAllocator() {
    }

    private static int align(int value, int alignment) {
        return Mth.roundToward(value, alignment);
    }

    public GpuBufferSlice write(String name, int std140Size, int expectedWritesPerFrame, UniformWriter writer) {
        ensureRing(Math.max(DEFAULT_CAPACITY_BYTES, aligned(std140Size) * Math.max(1, expectedWritesPerFrame)));

        UniformStream stream = streams.computeIfAbsent(name, key -> new UniformStream(key, aligned(std140Size)));
        int blockSize = aligned(std140Size);
        if (stream.cachedBuffer != null) {
            stream.cachedBuffer.close();
            stream.cachedBuffer = null;
            stream.cachedSlice = null;
            stream.lastCachedWriteFrame = Long.MIN_VALUE;
        }
        if (stream.blockSize != blockSize) {
            stream.blockSize = blockSize;
        }

        ensureCapacityFor(blockSize);

        int offset = cursorBytes;
        GpuBuffer currentBuffer = blockingBuffer();
        GpuBufferSlice slice = currentBuffer.slice(offset, blockSize);
        validateUniformSlice(name, slice, blockSize);

        try (GpuBufferSlice.MappedView mappedView = slice.map(false, true)) {
            writer.write(mappedView.data());
        }

        cursorBytes += blockSize;
        usedThisFrame = true;
        stream.usedThisFrame = true;
        stream.current = slice;

        stats.write(blockSize);
        stats.ringCursorBytes(cursorBytes);
        return slice;
    }


    /**
     * Writes a uniform into a dedicated persistent buffer no more often than once per {@code updateEveryFrames}.
     * <p>
     * This is intentionally separate from the per-frame dynamic ring. Reusing an old slice from the ring across
     * presented frames is unsafe because later frames can rotate/reuse the same backing buffer. Persistent cached
     * streams keep their own small UBO and can be bound every frame while only being mapped when the cadence says
     * to refresh the data.
     */
    public GpuBufferSlice writeCached(String name,
                                      int std140Size,
                                      int expectedWritesPerFrame,
                                      int updateEveryFrames,
                                      boolean force,
                                      UniformWriter writer) {
        int interval = Math.max(1, updateEveryFrames);
        if (interval <= 1) {
            return write(name, std140Size, expectedWritesPerFrame, writer);
        }

        int blockSize = aligned(std140Size);
        UniformStream stream = streams.computeIfAbsent(name, key -> new UniformStream(key, blockSize));
        if (stream.blockSize != blockSize) {
            stream.blockSize = blockSize;
            stream.lastCachedWriteFrame = Long.MIN_VALUE;
            if (stream.cachedBuffer != null) {
                stream.cachedBuffer.close();
                stream.cachedBuffer = null;
                stream.cachedSlice = null;
            }
        }

        if (stream.cachedBuffer == null) {
            stream.cachedBuffer = RenderSystem.getDevice().createBuffer(
                    () -> name + " cached",
                    GPU_BUFFER_USAGE_MAP_WRITE,
                    blockSize
            );
            stream.cachedSlice = stream.cachedBuffer.slice(0, blockSize);
            validateUniformSlice(name, stream.cachedSlice, blockSize);
            stream.lastCachedWriteFrame = Long.MIN_VALUE;
        }

        boolean shouldWrite = force
                || stream.cachedSlice == null
                || stream.lastCachedWriteFrame == Long.MIN_VALUE
                || frameId - stream.lastCachedWriteFrame >= interval;

        if (shouldWrite) {
            try (GpuBufferSlice.MappedView mappedView = stream.cachedSlice.map(false, true)) {
                writer.write(mappedView.data());
            }
            stream.lastCachedWriteFrame = frameId;
            usedThisFrame = true;
            stats.write(blockSize);
        }

        stream.usedThisFrame = true;
        stream.current = stream.cachedSlice;
        return stream.current;
    }

    @Nullable
    public GpuBufferSlice current(String name) {
        UniformStream stream = streams.get(name);
        GpuBufferSlice slice = stream == null ? null : stream.current;
        if (slice == null) {
            stats.staleReadMiss();
            if (STRICT_UNIFORM_READS) {
                throw new IllegalStateException("Uniform '" + name + "' was read before being updated in the current frame");
            }
        }
        return slice;
    }

    public boolean hasCurrent(String name) {
        UniformStream stream = streams.get(name);
        return stream != null && stream.current != null;
    }

    public void invalidate(String name) {
        UniformStream stream = streams.get(name);
        if (stream != null) {
            stream.current = null;
            stream.usedThisFrame = false;
            stream.lastCachedWriteFrame = Long.MIN_VALUE;
        }
    }

    public void beginFrame(long frameId) {
        this.frameId = frameId;
        stats.beginFrame(frameId);
        stats.streamCount(streams.size());
        stats.ringCapacityBytes(capacityBytes);
    }

    public long frameId() {
        return frameId;
    }

    public void onFramePresented() {
        long active = 0L;
        for (UniformStream stream : streams.values()) {
            if (stream.usedThisFrame) active++;
            if (stream.cachedBuffer == null) {
                stream.current = null;
            }
            stream.usedThisFrame = false;
        }

        stats.activeStreams(active);
        stats.streamCount(streams.size());
        stats.ringCapacityBytes(capacityBytes);
        stats.ringCursorBytes(cursorBytes);

        if (ring != null && usedThisFrame) {
            ring.rotate();
            stats.ringRotation();
        }

        cursorBytes = 0;
        usedThisFrame = false;

        if (!oldRings.isEmpty()) {
            for (MappableRingBuffer oldRing : oldRings) {
                oldRing.close();
            }
            oldRings.clear();
        }
    }

    public UniformAllocatorStats stats() {
        return stats;
    }

    public UniformAllocatorStatsSnapshot statsSnapshot() {
        return stats.snapshot();
    }

    private void ensureRing(int requiredCapacityBytes) {
        if (ring != null) return;
        alignment = resolveUniformOffsetAlignment();
        capacityBytes = Mth.smallestEncompassingPowerOfTwo(align(requiredCapacityBytes, alignment));
        if (DEBUG_UNIFORMS) {
            DebugLog.error("[CombatantRHI] uniform ring alignment=" + alignment + " capacity=" + capacityBytes
                    + " backend=" + RenderSystem.getDevice().getDeviceInfo().backendName());
        }
        ring = new MappableRingBuffer(() -> "Combatant - Unified Uniform Ring", GPU_BUFFER_USAGE_MAP_WRITE, capacityBytes);
        stats.ringCapacityBytes(capacityBytes);
    }

    private void ensureCapacityFor(int blockSize) {
        if (cursorBytes + blockSize <= capacityBytes) return;

        int required = cursorBytes + blockSize;
        int newCapacity = Mth.smallestEncompassingPowerOfTwo(Math.max(capacityBytes * 2, required));
        oldRings.add(ring);
        capacityBytes = newCapacity;
        cursorBytes = 0;
        ring = new MappableRingBuffer(() -> "Combatant - Unified Uniform Ring", GPU_BUFFER_USAGE_MAP_WRITE, capacityBytes);
        stats.ringGrow();
        stats.ringCapacityBytes(capacityBytes);
    }

    private GpuBuffer blockingBuffer() {
        stats.blockingBufferRequest();
        return ring.currentBuffer();
    }

    private int aligned(int size) {
        ensureAlignment();
        return align(size, alignment);
    }

    private void ensureAlignment() {
        if (alignment <= 0) {
            alignment = resolveUniformOffsetAlignment();
        }
    }

    private static int resolveUniformOffsetAlignment() {
        int value = RenderSystem.getDevice()
                .getDeviceInfo()
                .limits()
                .minUniformOffsetAlignment();
        if (value <= 0) {
            throw new IllegalStateException("GPU reported invalid minUniformOffsetAlignment=" + value);
        }
        return value;
    }

    private void validateUniformSlice(String name, GpuBufferSlice slice, int blockSize) {
        if (slice == null) {
            throw new IllegalStateException("Uniform '" + name + "' produced null slice");
        }
        if (slice.offset() % alignment != 0L) {
            throw new IllegalStateException("Uniform '" + name + "' slice offset is not aligned: offset="
                    + slice.offset() + ", alignment=" + alignment + ", length=" + slice.length());
        }
        if (slice.length() != blockSize) {
            throw new IllegalStateException("Uniform '" + name + "' slice length mismatch: length="
                    + slice.length() + ", blockSize=" + blockSize);
        }
    }

    @Override
    public void close() {
        for (UniformStream stream : streams.values()) {
            if (stream.cachedBuffer != null) {
                stream.cachedBuffer.close();
                stream.cachedBuffer = null;
                stream.cachedSlice = null;
            }
        }
        streams.clear();
        if (ring != null) {
            ring.close();
            ring = null;
        }
        for (MappableRingBuffer oldRing : oldRings) {
            oldRing.close();
        }
        oldRings.clear();
        cursorBytes = 0;
        capacityBytes = 0;
        usedThisFrame = false;
    }

    public interface UniformWriter {
        void write(ByteBuffer buffer);
    }

    private static final class UniformStream {
        private final String name;
        private int blockSize;
        private boolean usedThisFrame;
        private GpuBufferSlice current;
        private GpuBuffer cachedBuffer;
        private GpuBufferSlice cachedSlice;
        private long lastCachedWriteFrame = Long.MIN_VALUE;

        private UniformStream(String name, int blockSize) {
            this.name = name;
            this.blockSize = blockSize;
        }
    }
}
