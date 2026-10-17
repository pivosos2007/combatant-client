/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.resource;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.render.engine.framegraph.FrameGraphPhysicalResourceDescriptor;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.RhiStorageVolume;

/** One graph-owned physical allocation, potentially backing several aliased logical resources. */
public final class FrameGraphPhysicalResource implements AutoCloseable {
    private final int allocationId;
    private final FrameGraphPhysicalResourceDescriptor descriptor;
    private final GpuTextureView textureView;
    private final RhiStorageImage storageImage;
    private final RhiStorageBuffer storageBuffer;
    private final RhiStorageVolume storageVolume;
    private final AutoCloseable owned;
    private boolean closed;

    FrameGraphPhysicalResource(int allocationId,
                               FrameGraphPhysicalResourceDescriptor descriptor,
                               GpuTextureView textureView,
                               RhiStorageImage storageImage,
                               RhiStorageBuffer storageBuffer,
                               RhiStorageVolume storageVolume,
                               AutoCloseable owned) {
        this.allocationId = allocationId;
        this.descriptor = descriptor;
        this.textureView = textureView;
        this.storageImage = storageImage;
        this.storageBuffer = storageBuffer;
        this.storageVolume = storageVolume;
        this.owned = owned;
    }

    public int allocationId() {
        return allocationId;
    }

    public FrameGraphPhysicalResourceDescriptor descriptor() {
        return descriptor;
    }

    public GpuTextureView textureView() {
        requireOpen();
        if (textureView == null) throw new IllegalStateException("Physical resource is not a texture");
        return textureView;
    }

    public RhiStorageImage storageImage() {
        requireOpen();
        return storageImage;
    }

    public RhiStorageBuffer storageBuffer() {
        requireOpen();
        return storageBuffer;
    }

    public RhiStorageVolume storageVolume() {
        requireOpen();
        return storageVolume;
    }

    public boolean isClosed() {
        return closed;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Frame-graph physical allocation is closed: " + allocationId);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (owned == null) return;
        try {
            owned.close();
        } catch (RuntimeException | Error t) {
            throw t;
        } catch (Exception t) {
            throw new IllegalStateException("Failed to close frame-graph allocation " + allocationId, t);
        }
    }
}
