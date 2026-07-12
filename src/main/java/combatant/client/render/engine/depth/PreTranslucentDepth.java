/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.depth;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.msaa.MsaaWorldTarget;

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

    public static void captureFrom(RenderTarget source) {
        if (source == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        int w = source.width;
        int h = source.height;
        if (w <= 0 || h <= 0) return;

        captured = false;
        capturedMsaa = false;

        boolean isMsaa = source.getDepthTexture() instanceof IMsaaTexture msaa && msaa.combatant$isMsaa();
        if (isMsaa) {
            int samples = ((IMsaaTexture) source.getDepthTexture()).combatant$getSamples();
            ensureMsaaDepthCopy(w, h, samples);
            if (msaaDepthCopy != null && source.getDepthTexture() != null) {
                try {
                    RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                            source.getDepthTexture(),
                            msaaDepthCopy,
                            0, 0, 0,
                            0, 0,
                            w, h
                    );
                    msaaDepthView = msaaDepthCopyView;
                    capturedMsaa = true;
                    if (CombatantRenderSystem.rhi().msaa().eagerPreTranslucentDepthResolve()) {
                        captured = resolveMsaaDepthCopy();
                    }
                } catch (IllegalArgumentException ignored) {
                    // A resize can race this capture point on some setups: framebuffer.textureHeight
                    // may already report the new window size while the backing MSAA depth texture
                    // is still the previous one. Do not crash the render thread; skip this frame and
                    // force a fresh allocation on the next capture.
                    releaseMsaaDepthCopy();
                    capturedMsaa = false;
                    msaaDepthView = null;
                }
            }
        } else {
            msaaDepthView = null;
            capturedMsaa = false;
            depthCopy = CombatantRenderSystem.resources().persistentFramebuffer(
                    "combatant-pretrans-depth", w, h, true, "PreTranslucentDepth");
            depthCopyW = w;
            depthCopyH = h;
            depthCopy.copyDepthFrom(source);
            captured = true;
        }
    }

    public static void capture() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        RenderTarget fb = mc.gameRenderer.mainRenderTarget();
        if (fb == null) return;
        captureFrom(fb);
    }

    public static GpuTextureView getDepthView() {
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

    public static GpuTextureView getDepthViewFor(GpuTextureView colorAttachment) {
        int requiredSamples = samples(colorAttachment);
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
        GpuTextureView fallback = WorldSceneDepth.mainDepthView();
        return samples(fallback) == requiredSamples ? fallback : null;
    }

    public static void reset() {
        captured = false;
        capturedMsaa = false;
        msaaDepthView = null;
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
