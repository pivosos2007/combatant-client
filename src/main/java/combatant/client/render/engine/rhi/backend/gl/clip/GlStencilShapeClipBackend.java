/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.backend.gl.clip;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import combatant.client.render.engine.rhi.clip.ShapeClipBackend;
import combatant.client.render.engine.rhi.clip.ShapeClipRenderPassContract;
import combatant.client.util.logging.DebugLog;

/**
 * GL implementation of shape clipping using a render-pass-bound stencil attachment.
 */
public final class GlStencilShapeClipBackend implements ShapeClipBackend {
    private final GlStencilFramebufferSupport framebuffers = new GlStencilFramebufferSupport(this);
    private Mode mode = Mode.DISABLED;
    private int reference;
    private int compareReference;
    private boolean stencilAvailable;
    private boolean renderPassAttachmentRequired;
    private boolean clearRequested;
    private String attachmentReason = "none";
    private String clearReason = "none";
    private @Nullable GpuTextureView currentColorView;
    private @Nullable GpuTextureView currentDepthView;
    private String currentPassLabel = "<no-pass>";
    private boolean currentPassPrepared;
    private ShapeClipRenderPassContract currentPipelineContract = ShapeClipRenderPassContract.NONE;
    private String currentPipelineName = "<no-pipeline>";
    private boolean depthStateCaptured;
    private boolean previousDepthTest;
    private boolean previousDepthMask;
    private boolean cullStateCaptured;
    private boolean previousCullFace;
    private String lastFailure = "none";
    private int offscreenPassBypassDepth;

    private static int clampRef(int value) {
        if (value < 0) return 0;
        return Math.min(255, value);
    }

    private static String describeView(@Nullable GpuTextureView view) {
        if (view == null) return "null";
        try {
            return view.getClass().getSimpleName()
                    + "["
                    + view.getWidth(0)
                    + "x"
                    + view.getHeight(0)
                    + ", closed="
                    + view.isClosed()
                    + "]";
        } catch (Throwable t) {
            return view.getClass().getSimpleName() + "[describe failed: " + t.getClass().getSimpleName() + "]";
        }
    }

    @Override
    public boolean supported() {
        return framebuffers.supported();
    }

    @Override
    public boolean isActive() {
        return mode != Mode.DISABLED;
    }

    @Override
    public void requireRenderPassAttachment(String reason) {
        renderPassAttachmentRequired = true;
        attachmentReason = reason == null ? "unspecified" : reason;
    }

    @Override
    public void requestClear(String reason) {
        clearRequested = true;
        clearReason = reason == null ? "unspecified" : reason;
    }

    @Override
    public void beginRenderPass(String label, @Nullable GpuTextureView colorView, @Nullable GpuTextureView depthView) {
        currentPassLabel = label == null ? "<unnamed-pass>" : label;
        currentColorView = colorView;
        currentDepthView = depthView;
        currentPassPrepared = false;

        if (offscreenPassBypassDepth > 0) {
            resetNativeStateOnly();
            checkGlErrors("beginRenderPass(offscreen-bypass)");
            return;
        }

        boolean shouldPrepare = renderPassAttachmentRequired || mode != Mode.DISABLED || clearRequested;
        if (!shouldPrepare) {
            checkGlErrors("beginRenderPass(no-clip)");
            return;
        }

        if (!prepareCurrentPassTarget("beginRenderPass")) {
            DebugLog.errorOnChange("shapeclip.renderpass.prepare.failed", currentPassLabel + "|" + attachmentReason + "|" + lastFailureReason(), "[ShapeClip/GL] render pass stencil prepare failed: pass=%s reason=%s lastFailure=%s", currentPassLabel, attachmentReason, lastFailureReason());
            checkGlErrors("beginRenderPass(prepare-failed)");
            return;
        }

        if (clearRequested) {
            boolean cleared = framebuffers.clear(currentColorView, currentDepthView);
            if (!cleared) {
                lastFailure = "clear failed: " + framebuffers.lastFailure();
                DebugLog.errorOnChange("shapeclip.renderpass.clear.failed", currentPassLabel + "|" + clearReason + "|" + lastFailure, "[ShapeClip/GL] render pass stencil clear failed: pass=%s reason=%s lastFailure=%s", currentPassLabel, clearReason, lastFailure);
            }
            clearRequested = false;
            clearReason = "none";
        }

        checkGlErrors("beginRenderPass");
    }

