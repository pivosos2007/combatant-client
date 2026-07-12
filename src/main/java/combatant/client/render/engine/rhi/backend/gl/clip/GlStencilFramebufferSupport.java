/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.backend.gl.clip;

import com.mojang.blaze3d.opengl.FrameBufferAttachment;
import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.*;
import combatant.client.mixininterface.IGlBackendInfo;
import combatant.client.render.engine.msaa.MsaaTextureRegistry;
import combatant.client.render.engine.rhi.backend.gl.GlBackendAccess;
import combatant.client.util.logging.DebugLog;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * GL-only runtime stencil attachment/probe for Mojang framebuffers.
 *
 * <p>Minecraft 1.21.x framebuffers normally expose a color texture plus a DEPTH32 texture. Some GL
 * drivers reject adding a separate GL_STENCIL_INDEX8 renderbuffer to that color/depth combination
 * with GL_FRAMEBUFFER_UNSUPPORTED (0x8CDD). When that happens this backend switches to a real packed
 * depth-stencil renderbuffer for the active clip scope and restores the original attachments when
 * shape clipping is disabled.</p>
 */
final class GlStencilFramebufferSupport {
    /**
     * GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_SAMPLES_EXT is not a core framebuffer attachment pname and
     * Mesa/X11 reports it through KHR_debug as GL_INVALID_ENUM. Do not use attachment-level sample
     * queries. Texture sample detection is registry-first and then a guarded DSA texture-level query.
     */
    private static boolean textureSamplesDsaProbeDisabled;
    private final GlStencilShapeClipBackend owner;
    private final Map<Integer, Attachment> attachments = new HashMap<>();
    private boolean warnedUnsupported;
    private String lastFailure = "none";

    GlStencilFramebufferSupport(GlStencilShapeClipBackend owner) {
        this.owner = owner;
    }

    private static void restoreFramebufferBindings(int readFramebuffer, int drawFramebuffer) {
        // Mojang tracks read and draw FBOs separately. getFrameBuffer(GL_FRAMEBUFFER)
        // returns 0, so any helper that temporarily binds GL_FRAMEBUFFER must restore
        // GL_READ_FRAMEBUFFER and GL_DRAW_FRAMEBUFFER explicitly or the active render
        // pass silently falls back to the default FBO with no stencil attachment.
        if (readFramebuffer == drawFramebuffer) {
            GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, readFramebuffer);
            return;
        }
        GlStateManager._glBindFramebuffer(GlConst.GL_READ_FRAMEBUFFER, readFramebuffer);
        GlStateManager._glBindFramebuffer(GlConst.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
    }

    private static int framebufferFor(IGlBackendInfo backend, GlTextureView colorView, @Nullable GpuTextureView depthView) {
        FrameBufferAttachment depthAttachment = null;
        if (depthView != null) {
            if (!(depthView instanceof GlTextureView glDepthView)) {
                throw new IllegalArgumentException("depth view is not GlTextureView: " + depthView.getClass().getName());
            }
            depthAttachment = glDepthView;
        }
        return backend.combatant$frameBufferCache().getFbo(
                backend.combatant$directStateAccess(),
                List.of(colorView),
                depthAttachment
        );
    }

    private static int detectBoundFramebufferSamples() {
        int colorSamples = attachmentSamples(GL30C.GL_COLOR_ATTACHMENT0);
        int depthSamples = attachmentSamples(GL30C.GL_DEPTH_ATTACHMENT);
        int stencilSamples = attachmentSamples(GL30C.GL_STENCIL_ATTACHMENT);
        return Math.max(1, Math.max(colorSamples, Math.max(depthSamples, stencilSamples)));
    }

