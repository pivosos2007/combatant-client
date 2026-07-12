/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.backend.vulkan.msaa;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import org.lwjgl.vulkan.VkRenderingAttachmentInfo;
import org.lwjgl.vulkan.VkRenderingInfo;
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.render.engine.rhi.backend.vulkan.util.VulkanRenderStateBridge;

import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.vulkan.VK12.*;

/**
 * Connects Combatant's multisampled world target to Mojang's single-sampled main target.
 *
 * <p>The resolve is described on the same {@link VkRenderingAttachmentInfo} objects that Mojang
 * submits to dynamic rendering. No standalone resolve command is recorded, so color and depth
 * complete together at {@code vkCmdEndRenderingKHR} and remain ordered with subsequent passes.</p>
 */
public enum VulkanMsaaResolveBridge {
    ;

    private static final Map<GpuTexture, ResolveTarget> TARGETS = new IdentityHashMap<>();
    private static final Map<GpuTexture, ResolveTarget> SNAPSHOT_PREVIOUS = new IdentityHashMap<>();
    private static final Set<ResolveTarget> PENDING = new LinkedHashSet<>();

    public static void beginRenderPass() {
        PENDING.clear();
    }

    public static void finishRenderPass() {
        for (ResolveTarget target : PENDING) {
            target.resolved = true;
        }
        PENDING.clear();
    }

    public static void abortRenderPass() {
        PENDING.clear();
    }

    public static boolean snapshotActive() {
        return !SNAPSHOT_PREVIOUS.isEmpty();
    }

    public static void prepare(RenderTarget source, RenderTarget destination) {
        prepare(source, destination, true, true);
    }

    public static void prepare(RenderTarget source, RenderTarget destination, boolean color, boolean depth) {
        if (source == null || destination == null) {
            throw new IllegalArgumentException("MSAA resolve targets must not be null");
        }
        remove(source);
        if (color) {
            register(source.getColorTexture(), destination.getColorTextureView(), Aspect.COLOR, false);
        }
        if (depth && (source.getDepthTexture() != null || destination.getDepthTexture() != null)) {
            if (source.getDepthTexture() == null || destination.getDepthTextureView() == null) {
                remove(source);
                throw new IllegalStateException("MSAA depth resolve requires both source and destination depth attachments");
            }
            register(source.getDepthTexture(), destination.getDepthTextureView(), Aspect.DEPTH, false);
        }
    }

    public static boolean complete(RenderTarget source, RenderTarget destination, boolean color, boolean depth) {
        if (source == null || destination == null) return false;
        ResolveTarget colorTarget = target(source.getColorTexture());
        ResolveTarget depthTarget = target(source.getDepthTexture());
        boolean ok = (!color || colorTarget != null
                && colorTarget.view.texture() == destination.getColorTexture()
                && colorTarget.resolved)
                && (!depth || depthTarget != null
                && depthTarget.view.texture() == destination.getDepthTexture()
                && depthTarget.resolved);
        remove(source);
        return ok;
    }

    public static boolean matchesPreparedTarget(RenderTarget source, RenderTarget destination,
                                                boolean color, boolean depth) {
        if (source == null || destination == null) return false;
        ResolveTarget colorTarget = target(source.getColorTexture());
        ResolveTarget depthTarget = target(source.getDepthTexture());
        return (!color || colorTarget != null && colorTarget.view.texture() == destination.getColorTexture())
                && (!depth || depthTarget != null && depthTarget.view.texture() == destination.getDepthTexture());
    }

    public static void abandon(RenderTarget source) {
        remove(source);
    }

    public static void beginDepthSnapshot(GpuTextureView source, GpuTextureView destination) {
        beginSnapshot(source, destination, Aspect.DEPTH);
    }

    public static void beginColorSnapshot(GpuTextureView source, GpuTextureView destination) {
        beginSnapshot(source, destination, Aspect.COLOR);
    }