    @Override
    public void endRenderPass() {
        resetNativeStateOnly();
        currentColorView = null;
        currentDepthView = null;
        currentPassLabel = "<no-pass>";
        currentPassPrepared = false;
        currentPipelineContract = ShapeClipRenderPassContract.NONE;
        currentPipelineName = "<no-pipeline>";
        if (offscreenPassBypassDepth == 0) {
            renderPassAttachmentRequired = false;
            attachmentReason = "none";
        }
        checkGlErrors(offscreenPassBypassDepth > 0 ? "endRenderPass(offscreen-bypass)" : "endRenderPass");
    }

    @Override
    public void bindPipeline(RenderPipeline pipeline, ShapeClipRenderPassContract contract) {
        currentPipelineContract = contract == null ? ShapeClipRenderPassContract.NONE : contract;
        currentPipelineName = pipeline == null || pipeline.getLocation() == null ? "<unknown>" : pipeline.getLocation().toString();
        if (offscreenPassBypassDepth > 0) {
            return;
        }
        if (mode != Mode.DISABLED && currentPipelineContract == ShapeClipRenderPassContract.NONE) {
            DebugLog.warnOnce(
                    "shapeclip.stale.pipeline.contract." + currentPipelineName,
                    "[ShapeClip/GL] stale clip state risk: active mode=%s ref=%d but pipeline has no shape-clip contract. pipeline=%s pass=%s",
                    mode,
                    reference,
                    currentPipelineName,
                    currentPassLabel
            );
        }
        if (currentPipelineContract.requiresAttachment(mode != Mode.DISABLED || renderPassAttachmentRequired)) {
            requireRenderPassAttachment("pipeline contract " + currentPipelineContract + " for " + currentPipelineName);
            if (currentColorView != null && !currentPassPrepared) {
                prepareCurrentPassTarget("bindPipeline");
            }
        }
    }

    @Override
    public boolean prepareMainTarget() {
        return framebuffers.ensureForMainFramebuffer();
    }

    @Override
    public boolean prepare(@Nullable GpuTextureView colorView, @Nullable GpuTextureView depthView) {
        boolean ok = framebuffers.ensure(colorView, depthView);
        if (!ok) lastFailure = framebuffers.lastFailure();
        return ok;
    }

    @Override
    public boolean clearMainTarget() {
        return framebuffers.clearMainFramebufferStencil();
    }

    void setStencilAvailable(boolean available) {
        stencilAvailable = available;
        if (!available) {
            mode = Mode.DISABLED;
            reference = 0;
            compareReference = 0;
        }
    }

    @Override
    public void beginWrite(int parentReference, int newReference) {
        mode = Mode.WRITE;
        compareReference = clampRef(parentReference);
        reference = clampRef(newReference);
        requireRenderPassAttachment("mask write parent=" + compareReference + " ref=" + reference);
    }

    @Override
    public void beginRestore(int currentReference, int parentReference) {
        mode = Mode.RESTORE;
        compareReference = clampRef(currentReference);
        reference = clampRef(parentReference);
        requireRenderPassAttachment("mask restore current=" + compareReference + " parent=" + reference);
    }

    @Override
    public void beginTest(int activeReference) {
        mode = Mode.TEST;
        reference = clampRef(activeReference);
        compareReference = reference;
        requireRenderPassAttachment("content test ref=" + reference);
    }

    @Override
    public void pushOffscreenPassBypass() {
        offscreenPassBypassDepth++;
        if (offscreenPassBypassDepth == 1) {
            resetNativeStateOnly();
        }
    }

    @Override
    public void popOffscreenPassBypass() {
        if (offscreenPassBypassDepth <= 0) return;
        offscreenPassBypassDepth--;
    }

    @Override
    public void disable() {
        resetNativeStateOnly();
        mode = Mode.DISABLED;
        reference = 0;
        compareReference = 0;
        stencilAvailable = false;
        renderPassAttachmentRequired = false;
        clearRequested = false;
        attachmentReason = "none";
        clearReason = "none";
        offscreenPassBypassDepth = 0;
        framebuffers.restoreActiveAttachments();
        checkGlErrors("disable");
    }