    private static int attachmentSamples(int attachmentPoint) {
        int type = GL30C.glGetFramebufferAttachmentParameteri(
                GL30C.GL_FRAMEBUFFER,
                attachmentPoint,
                GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
        );
        if (type == GL11C.GL_NONE) return 1;

        if (type == GL30C.GL_RENDERBUFFER) {
            int renderbuffer = GL30C.glGetFramebufferAttachmentParameteri(
                    GL30C.GL_FRAMEBUFFER,
                    attachmentPoint,
                    GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
            );
            return renderbufferSamples(renderbuffer);
        }

        if (type == GL11C.GL_TEXTURE) {
            int texture = GL30C.glGetFramebufferAttachmentParameteri(
                    GL30C.GL_FRAMEBUFFER,
                    attachmentPoint,
                    GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
            );
            return textureSamples(texture);
        }

        return 1;
    }

    private static int textureSamples(int texture) {
        if (texture == 0) return 1;

        int registeredSamples = MsaaTextureRegistry.getSamples(texture);
        if (registeredSamples > 1) {
            return registeredSamples;
        }

        if (textureSamplesDsaProbeDisabled) {
            return 1;
        }

        GLCapabilities capabilities = GL.getCapabilities();
        if (capabilities == null || (!capabilities.OpenGL45 && !capabilities.GL_ARB_direct_state_access)) {
            return 1;
        }

        try {
            clearGlErrorsSilently();
            int samples = capabilities.OpenGL45
                    ? GL45C.glGetTextureLevelParameteri(texture, 0, GL32C.GL_TEXTURE_SAMPLES)
                    : ARBDirectStateAccess.glGetTextureLevelParameteri(texture, 0, GL32C.GL_TEXTURE_SAMPLES);
            int error = GL11C.glGetError();
            if (error == GL11C.GL_NO_ERROR) {
                return Math.max(1, samples);
            }

            // Never keep probing a driver/path that rejects the target-free sample query. One
            // failed probe is enough; repeated probes are exactly what creates scary KHR_debug spam.
            textureSamplesDsaProbeDisabled = true;
        } catch (Throwable ignored) {
            textureSamplesDsaProbeDisabled = true;
        }
        clearGlErrorsSilently();
        return 1;
    }

