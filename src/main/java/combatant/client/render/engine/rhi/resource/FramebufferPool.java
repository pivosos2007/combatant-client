/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.resource;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;

import java.util.*;

/**
 * Central framebuffer pool.
 * Persistent targets are looked up by logical name and resized on demand.
 * Temporary targets are borrowed/released by descriptor and destroyed on resize/reload/shutdown.
 */
public final class FramebufferPool implements AutoCloseable {
    private static final int MAX_IDLE_TRANSIENT_TARGETS = Math.max(
            4, Integer.getInteger("combatant.rhi.transientTargets", 64)
    );
    private static final long MAX_IDLE_TRANSIENT_BYTES = Math.max(
            16L * 1024L * 1024L,
            Long.getLong("combatant.rhi.transientBytes", 256L * 1024L * 1024L)
    );
    private final Map<String, TextureTarget> persistent = new HashMap<>();
    private final Map<String, Queue<TextureTarget>> temporary = new HashMap<>();
    private final Map<String, FrameTransient> frameTransient = new HashMap<>();
    private final Map<String, Queue<TextureTarget>> availableTransient = new HashMap<>();
    private final ArrayDeque<TextureTarget> transientLru = new ArrayDeque<>();
    private final Map<TextureTarget, String> availableTransientKeys = new IdentityHashMap<>();
    private final Map<TextureTarget, Long> transientBytes = new IdentityHashMap<>();
    private final ResourceLeakTracker leakTracker;
    private final Map<TextureTarget, String> borrowedKeys = new IdentityHashMap<>();

    private long borrows;
    private long releases;
    private long creates;
    private long resizes;
    private long invalidations;
    private long transientAcquires;
    private long transientReuses;
    private long transientReleases;
    private int peakFrameTransients;
    private long transientEvictions;
    private long idleTransientBytes;

    public FramebufferPool(ResourceLeakTracker leakTracker) {
        this.leakTracker = leakTracker;
    }

    /**
     * Compatibility constructor for legacy tests/tools.
     */
    public FramebufferPool() {
        this(new ResourceLeakTracker());
    }

    public TextureTarget persistent(String name, int width, int height, boolean depth, String owner) {
        return persistent(FramebufferDescriptor.persistent(name, width, height, depth, owner));
    }

    public TextureTarget persistent(FramebufferDescriptor descriptor) {
        String key = descriptor.poolKey();
        TextureTarget fb = persistent.get(key);
        if (fb == null) {
            fb = new TextureTarget(descriptor.name(), descriptor.width(), descriptor.height(), descriptor.depth(), GpuFormat.RGBA8_UNORM);
            persistent.put(key, fb);
            leakTracker.register(fb);
            creates++;
            return fb;
        }
        if (fb.width != descriptor.width() || fb.height != descriptor.height()) {
            fb.resize(descriptor.width(), descriptor.height());
            resizes++;
        }
        return fb;
    }

    public TextureTarget borrow(String name, int width, int height, boolean depth) {
        return borrowTemporary(FramebufferDescriptor.temporary(name, width, height, depth, "legacy"));
    }

    public TextureTarget borrowTemporary(String name, int width, int height, boolean depth, String owner) {
        return borrowTemporary(FramebufferDescriptor.temporary(name, width, height, depth, owner));
    }

    public TextureTarget borrowTemporary(FramebufferDescriptor descriptor) {
        String key = descriptor.tempKey();
        Queue<TextureTarget> queue = temporary.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        TextureTarget fb = queue.poll();
        if (fb == null) {
            fb = new TextureTarget(descriptor.name(), descriptor.width(), descriptor.height(), descriptor.depth(), GpuFormat.RGBA8_UNORM);
            leakTracker.register(fb);
            creates++;
        } else if (fb.width != descriptor.width() || fb.height != descriptor.height()) {
            fb.resize(descriptor.width(), descriptor.height());
            resizes++;
        }
        borrowedKeys.put(fb, key);
        borrows++;
        return fb;
    }

    public void release(TextureTarget framebuffer) {
        releaseTemporary(framebuffer, "legacy");
    }

    public void releaseTemporary(TextureTarget framebuffer, String owner) {
        if (framebuffer == null) return;
        String key = borrowedKeys.remove(framebuffer);
        if (key == null) {
            key = "unknown|" + framebuffer.width + "x" + framebuffer.height + "|" + (owner == null ? "legacy" : owner);
        }
        temporary.computeIfAbsent(key, ignored -> new ArrayDeque<>()).add(framebuffer);
        releases++;
    }

    public void beginFrame() {
        // Recover safely if an exceptional presentation path skipped the normal frame tail.
        releaseFrameTransients();
        transientAcquires = 0L;
        transientReuses = 0L;
        transientReleases = 0L;
        peakFrameTransients = frameTransient.size();
    }

