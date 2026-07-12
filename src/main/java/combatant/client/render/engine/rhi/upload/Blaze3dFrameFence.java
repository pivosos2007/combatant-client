/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.upload;

import com.mojang.blaze3d.buffers.GpuFence;

/**
 * Ref-counted frame fence shared by all dynamic mesh arenas retired at the same frame boundary.
 * <p>
 * The important part is that the backend creates one GPU fence per presented frame, not one fence per
 * allocation/batch. Arenas hold references to this wrapper and release them when their memory is reusable.
 */
final class Blaze3dFrameFence implements AutoCloseable {
    private GpuFence fence;
    private int references = 1;
    private boolean completed;

    Blaze3dFrameFence(GpuFence fence) {
        if (fence == null) throw new IllegalArgumentException("fence");
        this.fence = fence;
    }

    Blaze3dFrameFence retain() {
        if (fence == null) throw new IllegalStateException("Cannot retain closed frame fence");
        references++;
        return this;
    }

    boolean isCompleted() {
        if (completed) return true;
        if (fence == null) return true;
        completed = fence.awaitCompletion(0L);
        return completed;
    }

    void release() {
        if (references <= 0) return;
        references--;
        if (references == 0) close();
    }

    @Override
    public void close() {
        if (fence != null) {
            fence.close();
            fence = null;
        }
        completed = true;
        references = 0;
    }
}