    private static int renderbufferSamples(int renderbuffer) {
        if (renderbuffer == 0) return 1;
        int previousRenderbuffer = GL11C.glGetInteger(GL30C.GL_RENDERBUFFER_BINDING);
        try {
            GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, renderbuffer);
            int samples = GL30C.glGetRenderbufferParameteri(GL30C.GL_RENDERBUFFER, GL30C.GL_RENDERBUFFER_SAMPLES);
            return Math.max(1, samples);
        } catch (Throwable ignored) {
            clearGlErrorsSilently();
            return 1;
        } finally {
            GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, previousRenderbuffer);
        }
    }

    private static void clearGlErrorsSilently() {
        int guard = 0;
        while (GL11C.glGetError() != GL11C.GL_NO_ERROR && guard++ < 16) {
            // drain previous error state before optional capability probes
        }
    }

    private static void checkGlErrors(String where) {
        int guard = 0;
        int error;
        while ((error = GL11C.glGetError()) != GL11C.GL_NO_ERROR && guard++ < 16) {
            DebugLog.errorOnChange("shapeclip.framebuffer.gl.error", where + "|" + error, "[ShapeClip/GL] gl error at %s: 0x%s", where, Integer.toHexString(error));
        }
    }

    boolean supported() {
        return GlBackendAccess.current() != null;
    }

    String lastFailure() {
        return lastFailure;
    }

    String describe() {
        return "attachments=" + attachments.size()
                + ", warnedUnsupported=" + warnedUnsupported
                + ", lastFailure='" + lastFailure + "'";
    }

    boolean ensureForMainFramebuffer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return fail("MinecraftClient is null");
        RenderTarget framebuffer = mc.gameRenderer.mainRenderTarget();
        if (framebuffer == null) return fail("main framebuffer is null");

        /*
         * UI batches in Combatant are rendered into the main color target without passing the
         * framebuffer depth attachment to the render pass. GlTextureView keeps separate FBOs for
         * (color, null-depth) and (color, depthTexture). The shape-clip mask must be prepared and
         * cleared on the same color-only FBO that Renderer2D will use; otherwise the mask is written
         * to one framebuffer while the actual module rows are tested against another stencil buffer.
         */
        return ensure(framebuffer.getColorTextureView(), null);
    }

    boolean clearMainFramebufferStencil() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return fail("MinecraftClient is null");
        RenderTarget framebuffer = mc.gameRenderer.mainRenderTarget();
        if (framebuffer == null) return fail("main framebuffer is null");

        // Keep this in lockstep with ensureForMainFramebuffer(): Renderer2D UI uses color-only FBOs.
        return clear(framebuffer.getColorTextureView(), null);
    }

    boolean ensure(@Nullable GpuTextureView colorView, @Nullable GpuTextureView depthView) {
        IGlBackendInfo backend = GlBackendAccess.current();
        if (backend == null) {
            return fail("RenderSystem device is not GlBackend");
        }
        if (colorView == null) {
            return fail("color view is null");
        }
        if (!(colorView instanceof GlTextureView glColorView)) {
            return fail("color view is not GlTextureView: " + colorView.getClass().getName());
        }
        if (colorView.isClosed()) {
            return fail("color view is closed");
        }

        try {
            int framebuffer = framebufferFor(backend, glColorView, depthView);
            int width = Math.max(1, colorView.getWidth(0));
            int height = Math.max(1, colorView.getHeight(0));
            return ensureAttachment(framebuffer, width, height);
        } catch (Throwable t) {
            return fail("exception while preparing framebuffer: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    boolean clear(@Nullable GpuTextureView colorView, @Nullable GpuTextureView depthView) {
        if (!ensure(colorView, depthView)) return false;
        IGlBackendInfo backend = GlBackendAccess.current();
        if (backend == null || !(colorView instanceof GlTextureView glColorView)) {
            return fail("clear target is not backed by GlTextureView");
        }

        int previousReadFramebuffer = GlStateManager.getFrameBuffer(GlConst.GL_READ_FRAMEBUFFER);
        int previousDrawFramebuffer = GlStateManager.getFrameBuffer(GlConst.GL_DRAW_FRAMEBUFFER);
        boolean scissorWasEnabled = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        int previousStencilMask = GL11C.glGetInteger(GL11C.GL_STENCIL_WRITEMASK);
        try {
            int framebuffer = framebufferFor(backend, glColorView, depthView);
            GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, framebuffer);
            int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
            if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
                // Do not issue glClear on an incomplete FBO: that is what produces the scary
                // GL_INVALID_FRAMEBUFFER_OPERATION debug spam on stricter drivers. Rebuild the
                // attachment once because the owning GlTextureView may have recreated its backing FBO.
                invalidateAttachment(framebuffer, "clear preflight incomplete status=0x" + Integer.toHexString(status));
                if (!ensure(colorView, depthView)) {
                    return false;
                }
                framebuffer = framebufferFor(backend, glColorView, depthView);
                GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, framebuffer);
                status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
                if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
                    return fail("clear target still incomplete after rebuild: status=0x" + Integer.toHexString(status) + ", fbo=" + framebuffer);
                }
            }
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            GL11C.glStencilMask(0xFF);
            GL11C.glClearStencil(0);
            GL11C.glClear(GL11C.GL_STENCIL_BUFFER_BIT);
            GL11C.glStencilMask(previousStencilMask);
            checkGlErrors("clearStencil");
            return true;
        } catch (Throwable t) {
            return fail("exception while clearing stencil: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        } finally {
            if (scissorWasEnabled) {
                GL11C.glEnable(GL11C.GL_SCISSOR_TEST);
            } else {
                GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            }
            restoreFramebufferBindings(previousReadFramebuffer, previousDrawFramebuffer);
        }
    }

    void restoreActiveAttachments() {
        int previousReadFramebuffer = GlStateManager.getFrameBuffer(GlConst.GL_READ_FRAMEBUFFER);
        int previousDrawFramebuffer = GlStateManager.getFrameBuffer(GlConst.GL_DRAW_FRAMEBUFFER);
        try {
            for (Map.Entry<Integer, Attachment> entry : attachments.entrySet()) {
                Attachment attachment = entry.getValue();
                if (!attachment.attached) continue;
                GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, entry.getKey());
                attachment.restoreOriginalAttachments();
            }
        } catch (Throwable t) {
            fail("exception while restoring framebuffer attachments: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        } finally {
            restoreFramebufferBindings(previousReadFramebuffer, previousDrawFramebuffer);
        }
    }

    void cleanupClosedOrResized() {
        Iterator<Map.Entry<Integer, Attachment>> it = attachments.entrySet().iterator();
        while (it.hasNext()) {
            Attachment attachment = it.next().getValue();
            if (attachment.deleted) {
                it.remove();
            }
        }
    }

    boolean hasWarnedUnsupported() {
        return warnedUnsupported;
    }

    private boolean ensureAttachment(int framebuffer, int width, int height) {
        Attachment attachment = attachments.get(framebuffer);
        int previousReadFramebuffer = GlStateManager.getFrameBuffer(GlConst.GL_READ_FRAMEBUFFER);
        int previousDrawFramebuffer = GlStateManager.getFrameBuffer(GlConst.GL_DRAW_FRAMEBUFFER);
        try {
            GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, framebuffer);
            int samples = detectBoundFramebufferSamples();

            if (attachment == null || attachment.width != width || attachment.height != height || attachment.samples != samples || attachment.deleted) {
                if (attachment != null) {
                    attachment.restoreOriginalAttachments();
                    attachment.delete();
                }
                attachment = new Attachment(width, height, samples);
                attachments.put(framebuffer, attachment);
            }

            if (attachment.attached) {
                int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
                if (status == GL30C.GL_FRAMEBUFFER_COMPLETE) {
                    owner.setStencilAvailable(true);
                    lastFailure = "none";
                    checkGlErrors("ensureAttachment(existing)");
                    return true;
                }
                DebugLog.warnOnChange("shapeclip.attachment.stale", framebuffer + "|" + status + "|" + attachment.mode, "[ShapeClip/GL] existing stencil attachment stale/incomplete. fbo=%d status=0x%s mode=%s", framebuffer, Integer.toHexString(status), attachment.mode);
                attachment.restoreOriginalAttachments();
            }

            attachment.captureOriginalAttachments();

            if (!attachment.separateStencilRejected && tryAttachSeparateStencil(framebuffer, attachment)) {
                owner.setStencilAvailable(true);
                lastFailure = "none";
                return true;
            }

            if (tryAttachPackedDepthStencil(framebuffer, attachment)) {
                owner.setStencilAvailable(true);
                lastFailure = "none";
                return true;
            }

            attachment.restoreOriginalAttachments();
            markUnavailable();
            return false;
        } finally {
            restoreFramebufferBindings(previousReadFramebuffer, previousDrawFramebuffer);
        }
    }

    private void invalidateAttachment(int framebuffer, String reason) {
        Attachment attachment = attachments.remove(framebuffer);
        if (attachment == null) {
            lastFailure = reason;
            return;
        }
        try {
            attachment.restoreOriginalAttachments();
        } catch (Throwable t) {
            DebugLog.warnOnChange("shapeclip.attachment.restore.invalidate", framebuffer + "|" + t.getClass().getSimpleName(), "[ShapeClip/GL] failed to restore attachment while invalidating fbo=%d reason=%s error=%s: %s", framebuffer, reason, t.getClass().getSimpleName(), t.getMessage());
        } finally {
            try {
                attachment.delete();
            } catch (Throwable t) {
                DebugLog.warnOnChange("shapeclip.attachment.delete.invalidate", framebuffer + "|" + t.getClass().getSimpleName(), "[ShapeClip/GL] failed to delete invalidated attachment fbo=%d error=%s: %s", framebuffer, t.getClass().getSimpleName(), t.getMessage());
            }
            lastFailure = reason;
            DebugLog.stencilOnChange("shapeclip.attachment.invalidated", framebuffer + "|" + reason, "[ShapeClip/GL] invalidated cached stencil attachment. fbo=%d reason=%s", framebuffer, reason);
        }
    }

    private boolean tryAttachSeparateStencil(int framebuffer, Attachment attachment) {
        if (!attachment.separateStencilAllocationOk) {
            attachment.separateStencilRejected = true;
            lastFailure = "separate GL_STENCIL_INDEX8 allocation failed: fbo=" + framebuffer
                    + ", size=" + attachment.width + "x" + attachment.height
                    + ", samples=" + attachment.samples;
            DebugLog.stencilOnce("shapeclip.separate.alloc.failed." + attachment.samples, "[ShapeClip/GL] separate stencil allocation failed; packed depth-stencil will be tried. %s", lastFailure);
            return false;
        }
        GL30C.glFramebufferRenderbuffer(
                GL30C.GL_FRAMEBUFFER,
                GL30C.GL_STENCIL_ATTACHMENT,
                GL30C.GL_RENDERBUFFER,
                attachment.stencilRenderbuffer
        );
        int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
        if (status == GL30C.GL_FRAMEBUFFER_COMPLETE) {
            attachment.attached = true;
            attachment.mode = AttachmentMode.SEPARATE_STENCIL;
            DebugLog.stencilOnce("shapeclip.separate.stencil." + framebuffer, "[ShapeClip/GL] using separate GL_STENCIL_INDEX8. fbo=%d size=%dx%d samples=%d", framebuffer, attachment.width, attachment.height, attachment.samples);
            checkGlErrors("tryAttachSeparateStencil(success)");
            return true;
        }

        GL30C.glFramebufferRenderbuffer(GL30C.GL_FRAMEBUFFER, GL30C.GL_STENCIL_ATTACHMENT, GL30C.GL_RENDERBUFFER, 0);
        attachment.restoreOriginalAttachments();
        lastFailure = "separate GL_STENCIL_INDEX8 incomplete: status=0x"
                + Integer.toHexString(status)
                + ", fbo=" + framebuffer
                + ", size=" + attachment.width + "x" + attachment.height
                + ", samples=" + attachment.samples;
        attachment.separateStencilRejected = true;
        DebugLog.stencilOnce("shapeclip.separate.rejected", "[ShapeClip/GL] separate stencil attach failed, using packed depth-stencil path from now on. %s", lastFailure);
        return false;
    }

    private boolean tryAttachPackedDepthStencil(int framebuffer, Attachment attachment) {
        if (!attachment.packedDepthStencilAllocationOk) {
            lastFailure = "packed GL_DEPTH24_STENCIL8 allocation failed: fbo=" + framebuffer
                    + ", size=" + attachment.width + "x" + attachment.height
                    + ", samples=" + attachment.samples;
            DebugLog.warnOnChange("shapeclip.packed.alloc.failed", framebuffer + "|" + attachment.samples, "[ShapeClip/GL] packed depth-stencil allocation failed. %s", lastFailure);
            return false;
        }
        GL30C.glFramebufferRenderbuffer(
                GL30C.GL_FRAMEBUFFER,
                GL30C.GL_DEPTH_ATTACHMENT,
                GL30C.GL_RENDERBUFFER,
                attachment.depthStencilRenderbuffer
        );
        GL30C.glFramebufferRenderbuffer(
                GL30C.GL_FRAMEBUFFER,
                GL30C.GL_STENCIL_ATTACHMENT,
                GL30C.GL_RENDERBUFFER,
                attachment.depthStencilRenderbuffer
        );
        int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
        if (status == GL30C.GL_FRAMEBUFFER_COMPLETE) {
            attachment.attached = true;
            attachment.mode = AttachmentMode.PACKED_DEPTH_STENCIL;
            if (!attachment.loggedPackedMode) {
                attachment.loggedPackedMode = true;
                DebugLog.stencilOnce("shapeclip.packed.depth.stencil." + framebuffer, "[ShapeClip/GL] using packed depth-stencil for shape clip. fbo=%d size=%dx%d samples=%d",
                        framebuffer, attachment.width, attachment.height, attachment.samples);
            }
            checkGlErrors("tryAttachPackedDepthStencil(success)");
            return true;
        }

        lastFailure = "packed GL_DEPTH24_STENCIL8 incomplete: status=0x"
                + Integer.toHexString(status)
                + ", fbo=" + framebuffer
                + ", size=" + attachment.width + "x" + attachment.height
                + ", samples=" + attachment.samples;
        return false;
    }

    private boolean fail(String reason) {
        lastFailure = reason;
        markUnavailable();
        return false;
    }

    private void markUnavailable() {
        owner.setStencilAvailable(false);
        warnedUnsupported = true;
    }

    private enum AttachmentMode {
        NONE,
        SEPARATE_STENCIL,
        PACKED_DEPTH_STENCIL
    }

    private static final class Attachment {
        final int stencilRenderbuffer;
        final int depthStencilRenderbuffer;
        final int width;
        final int height;
        final int samples;
        final boolean separateStencilAllocationOk;
        final boolean packedDepthStencilAllocationOk;
        boolean deleted;
        boolean attached;
        boolean separateStencilRejected;
        boolean loggedPackedMode;
        AttachmentMode mode = AttachmentMode.NONE;
        SavedAttachment originalDepth = SavedAttachment.none();
        SavedAttachment originalStencil = SavedAttachment.none();

        Attachment(int width, int height, int samples) {
            this.width = width;
            this.height = height;
            this.samples = Math.max(1, samples);

            int previousRenderbuffer = GL11C.glGetInteger(GL30C.GL_RENDERBUFFER_BINDING);
            try {
                this.stencilRenderbuffer = GL30C.glGenRenderbuffers();
                GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, stencilRenderbuffer);
                this.separateStencilAllocationOk = allocateRenderbuffer(GL30C.GL_STENCIL_INDEX8, width, height, this.samples);

                this.depthStencilRenderbuffer = GL30C.glGenRenderbuffers();
                GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, depthStencilRenderbuffer);
                this.packedDepthStencilAllocationOk = allocateRenderbuffer(GL30C.GL_DEPTH24_STENCIL8, width, height, this.samples);
            } finally {
                GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, previousRenderbuffer);
            }
        }

        private static boolean allocateRenderbuffer(int internalFormat, int width, int height, int samples) {
            clearGlErrorsSilently();
            if (samples > 1) {
                GL32C.glRenderbufferStorageMultisample(GL30C.GL_RENDERBUFFER, samples, internalFormat, width, height);
            } else {
                GL30C.glRenderbufferStorage(GL30C.GL_RENDERBUFFER, internalFormat, width, height);
            }
            int error = GL11C.glGetError();
            if (error != GL11C.GL_NO_ERROR) {
                return false;
            }

            int storedWidth = GL30C.glGetRenderbufferParameteri(GL30C.GL_RENDERBUFFER, GL30C.GL_RENDERBUFFER_WIDTH);
            int storedHeight = GL30C.glGetRenderbufferParameteri(GL30C.GL_RENDERBUFFER, GL30C.GL_RENDERBUFFER_HEIGHT);
            int storedSamples = GL30C.glGetRenderbufferParameteri(GL30C.GL_RENDERBUFFER, GL30C.GL_RENDERBUFFER_SAMPLES);
            return storedWidth == width && storedHeight == height && Math.max(1, storedSamples) == Math.max(1, samples);
        }

        void captureOriginalAttachments() {
            if (attached) return;
            originalDepth = SavedAttachment.capture(GL30C.GL_DEPTH_ATTACHMENT);
            originalStencil = SavedAttachment.capture(GL30C.GL_STENCIL_ATTACHMENT);
        }

        void restoreOriginalAttachments() {
            originalDepth.restore(GL30C.GL_DEPTH_ATTACHMENT);
            originalStencil.restore(GL30C.GL_STENCIL_ATTACHMENT);
            attached = false;
            mode = AttachmentMode.NONE;
        }

        void delete() {
            if (deleted) return;
            restoreOriginalAttachments();
            deleted = true;
            GL30C.glDeleteRenderbuffers(stencilRenderbuffer);
            GL30C.glDeleteRenderbuffers(depthStencilRenderbuffer);
        }
    }

    private record SavedAttachment(int type, int name, int level, int cubeMapFace, int layer, boolean layered) {
        private static final int GL_FRAMEBUFFER_ATTACHMENT_LAYERED_SAFE = 0x8DA7;

        static SavedAttachment none() {
            return new SavedAttachment(GL11C.GL_NONE, 0, 0, 0, 0, false);
        }

        static SavedAttachment capture(int attachmentPoint) {
            int type = GL30C.glGetFramebufferAttachmentParameteri(
                    GL30C.GL_FRAMEBUFFER,
                    attachmentPoint,
                    GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
            );
            if (type == GL11C.GL_NONE) return none();

            int name = GL30C.glGetFramebufferAttachmentParameteri(
                    GL30C.GL_FRAMEBUFFER,
                    attachmentPoint,
                    GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
            );
            int level = 0;
            int cubeMapFace = 0;
            int layer = 0;
            boolean layered = false;
            if (type == GL11C.GL_TEXTURE) {
                level = GL30C.glGetFramebufferAttachmentParameteri(
                        GL30C.GL_FRAMEBUFFER,
                        attachmentPoint,
                        GL30C.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL
                );
                cubeMapFace = GL30C.glGetFramebufferAttachmentParameteri(
                        GL30C.GL_FRAMEBUFFER,
                        attachmentPoint,
                        GL30C.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE
                );
                try {
                    layer = GL30C.glGetFramebufferAttachmentParameteri(
                            GL30C.GL_FRAMEBUFFER,
                            attachmentPoint,
                            GL30C.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LAYER
                    );
                    layered = GL30C.glGetFramebufferAttachmentParameteri(
                            GL30C.GL_FRAMEBUFFER,
                            attachmentPoint,
                            GL_FRAMEBUFFER_ATTACHMENT_LAYERED_SAFE
                    ) != 0;
                } catch (Throwable ignored) {
                    clearGlErrorsSilently();
                }
            }
            return new SavedAttachment(type, name, level, cubeMapFace, layer, layered);
        }

        void restore(int attachmentPoint) {
            if (type == GL11C.GL_TEXTURE) {
                if (cubeMapFace != 0) {
                    GL30C.glFramebufferTexture2D(
                            GL30C.GL_FRAMEBUFFER,
                            attachmentPoint,
                            cubeMapFace,
                            name,
                            level
                    );
                } else {
                    // Do not query a non-existent TEXTURE_TARGET token. glFramebufferTexture lets
                    // the driver restore ordinary 2D and 2D multisample attachments target-free.
                    GL32C.glFramebufferTexture(
                            GL30C.GL_FRAMEBUFFER,
                            attachmentPoint,
                            name,
                            level
                    );
                }
                return;
            }
            if (type == GL30C.GL_RENDERBUFFER) {
                GL30C.glFramebufferRenderbuffer(
                        GL30C.GL_FRAMEBUFFER,
                        attachmentPoint,
                        GL30C.GL_RENDERBUFFER,
                        name
                );
                return;
            }
            GL30C.glFramebufferRenderbuffer(
                    GL30C.GL_FRAMEBUFFER,
                    attachmentPoint,
                    GL30C.GL_RENDERBUFFER,
                    0
            );
        }
    }
}