    @Override
    public void applyNativeState() {
        if (offscreenPassBypassDepth > 0) {
            resetNativeStateOnly();
            checkGlErrors("applyNativeState(offscreen-bypass)");
            return;
        }
        if (mode == Mode.DISABLED) {
            resetNativeStateOnly();
            checkGlErrors("applyNativeState(disabled)");
            return;
        }

        if (!prepareCurrentPassTarget("applyNativeState")) {
            resetNativeStateOnly();
            DebugLog.errorOnChange(
                    "shapeclip.apply.prepare.failed",
                    currentPassLabel + "|" + currentPipelineName + "|" + mode + "|" + reference + "|" + lastFailureReason(),
                    "[ShapeClip/GL] cannot apply stencil state: pass=%s pipeline=%s mode=%s ref=%d reason=%s lastFailure=%s",
                    currentPassLabel,
                    currentPipelineName,
                    mode,
                    reference,
                    attachmentReason,
                    lastFailureReason()
            );
            checkGlErrors("applyNativeState(prepare-failed)");
            return;
        }

        captureDepthStateIfNeeded();
        GL11C.glDisable(GL11C.GL_DEPTH_TEST);
        GL11C.glDepthMask(false);
        GL11C.glEnable(GL11C.GL_STENCIL_TEST);

        switch (mode) {
            case WRITE -> {
                captureCullStateIfNeeded();
                GL11C.glDisable(GL11C.GL_CULL_FACE);
                GL11C.glStencilMask(0xFF);
                GL20C.glStencilMaskSeparate(GL11C.GL_FRONT_AND_BACK, 0xFF);
                int compareFunc = compareReference == 0 ? GL11C.GL_ALWAYS : GL11C.GL_EQUAL;
                GL11C.glStencilFunc(compareFunc, compareReference, 0xFF);
                GL20C.glStencilFuncSeparate(GL11C.GL_FRONT_AND_BACK, compareFunc, compareReference, 0xFF);
                // The stack model stores child = parent + 1. GL_REPLACE cannot express
                // "compare against parent but write child", because REPLACE writes the
                // same ref value supplied to glStencilFunc. Increment/decrement keeps the
                // compare ref independent from the value transition.
                GL11C.glStencilOp(GL11C.GL_KEEP, GL11C.GL_INCR, GL11C.GL_INCR);
                GL20C.glStencilOpSeparate(GL11C.GL_FRONT_AND_BACK, GL11C.GL_KEEP, GL11C.GL_INCR, GL11C.GL_INCR);
                GL11C.glColorMask(false, false, false, false);
            }
            case RESTORE -> {
                captureCullStateIfNeeded();
                GL11C.glDisable(GL11C.GL_CULL_FACE);
                GL11C.glStencilMask(0xFF);
                GL20C.glStencilMaskSeparate(GL11C.GL_FRONT_AND_BACK, 0xFF);
                GL11C.glStencilFunc(GL11C.GL_EQUAL, compareReference, 0xFF);
                GL20C.glStencilFuncSeparate(GL11C.GL_FRONT_AND_BACK, GL11C.GL_EQUAL, compareReference, 0xFF);
                GL11C.glStencilOp(GL11C.GL_KEEP, GL11C.GL_DECR, GL11C.GL_DECR);
                GL20C.glStencilOpSeparate(GL11C.GL_FRONT_AND_BACK, GL11C.GL_KEEP, GL11C.GL_DECR, GL11C.GL_DECR);
                GL11C.glColorMask(false, false, false, false);
            }
            case TEST -> {
                restoreCullStateIfNeeded();
                GL11C.glStencilMask(0x00);
                GL20C.glStencilMaskSeparate(GL11C.GL_FRONT_AND_BACK, 0x00);
                GL11C.glStencilFunc(GL11C.GL_EQUAL, reference, 0xFF);
                GL20C.glStencilFuncSeparate(GL11C.GL_FRONT_AND_BACK, GL11C.GL_EQUAL, reference, 0xFF);
                GL11C.glStencilOp(GL11C.GL_KEEP, GL11C.GL_KEEP, GL11C.GL_KEEP);
                GL20C.glStencilOpSeparate(GL11C.GL_FRONT_AND_BACK, GL11C.GL_KEEP, GL11C.GL_KEEP, GL11C.GL_KEEP);
                GL11C.glColorMask(true, true, true, true);
            }
            case DISABLED -> resetNativeStateOnly();
        }

        checkGlErrors("applyNativeState(" + mode + ")");
    }

