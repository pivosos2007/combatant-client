/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.msaa;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.mixins.accessors.GameRendererAccessor;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.logging.DebugLog;

public enum MsaaWorldTarget {
    ;
    private static MsaaFramebuffer msaa;
    private static int samples;
    private static int allocationRequestSamples;
    private static int width;
    private static int height;
    private static boolean active;
    private static int requestedSamples;
    private static boolean supported;
    private static boolean lastResolveOk;
    private static boolean resolveDepth;
    private static String state = "never";

    public static void begin(Minecraft mc, int sampleCount) {
        begin(mc, sampleCount, true);
    }

    public static void begin(Minecraft mc, int sampleCount, boolean needDepthResolve) {
        requestedSamples = sampleCount;
        resolveDepth = needDepthResolve;
        if (mc == null) {
            state = "no_client";
            active = false;
            return;
        }
        if (RuntimeGate.isPanic()) {
            state = "panic";
            shutdown(mc);
            return;
        }
        if (sampleCount <= 1) {
            state = "off";
            supported = false;
            if (active || msaa != null) {
                shutdown(mc);
            }
            return;
        }
        supported = CombatantRenderSystem.rhi().msaa().supported();
        if (!supported) {
            state = "unsupported";
            shutdown(mc);
            return;
        }
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        if (w <= 0 || h <= 0) {
            state = "bad_size";
            shutdown(mc);
            return;
        }

        ensureBuffer(w, h, sampleCount);
        if (msaa == null) {
            state = "alloc_failed";
            shutdown(mc);
            return;
        }

        RenderTarget main = rawMain(mc);
        if (main == null) {
            state = "no_main";
            shutdown(mc);
            return;
        }
        try {
            CombatantRenderSystem.rhi().msaa().prepareTarget(msaa, main, true, needDepthResolve);
        } catch (Throwable t) {
            CombatantRenderSystem.rhi().msaa().abandonTarget(msaa);
            lastResolveOk = false;
            state = "prepare_failed";
            DebugLog.warnOnChange("msaa.world.prepare_failed",
                    sampleCount + "|" + t.getClass().getSimpleName() + "|" + t.getMessage(),
                    "[MSAA] world target preparation failed; disabling target. requested=%d size=%dx%d error=%s: %s",
                    sampleCount, w, h, t.getClass().getSimpleName(), t.getMessage());
            shutdown(mc);
            return;
        }

        state = "active";
        active = true;
    }

    public static void resolveToMain(Minecraft mc) {
        if (mc == null) return;
        if (!active || msaa == null) {
            return;
        }
        RenderTarget main = rawMain(mc);
        if (main == null) {
            CombatantRenderSystem.rhi().msaa().abandonTarget(msaa);
            active = false;
            lastResolveOk = false;
            state = "resolve_no_main";
            return;
        }
        lastResolveOk = CombatantRenderSystem.rhi().msaa().resolve(msaa, main, true, resolveDepth);
        active = false;
    }

    public static void shutdown(Minecraft mc) {
        if (!active && msaa == null) {
            return;
        }
        if (active) {
            resolveToMain(mc);
        }
        if (msaa != null) {
            MsaaFramebuffer retired = msaa;
            CombatantRenderSystem.rhi().msaa().abandonTarget(retired);
            CombatantRenderSystem.resources().retire(retired::destroyBuffers);
            msaa = null;
        }
        active = false;
        samples = 0;
        allocationRequestSamples = 0;
        width = 0;
        height = 0;
    }

    private static void ensureBuffer(int w, int h, int sampleCount) {
        if (msaa == null || allocationRequestSamples != sampleCount) {
            MsaaFramebuffer previous = msaa;
            try {
                MsaaFramebuffer next = new MsaaFramebuffer("combatant-msaa-world", w, h, true, sampleCount);
                msaa = next;
                samples = next.getSamples();
                allocationRequestSamples = sampleCount;
                width = w;
                height = h;
                if (previous != null) {
                    CombatantRenderSystem.rhi().msaa().abandonTarget(previous);
                    CombatantRenderSystem.resources().retire(previous::destroyBuffers);
                }
            } catch (Throwable t) {
                if (previous != null) {
                    CombatantRenderSystem.rhi().msaa().abandonTarget(previous);
                    CombatantRenderSystem.resources().retire(previous::destroyBuffers);
                }
                msaa = null;
                samples = 0;
                allocationRequestSamples = 0;
                width = 0;
                height = 0;
                state = "alloc_failed";
                DebugLog.warnOnChange("msaa.world.alloc_failed", sampleCount + "|" + t.getClass().getSimpleName() + "|" + t.getMessage(),
                        "[MSAA] world MSAA allocation failed; disabling target. requested=%d size=%dx%d error=%s: %s",
                        sampleCount, w, h, t.getClass().getSimpleName(), t.getMessage());
            }
            return;
        }
        if (w != width || h != height) {
            try {
                msaa.resize(w, h);
                samples = msaa.getSamples();
                width = w;
                height = h;
            } catch (Throwable t) {
                MsaaFramebuffer retired = msaa;
                CombatantRenderSystem.rhi().msaa().abandonTarget(retired);
                CombatantRenderSystem.resources().retire(retired::destroyBuffers);
                msaa = null;
                samples = 0;
                allocationRequestSamples = 0;
                width = 0;
                height = 0;
                state = "resize_failed";
                DebugLog.warnOnChange("msaa.world.resize_failed", sampleCount + "|" + t.getClass().getSimpleName() + "|" + t.getMessage(),
                        "[MSAA] world MSAA resize failed; disabling target. requested=%d size=%dx%d error=%s: %s",
                        sampleCount, w, h, t.getClass().getSimpleName(), t.getMessage());
            }
        }
    }

    public static boolean isActive() {
        return active && msaa != null;
    }

    private static RenderTarget rawMain(Minecraft mc) {
        return mc != null && mc.gameRenderer != null
                ? ((GameRendererAccessor) mc.gameRenderer).combatant$getMainRenderTargetRaw()
                : null;
    }

    public static RenderTarget getFramebufferOverride() {
        return active ? msaa : null;
    }

    public static MsaaFramebuffer getMsaaFramebuffer() {
        return msaa;
    }

    public static int getSamples() {
        return msaa != null ? msaa.getSamples() : 0;
    }

    public static DebugStatus debugStatus() {
        return new DebugStatus(
                requestedSamples,
                samples,
                width,
                height,
                supported,
                active,
                msaa != null,
                lastResolveOk,
                state
        );
    }

    public record DebugStatus(int requestedSamples,
                              int targetSamples,
                              int width,
                              int height,
                              boolean supported,
                              boolean active,
                              boolean allocated,
                              boolean lastResolveOk,
                              String state) {
    }
}