    /**
     * Acquires a target whose contents are live through the current frame. Repeated acquisition
     * of the same logical allocation is free. Physical allocations are matched only by their
     * compatibility descriptor and can serve a different logical role in a later frame.
     */
    public TextureTarget acquireFrameTransient(TransientTargetDescriptor descriptor) {
        if (descriptor == null) throw new IllegalArgumentException("descriptor");
        if (descriptor.lifetime() != TransientTargetDescriptor.Lifetime.FRAME) {
            throw new IllegalArgumentException("Frame pool requires FRAME lifetime: " + descriptor.logicalName());
        }
        if (descriptor.samples() != 1) {
            throw new IllegalArgumentException("TextureTarget transient pool supports single-sample targets only: "
                    + descriptor.logicalName() + " samples=" + descriptor.samples());
        }

        String logicalKey = descriptor.logicalKey();
        FrameTransient existing = frameTransient.get(logicalKey);
        if (existing != null) {
            if (!existing.descriptor.compatibilityKey().equals(descriptor.compatibilityKey())) {
                throw new IllegalStateException("Transient target descriptor changed while live: "
                        + logicalKey + " old=" + existing.descriptor.compatibilityKey()
                        + " new=" + descriptor.compatibilityKey());
            }
            return existing.target;
        }

        Queue<TextureTarget> compatible = availableTransient.computeIfAbsent(
                descriptor.compatibilityKey(), ignored -> new ArrayDeque<>()
        );
        TextureTarget target = compatible.poll();
        if (target == null) {
            target = new TextureTarget(
                    descriptor.logicalName(), descriptor.width(), descriptor.height(), descriptor.depth(),
                    descriptor.colorFormat()
            );
            leakTracker.register(target);
            creates++;
        } else {
            transientLru.remove(target);
            availableTransientKeys.remove(target);
            idleTransientBytes = Math.max(0L, idleTransientBytes - transientBytes.getOrDefault(target, 0L));
            transientReuses++;
        }
        transientBytes.putIfAbsent(target, estimateBytes(descriptor));
        frameTransient.put(logicalKey, new FrameTransient(descriptor, target));
        transientAcquires++;
        peakFrameTransients = Math.max(peakFrameTransients, frameTransient.size());
        return target;
    }

    /** Ends every FRAME lifetime after the backend has submitted the frame. */
    public void releaseFrameTransients() {
        if (frameTransient.isEmpty()) return;
        for (FrameTransient allocation : frameTransient.values()) {
            availableTransient.computeIfAbsent(
                    allocation.descriptor.compatibilityKey(), ignored -> new ArrayDeque<>()
            ).add(allocation.target);
            transientLru.addLast(allocation.target);
            availableTransientKeys.put(allocation.target, allocation.descriptor.compatibilityKey());
            idleTransientBytes += transientBytes.getOrDefault(
                    allocation.target, estimateBytes(allocation.descriptor)
            );
            transientReleases++;
        }
        frameTransient.clear();
        trimIdleTransients();
    }

    private void trimIdleTransients() {
        while (transientLru.size() > MAX_IDLE_TRANSIENT_TARGETS
                || idleTransientBytes > MAX_IDLE_TRANSIENT_BYTES) {
            TextureTarget target = transientLru.pollFirst();
            if (target == null) break;
            String key = availableTransientKeys.remove(target);
            Queue<TextureTarget> queue = key != null ? availableTransient.get(key) : null;
            if (queue != null) {
                queue.remove(target);
                if (queue.isEmpty()) availableTransient.remove(key);
            }
            long bytes = transientBytes.getOrDefault(target, 0L);
            idleTransientBytes = Math.max(0L, idleTransientBytes - bytes);
            transientBytes.remove(target);
            leakTracker.release(target);
            target.destroyBuffers();
            transientEvictions++;
        }
    }

    private static long estimateBytes(TransientTargetDescriptor descriptor) {
        long pixels = (long) descriptor.width() * descriptor.height() * descriptor.samples();
        long color = pixels * Math.max(1, descriptor.colorFormat().blockSize());
        long depth = descriptor.depth() ? pixels * 4L : 0L;
        return color + depth;
    }

    public void invalidate() {
        invalidations++;
        closeTemporary();
        closePersistent();
    }

    public void invalidateSizeDependent() {
        invalidations++;
        closeTemporary();
        closePersistent();
    }

    public void closeTemporary() {
        for (Queue<TextureTarget> queue : temporary.values()) {
            while (!queue.isEmpty()) {
                TextureTarget fb = queue.poll();
                leakTracker.release(fb);
                fb.destroyBuffers();
            }
        }
        temporary.clear();
        borrowedKeys.clear();
        closeFrameTransients();
    }

    private void closeFrameTransients() {
        for (FrameTransient allocation : frameTransient.values()) {
            leakTracker.release(allocation.target);
            allocation.target.destroyBuffers();
        }
        frameTransient.clear();
        for (Queue<TextureTarget> queue : availableTransient.values()) {
            while (!queue.isEmpty()) {
                TextureTarget target = queue.poll();
                leakTracker.release(target);
                target.destroyBuffers();
            }
        }
        availableTransient.clear();
        transientLru.clear();
        availableTransientKeys.clear();
        transientBytes.clear();
        idleTransientBytes = 0L;
    }

    public void closePersistent() {
        for (TextureTarget fb : persistent.values()) {
            leakTracker.release(fb);
            fb.destroyBuffers();
        }
        persistent.clear();
    }

    @Override
    public void close() {
        closeTemporary();
        closePersistent();
    }

    public int persistentCount() {
        return persistent.size();
    }

    public int temporaryCount() {
        int total = 0;
        for (Queue<TextureTarget> queue : temporary.values()) total += queue.size();
        for (Queue<TextureTarget> queue : availableTransient.values()) total += queue.size();
        total += frameTransient.size();
        return total;
    }

    public int activeFrameTransientCount() {
        return frameTransient.size();
    }

    public long transientAcquires() {
        return transientAcquires;
    }

    public long transientReuses() {
        return transientReuses;
    }

    public long transientReleases() {
        return transientReleases;
    }

    public int peakFrameTransients() {
        return peakFrameTransients;
    }

    public long transientEvictions() {
        return transientEvictions;
    }

    public long idleTransientBytes() {
        return idleTransientBytes;
    }

    public long borrows() {
        return borrows;
    }

    public long releases() {
        return releases;
    }

    public long creates() {
        return creates;
    }

    public long resizes() {
        return resizes;
    }

    public long invalidations() {
        return invalidations;
    }

    private record FrameTransient(TransientTargetDescriptor descriptor, TextureTarget target) {
    }
}