    private boolean prepareCurrentPassTarget(String source) {
        if (currentColorView == null) {
            lastFailure = "no current render pass target while " + source + " requested stencil state";
            DebugLog.warnOnChange("shapeclip.no.current.pass", lastFailure, "[ShapeClip/GL] stale state: %s", lastFailure);
            return false;
        }
        if (currentPassPrepared && stencilAvailable) return true;
        boolean ok = framebuffers.ensure(currentColorView, currentDepthView);
        currentPassPrepared = ok;
        stencilAvailable = ok;
        if (!ok) {
            lastFailure = framebuffers.lastFailure();
            DebugLog.errorOnChange("shapeclip.prepare.current.target.failed", source + "|" + currentPassLabel + "|" + describeView(currentColorView) + "|" + describeView(currentDepthView) + "|" + lastFailure, "[ShapeClip/GL] prepareCurrentPassTarget failed from=%s pass=%s targetColor=%s targetDepth=%s failure=%s",
                    source, currentPassLabel, describeView(currentColorView), describeView(currentDepthView), lastFailure);
            return false;
        }
        lastFailure = "none";
        return true;
    }

    private void resetNativeStateOnly() {
        GL11C.glDisable(GL11C.GL_STENCIL_TEST);
        GL11C.glStencilMask(0x00);
        GL20C.glStencilMaskSeparate(GL11C.GL_FRONT_AND_BACK, 0x00);
        GL11C.glStencilFunc(GL11C.GL_ALWAYS, 0, 0xFF);
        GL20C.glStencilFuncSeparate(GL11C.GL_FRONT_AND_BACK, GL11C.GL_ALWAYS, 0, 0xFF);
        GL11C.glStencilOp(GL11C.GL_KEEP, GL11C.GL_KEEP, GL11C.GL_KEEP);
        GL20C.glStencilOpSeparate(GL11C.GL_FRONT_AND_BACK, GL11C.GL_KEEP, GL11C.GL_KEEP, GL11C.GL_KEEP);
        GL11C.glColorMask(true, true, true, true);
        restoreDepthStateIfNeeded();
        restoreCullStateIfNeeded();
    }

    private void captureDepthStateIfNeeded() {
        if (depthStateCaptured) return;
        previousDepthTest = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
        previousDepthMask = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);
        depthStateCaptured = true;
    }

    private void restoreDepthStateIfNeeded() {
        if (!depthStateCaptured) return;
        if (previousDepthTest) {
            GL11C.glEnable(GL11C.GL_DEPTH_TEST);
        } else {
            GL11C.glDisable(GL11C.GL_DEPTH_TEST);
        }
        GL11C.glDepthMask(previousDepthMask);
        depthStateCaptured = false;
    }

    private void captureCullStateIfNeeded() {
        if (cullStateCaptured) return;
        previousCullFace = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
        cullStateCaptured = true;
    }

    private void restoreCullStateIfNeeded() {
        if (!cullStateCaptured) return;
        if (previousCullFace) {
            GL11C.glEnable(GL11C.GL_CULL_FACE);
        } else {
            GL11C.glDisable(GL11C.GL_CULL_FACE);
        }
        cullStateCaptured = false;
    }

    @Override
    public String debugState() {
        return toString();
    }

    @Override
    public String lastFailureReason() {
        if (!"none".equals(lastFailure)) return lastFailure;
        return framebuffers.lastFailure();
    }

    @Override
    public String toString() {
        return "GlStencilShapeClipBackend{"
                + "mode=" + mode
                + ", ref=" + reference
                + ", compareRef=" + compareReference
                + ", stencilAvailable=" + stencilAvailable
                + ", passPrepared=" + currentPassPrepared
                + ", pass='" + currentPassLabel + '\''
                + ", pipeline='" + currentPipelineName + '\''
                + ", contract=" + currentPipelineContract
                + ", attachmentRequired=" + renderPassAttachmentRequired
                + ", clearRequested=" + clearRequested
                + ", offscreenBypassDepth=" + offscreenPassBypassDepth
                + ", supported=" + supported()
                + ", framebuffer=" + framebuffers.describe()
                + '}';
    }

    public String lastFailure() {
        return lastFailureReason();
    }

    private void checkGlErrors(String where) {
        int guard = 0;
        int error;
        while ((error = GL11C.glGetError()) != GL11C.GL_NO_ERROR && guard++ < 16) {
            DebugLog.errorOnChange("shapeclip.gl.error", where + "|" + error, "[ShapeClip/GL] gl error at %s: 0x%s state=%s", where, Integer.toHexString(error), this);
        }
    }

    private enum Mode {
        DISABLED,
        WRITE,
        RESTORE,
        TEST
    }
}
