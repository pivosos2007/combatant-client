/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import org.jetbrains.annotations.Nullable;

/**
 * Compatibility tombstone for the pre-frame-graph deferred texture owner.
 *
 * <p>Production deferred resources are now materialized exclusively by
 * FrameGraphPhysicalResourcePool. Keeping this type temporarily avoids source-level breakage for
 * out-of-tree integrations while making accidental reintroduction of the duplicate owner fail
 * immediately instead of silently allocating a second texture.</p>
 */
@Deprecated(forRemoval = true)
public final class DeferredResourceAllocator implements AutoCloseable {
    public void beginFrame(long frameId) {
        // No ownership remains here.
    }

    public Allocation acquire(DeferredResource resource,
                              int renderWidth,
                              int renderHeight,
                              int outputWidth,
                              int outputHeight,
                              int sceneSamples,
                              CombatantRhi rhi,
                              DeferredRuntimeConfig.Snapshot settings) {
        throw removed(resource);
    }

    public Allocation acquire(DeferredResource resource,
                              int fullWidth,
                              int fullHeight,
                              int sceneSamples,
                              CombatantRhi rhi,
                              DeferredRuntimeConfig.Snapshot settings) {
        throw removed(resource);
    }

    public @Nullable Allocation current(DeferredResource resource) {
        return null;
    }

    public void reset() {
        // No ownership remains here.
    }

    @Override
    public void close() {
        // No ownership remains here.
    }

    private static IllegalStateException removed(DeferredResource resource) {
        return new IllegalStateException("DeferredResourceAllocator no longer owns physical resources; declare '"
                + (resource == null ? "<null>" : resource.key().name()) + "' in the frame graph instead");
    }

    /** Source-compatibility shell; no production allocation can be created. */
    @Deprecated(forRemoval = true)
    public static final class Allocation implements AutoCloseable {
        private Allocation() {
        }

        public GpuTextureView view() { throw unavailable(); }
        public @Nullable RhiStorageImage storageImage() { throw unavailable(); }
        public int width() { throw unavailable(); }
        public int height() { throw unavailable(); }
        public int samples() { throw unavailable(); }
        public int mipLevels() { throw unavailable(); }
        public boolean valid() { return false; }
        public boolean validForEpoch(long epoch) { return false; }
        public void markValid() { throw unavailable(); }
        public void markValid(long epoch) { throw unavailable(); }

        @Override
        public void close() {
        }

        private static IllegalStateException unavailable() {
            return new IllegalStateException("DeferredResourceAllocator allocations no longer exist");
        }
    }
}