    private static void beginSnapshot(GpuTextureView source, GpuTextureView destination, Aspect aspect) {
        if (source == null || source.texture() == null) {
            throw new IllegalArgumentException("MSAA " + aspect + " snapshot source is null");
        }
        GpuTexture texture = source.texture();
        if (SNAPSHOT_PREVIOUS.containsKey(texture)) {
            throw new IllegalStateException("Nested MSAA snapshot resolve for " + texture.getLabel());
        }
        ResolveTarget previous = TARGETS.remove(texture);
        SNAPSHOT_PREVIOUS.put(texture, previous);
        try {
            register(texture, destination, aspect, true);
            validate(source, destination);
        } catch (Throwable t) {
            restoreSnapshotTarget(texture);
            throw t;
        }
    }

    public static boolean completeDepthSnapshot(GpuTextureView source, GpuTextureView destination) {
        return completeSnapshot(source, destination, Aspect.DEPTH);
    }

    public static boolean completeColorSnapshot(GpuTextureView source, GpuTextureView destination) {
        return completeSnapshot(source, destination, Aspect.COLOR);
    }

    private static boolean completeSnapshot(GpuTextureView source, GpuTextureView destination, Aspect aspect) {
        if (source == null || source.texture() == null || destination == null) return false;
        GpuTexture texture = source.texture();
        ResolveTarget target = TARGETS.get(texture);
        boolean ok = target != null
                && target.aspect == aspect
                && target.view.texture() == destination.texture()
                && target.resolved;
        restoreSnapshotTarget(texture);
        return ok;
    }

    public static void abortDepthSnapshot(GpuTextureView source) {
        abortSnapshot(source);
    }

    public static void abortColorSnapshot(GpuTextureView source) {
        abortSnapshot(source);
    }

    private static void abortSnapshot(GpuTextureView source) {
        if (source != null && source.texture() != null) {
            restoreSnapshotTarget(source.texture());
        }
    }

    public static void configure(RenderPassDescriptor descriptor, VkRenderingInfo renderingInfo) {
        if (descriptor == null || renderingInfo == null || VulkanRenderStateBridge.currentRenderPassSamples() <= 1) {
            return;
        }

        List<RenderPassDescriptor.Attachment<java.util.Optional<org.joml.Vector4fc>>> colors = descriptor.colorAttachments();
        VkRenderingAttachmentInfo.Buffer vkColors = renderingInfo.pColorAttachments();
        if (colors != null && vkColors != null) {
            if (vkColors.remaining() != colors.size()) {
                throw new IllegalStateException("Vulkan color attachment count changed before MSAA resolve configuration");
            }
            for (int i = 0; i < colors.size(); i++) {
                RenderPassDescriptor.Attachment<?> attachment = colors.get(i);
                if (attachment == null) continue;
                ResolveTarget target = target(attachment.textureView().texture());
                if (target == null) continue;
                if (!target.snapshot) continue;
                if (target.aspect != Aspect.COLOR) {
                    throw new IllegalStateException("Depth resolve target registered for a color attachment");
                }
                validate(attachment.textureView(), target.view);
                vkColors.get(i)
                        .resolveMode(VK_RESOLVE_MODE_AVERAGE_BIT)
                        .resolveImageView(target.vulkanView().vkImageView())
                        .resolveImageLayout(VK_IMAGE_LAYOUT_GENERAL);
                PENDING.add(target);
            }
        }

        RenderPassDescriptor.Attachment<?> depth = descriptor.depthAttachment();
        if (depth == null) return;
        ResolveTarget target = target(depth.textureView().texture());
        if (target == null) return;
        if (!target.snapshot) return;
        if (target.aspect != Aspect.DEPTH) {
            throw new IllegalStateException("Color resolve target registered for a depth attachment");
        }
        VkRenderingAttachmentInfo vkDepth = renderingInfo.pDepthAttachment();
        if (vkDepth == null) {
            throw new IllegalStateException("Mojang did not create VkRenderingAttachmentInfo for the depth attachment");
        }
        if (renderingInfo.pStencilAttachment() != null && !VulkanRenderStateBridge.independentResolveNone()) {
            throw new IllegalStateException("Device cannot resolve MSAA depth independently while a stencil attachment is active");
        }
        validate(depth.textureView(), target.view);
        vkDepth
                .resolveMode(VulkanRenderStateBridge.depthResolveMode())
                .resolveImageView(target.vulkanView().vkImageView())
                .resolveImageLayout(VK_IMAGE_LAYOUT_GENERAL);
        PENDING.add(target);
    }

