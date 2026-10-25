/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.depth;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.msaa.MsaaWorldTarget;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.util.logging.DebugLog;

/**
 * Captures the main framebuffer depth BEFORE translucent layers.
 * Used to render custom geometry above liquids while still depth-testing vs solids.
 */
public enum PreTranslucentDepth {
    ;
    private static TextureTarget depthCopy;
    private static GpuTextureView msaaDepthView;
    private static GpuTexture msaaDepthCopy;
    private static GpuTextureView msaaDepthCopyView;
    private static int msaaSamples;
    private static int depthCopyW = -1;
    private static int depthCopyH = -1;
    private static int msaaDepthCopyW = -1;
    private static int msaaDepthCopyH = -1;
    private static boolean captured;
    private static boolean capturedMsaa;
    private static long capturedFrameId = Long.MIN_VALUE;

    public static void captureFrom(RenderTarget source) {
        captured = false;
        capturedMsaa = false;
        capturedFrameId = Long.MIN_VALUE;
        msaaDepthView = null;
        if (source == null || source.getDepthTexture() == null) return;
        int w = source.width;
        int h = source.height;
        if (w <= 0 || h <= 0) return;

        boolean isMsaa = source.getDepthTexture() instanceof IMsaaTexture msaa && msaa.combatant$isMsaa();
        if (isMsaa) {
            int samples = ((IMsaaTexture) source.getDepthTexture()).combatant$getSamples();
            try {
                ensureMsaaDepthCopy(w, h, samples);
                if (msaaDepthCopy != null) {
                    RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                            source.getDepthTexture(),
                            msaaDepthCopy,
                            0, 0, 0,
                            0, 0,
                            w, h
                    );
                    msaaDepthView = msaaDepthCopyView;
                    capturedMsaa = true;
                    capturedFrameId = currentFrameId();
                    if (CombatantRenderSystem.rhi().msaa().eagerPreTranslucentDepthResolve()) {
                        captured = resolveMsaaDepthCopy();
                    }
                }
            } catch (RuntimeException failure) {
                // In particular, a resized framebuffer can still expose its previous MSAA image.
                releaseMsaaDepthCopy();
                capturedMsaa = false;
                DebugLog.warnOnChange("pre-translucent-depth.msaa.capture.failed",
                        failure.getClass().getName() + ":" + failure.getMessage(),
                        "[PreTranslucentDepth] MSAA capture failed: %s: %s",
                        failure.getClass().getSimpleName(), failure.getMessage());
            }
            // OpenGL supports depth-only framebuffer blits, even though it does not implement
            // MsaaControl.resolveDepthSnapshot(view, view). Keep a single-sample depth copy
            // for post-process consumers rather than exposing a non-sampleable MSAA view.
            if (!CombatantRenderSystem.rhi().msaa().supportsDepthSnapshotResolve()) {
                try {
                    ensureResolvedDepthCopy(w, h);
                    captured = depthCopy != null
                            && CombatantRenderSystem.rhi().msaa().resolve(source, depthCopy, false, true);
                    if (captured) capturedFrameId = currentFrameId();
                } catch (RuntimeException failure) {
                    DebugLog.warnOnChange("pre-translucent-depth.msaa.resolve.failed",
                            failure.getClass().getName() + ":" + failure.getMessage(),
                            "[PreTranslucentDepth] MSAA depth resolve failed: %s: %s",
                            failure.getClass().getSimpleName(), failure.getMessage());
                }
            }
        } else {
            try {
                ensureResolvedDepthCopy(w, h);
                if (depthCopy == null || depthCopy.getDepthTextureView() == null) return;
                depthCopy.copyDepthFrom(source);
                captured = true;
                capturedFrameId = currentFrameId();
            } catch (RuntimeException failure) {
                DebugLog.warnOnChange("pre-translucent-depth.capture.failed",
                        failure.getClass().getName() + ":" + failure.getMessage(),
                        "[PreTranslucentDepth] capture failed: %s: %s",
                        failure.getClass().getSimpleName(), failure.getMessage());
            }
        }
    }

    public static void capture() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        RenderTarget fb = mc.gameRenderer.mainRenderTarget();
        if (fb == null) return;
        captureFrom(fb);
    }

    public static boolean hasCapturedThisFrame() {
        return capturedFrameId == currentFrameId() && (captured || capturedMsaa);
    }

    public static GpuTextureView getDepthView() {
        // Do not accept a stale vanilla snapshot; Iris keeps its existing capture path.
        if (!IrisRuntime.isShaderpackRendererActive() && !hasCapturedThisFrame()) return null;
        if (MsaaWorldTarget.isActive() && capturedMsaa && msaaDepthView != null) {
            return msaaDepthView;
        }
        if (captured && depthCopy != null) {
            return depthCopy.getDepthTextureView();
        }
        if (capturedMsaa && msaaDepthView != null && resolveMsaaDepthCopy()) {
            return depthCopy.getDepthTextureView();
        }
        if (capturedMsaa
                && msaaDepthView != null
                && !CombatantRenderSystem.rhi().msaa().supportsDepthSnapshotResolve()) {
            return msaaDepthView;
        }
        return WorldSceneDepth.mainDepthView();
    }


    public static GpuTextureView getCapturedDepthView() {
        if (!hasCapturedThisFrame()) {
            return null;
        }
        if (captured && depthCopy != null) {
            return depthCopy.getDepthTextureView();
        }
        if (capturedMsaa && msaaDepthView != null && resolveMsaaDepthCopy()) {
            return depthCopy != null ? depthCopy.getDepthTextureView() : null;
        }
        return null;
    }

    public static GpuTextureView getDepthViewFor(GpuTextureView colorAttachment) {
        int requiredSamples = samples(colorAttachment);
        if (!IrisRuntime.isShaderpackRendererActive() && !hasCapturedThisFrame()) return null;
        if (requiredSamples > 1
                && capturedMsaa
                && msaaDepthView != null
                && samples(msaaDepthView) == requiredSamples) {
            return msaaDepthView;
        }
        if (requiredSamples == 1 && captured && depthCopy != null) {
            return depthCopy.getDepthTextureView();
        }
        if (requiredSamples == 1 && capturedMsaa && msaaDepthView != null && resolveMsaaDepthCopy()) {
            return depthCopy.getDepthTextureView();
        }
        return null;
    }

    public static void reset() {
        captured = false;
        capturedMsaa = false;
        capturedFrameId = Long.MIN_VALUE;
        msaaDepthView = null;
    }

    private static long currentFrameId() {
        RenderFrameContext context = CombatantRenderSystem.currentContext();
        return context != null ? context.frameId() : CombatantRenderSystem.resources().frameId();
    }

    private static void ensureMsaaDepthCopy(int w, int h, int samples) {
        if (!CombatantRenderSystem.rhi().msaa().supported()) return;
        if (msaaDepthCopy == null || w != msaaDepthCopyW || h != msaaDepthCopyH || msaaSamples != samples) {
            releaseMsaaDepthCopy();
            int usage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_RENDER_ATTACHMENT;
            msaaDepthCopy = CombatantRenderSystem.rhi().msaa().createTexture(
                    "combatant-pretrans-depth-msaa",
                    usage,
                    GpuFormat.D32_FLOAT,
                    w,
                    h,
                    samples
            );
            msaaDepthCopyView = RenderSystem.getDevice().createTextureView(msaaDepthCopy);
            msaaSamples = samples;
            msaaDepthCopyW = w;
            msaaDepthCopyH = h;
        }
    }

    private static void ensureResolvedDepthCopy(int w, int h) {
        depthCopy = CombatantRenderSystem.resources().persistentFramebuffer(
                "combatant-pretrans-depth",
                w,
                h,
                true,
                "PreTranslucentDepth"
        );
        depthCopyW = w;
        depthCopyH = h;
    }

    private static boolean resolveMsaaDepthCopy() {
        if (captured) return true;
        if (!capturedMsaa || msaaDepthCopyView == null || msaaDepthCopyW <= 0 || msaaDepthCopyH <= 0) {
            return false;
        }
        if (!CombatantRenderSystem.rhi().msaa().supportsDepthSnapshotResolve()) {
            return false;
        }
        ensureResolvedDepthCopy(msaaDepthCopyW, msaaDepthCopyH);
        captured = depthCopy != null
                && depthCopy.getDepthTextureView() != null
                && CombatantRenderSystem.rhi().msaa().resolveDepthSnapshot(
                        msaaDepthCopyView,
                        depthCopy.getDepthTextureView()
                );
        return captured;
    }

    private static int samples(GpuTextureView view) {
        if (view == null || view.texture() == null) return 1;
        return view.texture() instanceof IMsaaTexture msaa && msaa.combatant$isMsaa()
                ? msaa.combatant$getSamples()
                : 1;
    }

    private static void releaseMsaaDepthCopy() {
        if (msaaDepthCopyView != null) {
            msaaDepthCopyView.close();
            msaaDepthCopyView = null;
        }
        if (msaaDepthCopy != null) {
            CombatantRenderSystem.resources().retireTexture(msaaDepthCopy);
            msaaDepthCopy = null;
        }
        msaaDepthView = null;
        msaaSamples = 0;
        msaaDepthCopyW = -1;
        msaaDepthCopyH = -1;
    }
}
