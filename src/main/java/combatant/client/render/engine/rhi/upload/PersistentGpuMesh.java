/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.upload;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import combatant.client.render.engine.rhi.GpuMeshHandle;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Owns the GPU buffers behind one persistent mesh allocation.
 *
 * <p>The contained {@link GpuMeshHandle} is only a draw view. Closing that handle is intentionally
 * a no-op because dynamic and persistent mesh lifetimes are different. Closing this owner releases
 * the underlying Mojang {@link GpuBuffer}s on both OpenGL and Vulkan backends.</p>
 */
public final class PersistentGpuMesh implements AutoCloseable {
    private final GpuBuffer vertexBuffer;
    private final GpuBuffer indexBuffer;
    private final GpuMeshHandle handle;
    private final Consumer<PersistentGpuMesh> onClose;
    private boolean closed;

    PersistentGpuMesh(GpuBuffer vertexBuffer, GpuBuffer indexBuffer, GpuMeshHandle handle,
                      Consumer<PersistentGpuMesh> onClose) {
        this.vertexBuffer = Objects.requireNonNull(vertexBuffer, "vertexBuffer");
        this.indexBuffer = Objects.requireNonNull(indexBuffer, "indexBuffer");
        this.handle = Objects.requireNonNull(handle, "handle");
        this.onClose = onClose != null ? onClose : ignored -> {};
    }

    public GpuMeshHandle handle() {
        if (closed) throw new IllegalStateException("Persistent GPU mesh is closed");
        return handle;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (!vertexBuffer.isClosed()) vertexBuffer.close();
        if (!indexBuffer.isClosed()) indexBuffer.close();
        onClose.accept(this);
    }
}
