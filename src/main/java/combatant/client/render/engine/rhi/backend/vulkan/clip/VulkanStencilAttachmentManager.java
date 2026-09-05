/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.backend.vulkan.clip;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;
import combatant.client.render.engine.rhi.backend.vulkan.util.VulkanRenderStateBridge;
import combatant.client.util.logging.DebugLog;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Per-render-target S8 attachments for Vulkan dynamic rendering.
 *
 * <p>Mojang's Vulkan RenderPassDescriptor has no stencil slot. We attach one in the Vulkan command
 * encoder mixin and keep it keyed by the primary color/depth texture identity.</p>
 */
public final class VulkanStencilAttachmentManager implements AutoCloseable {
    private final Map<GpuTexture, Attachment> attachments = new IdentityHashMap<>();
    private boolean lastAttachmentClear;

    private static @Nullable GpuTextureView primaryTarget(RenderPassDescriptor descriptor) {
        if (descriptor.colorAttachments() != null) {
            for (RenderPassDescriptor.Attachment<?> attachment : descriptor.colorAttachments()) {
                if (attachment != null && attachment.textureView() != null) return attachment.textureView();
            }
        }
        if (descriptor.depthAttachment() != null) return descriptor.depthAttachment().textureView();
        return null;
    }

    private static Attachment createAttachment(String ownerLabel, int width, int height, int samples) {
        String label = "combatant-vk-stencil:" + (ownerLabel == null ? "unnamed" : ownerLabel);
        GpuTexture texture = null;
        GpuTextureView view = null;
        try {
            texture = VulkanRenderStateBridge.withTextureSamples(samples, () ->
                    RenderSystem.getDevice().createTexture(
                            label,
                            GpuTexture.USAGE_RENDER_ATTACHMENT,
                            GpuFormat.S8_UINT,
                            width,
                            height,
                            1,
                            1
                    )
            );
            view = RenderSystem.getDevice().createTextureView(texture);
            DebugLog.stencilOnChange("shapeclip.vulkan.stencil.alloc", label + "|" + width + "x" + height + "|" + samples,
                    "[ShapeClip/Vulkan] UI clip attachment format=S8_UINT label=%s size=%dx%d samples=%d",
                    label, width, height, samples);
            return new Attachment(texture, view, width, height, samples);
        } catch (RuntimeException | Error failure) {
            if (view != null && !view.isClosed()) view.close();
            if (texture != null && !texture.isClosed()) texture.close();
            throw failure;
        }
    }

    public @Nullable GpuTextureView attachmentFor(RenderPassDescriptor descriptor) {
        if (descriptor == null) return null;
        pruneClosedAttachments();
        GpuTextureView target = primaryTarget(descriptor);
        if (target == null || target.texture() == null) return null;

        GpuTexture key = target.texture();
        int width = Math.max(1, target.getWidth(0));
        int height = Math.max(1, target.getHeight(0));
        int samples = VulkanRenderStateBridge.samplesFor(descriptor);

        boolean clearRequested = VulkanRenderStateBridge.consumeStencilClearRequested();
        Attachment attachment = attachments.get(key);
        if (attachment == null || attachment.width != width || attachment.height != height || attachment.samples != samples || attachment.closed()) {
            if (attachment != null) attachment.close();
            try {
                attachment = createAttachment(key.getLabel(), width, height, samples);
                attachments.put(key, attachment);
                lastAttachmentClear = true;
            } catch (Throwable t) {
                attachments.remove(key);
                lastAttachmentClear = false;
                VulkanRenderStateBridge.disableStencilAfterFailure("attachment allocation " + width + "x" + height + " samples=" + samples, t);
                return null;
            }
        } else {
            lastAttachmentClear = clearRequested;
        }
        return attachment.view;
    }

    /**
     * Resizable render targets replace their color texture identity. Retaining the old identity here
     * also retained its private S8 image until backend shutdown, turning size jitter into a GPU leak.
     */
    private void pruneClosedAttachments() {
        Iterator<Map.Entry<GpuTexture, Attachment>> iterator = attachments.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<GpuTexture, Attachment> entry = iterator.next();
            GpuTexture owner = entry.getKey();
            Attachment attachment = entry.getValue();
            if (owner == null || owner.isClosed() || attachment == null || attachment.closed()) {
                if (attachment != null) attachment.close();
                iterator.remove();
            }
        }
    }

    public boolean consumeLastAttachmentClear() {
        boolean clear = lastAttachmentClear;
        lastAttachmentClear = false;
        return clear;
    }

    @Override
    public void close() {
        for (Attachment attachment : attachments.values()) {
            attachment.close();
        }
        attachments.clear();
    }

    private record Attachment(GpuTexture texture, GpuTextureView view, int width, int height,
                              int samples) implements AutoCloseable {

        private boolean closed() {
            return texture == null || texture.isClosed() || view == null || view.isClosed();
        }

        @Override
        public void close() {
            if (view != null && !view.isClosed()) view.close();
            if (texture != null && !texture.isClosed()) texture.close();
        }
    }
}
