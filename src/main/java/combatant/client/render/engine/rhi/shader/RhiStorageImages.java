/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

/** Creates storage-capable images through Mojang's active GpuDevice instead of a parallel texture owner. */
public final class RhiStorageImages {
    private RhiStorageImages() {}

    public static RhiStorageImage create(StorageImageDescriptor descriptor) {
        if (descriptor == null) throw new IllegalArgumentException("descriptor");
        RenderSystem.assertOnRenderThread();
        int usage = GpuTexture.USAGE_COPY_DST
                | GpuTexture.USAGE_COPY_SRC
                | RhiTextureUsage.STORAGE_IMAGE;
        if (descriptor.sampled()) usage |= GpuTexture.USAGE_TEXTURE_BINDING;
        if (descriptor.renderAttachment()) usage |= GpuTexture.USAGE_RENDER_ATTACHMENT;

        GpuTexture texture = RenderSystem.getDevice().createTexture(
                descriptor.label(), usage, descriptor.format(), descriptor.width(), descriptor.height(), 1, 1);
        GpuTextureView view = null;
        try {
            view = RenderSystem.getDevice().createTextureView(texture);
            return new OwnedStorageImage(descriptor, texture, view);
        } catch (RuntimeException | Error t) {
            if (view != null) view.close();
            texture.close();
            throw t;
        }
    }

    private static final class OwnedStorageImage implements RhiStorageImage {
        private final StorageImageDescriptor descriptor;
        private final GpuTexture texture;
        private final GpuTextureView view;
        private boolean closed;

        private OwnedStorageImage(StorageImageDescriptor descriptor, GpuTexture texture, GpuTextureView view) {
            this.descriptor = descriptor;
            this.texture = texture;
            this.view = view;
        }

        @Override public StorageImageDescriptor descriptor() { return descriptor; }

        @Override
        public GpuTextureView view() {
            if (closed) throw new IllegalStateException("Storage image is closed: " + descriptor.label());
            return view;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            view.close();
            texture.close();
        }
    }
}
