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
    private final Map<String, TextureTarget> persistent = new HashMap<>();
    private final Map<String, Queue<TextureTarget>> temporary = new HashMap<>();
    private final ResourceLeakTracker leakTracker;
    private final Map<TextureTarget, String> borrowedKeys = new IdentityHashMap<>();

    private long borrows;
    private long releases;
    private long creates;
    private long resizes;
    private long invalidations;

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
        return total;
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
}