    private static void register(GpuTexture source, GpuTextureView destination, Aspect aspect, boolean snapshot) {
        if (source == null || destination == null) {
            throw new IllegalStateException("Missing " + aspect + " resolve attachment");
        }
        if (!(source instanceof IMsaaTexture msaa) || !msaa.combatant$isMsaa()) {
            throw new IllegalStateException("Resolve source is not multisampled: " + source.getLabel());
        }
        validateTexturePair(source, destination);
        TARGETS.put(source, new ResolveTarget(destination, aspect, snapshot));
    }

    private static void validate(GpuTextureView source, GpuTextureView destination) {
        if (source == null || source.isClosed()) {
            throw new IllegalStateException("MSAA source view is null or closed");
        }
        validateTexturePair(source.texture(), destination);
        if (source.getWidth(0) != destination.getWidth(0) || source.getHeight(0) != destination.getHeight(0)) {
            throw new IllegalStateException("MSAA resolve view size mismatch: source="
                    + source.getWidth(0) + "x" + source.getHeight(0) + ", destination="
                    + destination.getWidth(0) + "x" + destination.getHeight(0));
        }
    }

    private static void validateTexturePair(GpuTexture source, GpuTextureView destination) {
        if (source == null || source.isClosed() || destination == null || destination.isClosed()
                || destination.texture() == null || destination.texture().isClosed()) {
            throw new IllegalStateException("MSAA resolve contains a null or closed texture/view");
        }
        if (!(destination instanceof VulkanGpuTextureView)) {
            throw new IllegalStateException("MSAA resolve destination is not a Vulkan texture view");
        }
        if (source == destination.texture()) {
            throw new IllegalStateException("MSAA source and resolve destination refer to the same texture");
        }
        if (source.getFormat() != destination.texture().getFormat()) {
            throw new IllegalStateException("MSAA resolve format mismatch: source=" + source.getFormat()
                    + ", destination=" + destination.texture().getFormat());
        }
        int destinationSamples = destination.texture() instanceof IMsaaTexture msaa
                ? msaa.combatant$getSamples()
                : 1;
        if (destinationSamples != 1) {
            throw new IllegalStateException("MSAA resolve destination must be single-sampled, got " + destinationSamples + "x");
        }
    }

    private static ResolveTarget target(GpuTexture texture) {
        return texture == null ? null : TARGETS.get(texture);
    }

    private static void remove(RenderTarget source) {
        if (source == null) return;
        if (source.getColorTexture() != null) PENDING.remove(TARGETS.remove(source.getColorTexture()));
        if (source.getDepthTexture() != null) PENDING.remove(TARGETS.remove(source.getDepthTexture()));
    }

    private static void restoreSnapshotTarget(GpuTexture texture) {
        ResolveTarget temporary = TARGETS.remove(texture);
        if (temporary != null) PENDING.remove(temporary);
        if (!SNAPSHOT_PREVIOUS.containsKey(texture)) return;
        ResolveTarget previous = SNAPSHOT_PREVIOUS.remove(texture);
        if (previous != null) TARGETS.put(texture, previous);
    }

    private enum Aspect {
        COLOR,
        DEPTH
    }

    private static final class ResolveTarget {
        private final GpuTextureView view;
        private final Aspect aspect;
        private final boolean snapshot;
        private boolean resolved;

        private ResolveTarget(GpuTextureView view, Aspect aspect, boolean snapshot) {
            this.view = view;
            this.aspect = aspect;
            this.snapshot = snapshot;
        }

        private VulkanGpuTextureView vulkanView() {
            return (VulkanGpuTextureView) view;
        }
    }
}
