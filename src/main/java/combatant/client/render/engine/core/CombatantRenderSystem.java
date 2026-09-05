/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.core;

import com.mojang.blaze3d.systems.RenderSystem;
import combatant.client.render.engine.compat.immediatelyfast.ImmediatelyFastRuntime;
import combatant.client.render.engine.compat.immediatelyfast.ImmediatelyFastRuntimeSnapshot;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.renderer.ui.ItemBatchRenderer;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.render.iris.IrisRuntimeSnapshot;
import combatant.client.render.sodium.SodiumTerrainInteropStatsSnapshot;
import combatant.client.render.sodium.SodiumVisibilityStatsSnapshot;
import org.joml.Matrix4f;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.policy.LightPolicy;
import combatant.client.render.engine.core.policy.VanillaWorldFogProvider;
import combatant.client.render.engine.depth.WorldSceneDepth;
import combatant.client.render.engine.framegraph.CombatantFrameGraph;
import combatant.client.render.engine.profiler.FrameStutterProfiler;
import combatant.client.render.engine.profiler.RenderFrameProfiler;
import combatant.client.render.engine.profiler.TracyProfiler;
import combatant.client.render.engine.profiler.UiPipelineTelemetry;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.RhiStatsSnapshot;
import combatant.client.render.engine.rhi.backend.SodiumGlBackend;
import combatant.client.render.engine.rhi.backend.vulkan.CombatantVulkanBackend;
import combatant.client.render.engine.rhi.resource.RenderResourceManager;
import combatant.client.render.engine.rhi.resource.RenderResourceStatsSnapshot;
import combatant.client.render.engine.rhi.uniform.CombatantUniformAllocator;
import combatant.client.render.engine.rhi.uniform.UniformAllocatorStatsSnapshot;
import combatant.client.render.engine.text.TextCommandStatsSnapshot;
import combatant.client.render.engine.text.TextRenderSystem;
import combatant.client.render.engine.renderer.ui.clip.UiMsaaClipLayer;
import combatant.client.render.engine.world.WorldRenderStatsSnapshot;
import combatant.client.render.sodium.SodiumFrameContext;
import combatant.client.render.sodium.SodiumRenderBridge;
import combatant.client.util.logging.DebugLog;

import java.util.Locale;

/**
 * Single owner of Combatant render lifecycle.
 * - one Combatant frame per Minecraft-presented frame;
 * - many scoped RenderPhase entries inside that frame;
 * - backend submission is flushed before framePresented(), not by random renderer classes;
 * - RenderState is a compatibility shim, not the lifecycle owner.
 */
public enum CombatantRenderSystem {
    ;
    private static final SodiumRenderBridge SODIUM = new SodiumRenderBridge();
    private static final CombatantFrameGraph FRAME_GRAPH = new CombatantFrameGraph();
    private static final CombatantUniformAllocator UNIFORMS = new CombatantUniformAllocator();

    private static CombatantRhi rhi;
    private static BackendKind backendKind = BackendKind.UNKNOWN;
    private static RenderFrameContext currentContext;
    private static long frameId;
    private static boolean initialized;
    private static boolean frameOpen;
    private static boolean submissionEnded;
    private static FrameLifecycle lifecycle = FrameLifecycle.IDLE;

    private enum BackendKind {
        UNKNOWN,
        GL,
        VULKAN
    }

    public static void init() {
        if (initialized) {
            ensureBackendMatchesDevice();
            return;
        }
        BackendKind desired = detectBackendKind();
        backendKind = desired == BackendKind.UNKNOWN ? BackendKind.GL : desired;
        rhi = createBackend(backendKind);
        initialized = true;
        DebugLog.renderThreadOnChange(
                "combatant.rhi.backend",
                backendKind,
                "[CombatantRHI] backend initialized: %s",
                backendKind
        );
        ensureBackendMatchesDevice();
    }

    private static CombatantRhi createBackend(BackendKind kind) {
        if (kind == BackendKind.VULKAN) {
            return new CombatantVulkanBackend();
        }
        return new SodiumGlBackend();
    }

    private static void ensureBackendMatchesDevice() {
        if (!initialized || rhi == null) return;
        BackendKind desired = detectBackendKind();
        if (desired == BackendKind.UNKNOWN || desired == backendKind) return;

        if (frameOpen) {
            DebugLog.warnOnChange(
                    "combatant.rhi.backend.switch.deferred",
                    backendKind + "->" + desired + "|frameOpen",
                    "[CombatantRHI] backend switch deferred until frame end: %s -> %s",
                    backendKind,
                    desired
            );
            return;
        }

        CombatantRhi previous = rhi;
        BackendKind previousKind = backendKind;
        CombatantRhi next;
        try {
            next = createBackend(desired);
        } catch (Throwable t) {
            DebugLog.error("[CombatantRHI] failed to create backend " + desired, t);
            return;
        }

        rhi = next;
        backendKind = desired;
        try {
            previous.close();
        } catch (Throwable t) {
            DebugLog.warnOnChange(
                    "combatant.rhi.backend.old.close.failed",
                    previousKind + "|" + desired + "|" + t.getClass().getSimpleName(),
                    "[CombatantRHI] old backend close failed during switch %s -> %s: %s: %s",
                    previousKind,
                    desired,
                    t.getClass().getSimpleName(),
                    t.getMessage()
            );
        }
        DebugLog.renderThreadOnChange(
                "combatant.rhi.backend",
                backendKind,
                "[CombatantRHI] backend switched: %s -> %s",
                previousKind,
                backendKind
        );
    }

    private static BackendKind detectBackendKind() {
        String backendName = null;
        try {
            var device = RenderSystem.tryGetDevice();
            if (device != null && device.getDeviceInfo() != null) {
                backendName = device.getDeviceInfo().backendName();
            }
        } catch (Throwable ignored) {
            // Fall through to RenderSystem backend description.
        }

        if (backendName == null || backendName.isBlank()) {
            try {
                backendName = RenderSystem.getBackendDescription();
            } catch (Throwable ignored) {
                backendName = null;
            }
        }

        if (backendName == null || backendName.isBlank()) return BackendKind.UNKNOWN;
        String normalized = backendName.toLowerCase(Locale.ROOT);
        if (normalized.contains("vulkan")) return BackendKind.VULKAN;
        if (normalized.contains("opengl") || normalized.contains("gl")) return BackendKind.GL;
        return BackendKind.UNKNOWN;
    }

    public static CombatantRhi rhi() {
        init();
        ensureBackendMatchesDevice();
        return rhi;
    }

    public static SodiumRenderBridge sodium() {
        return SODIUM;
    }

    public static RenderResourceManager resources() {
        return rhi().resources();
    }

    public static CombatantFrameGraph frameGraph() {
        return FRAME_GRAPH;
    }

    public static CombatantUniformAllocator uniforms() {
        return UNIFORMS;
    }

    public static RenderFrameContext currentContext() {
        return currentContext;
    }

    public static FrameLifecycle lifecycle() {
        return lifecycle;
    }

    /**
     * Opens the Combatant frame if needed, otherwise refreshes camera/viewport data without advancing frameId.
     */
    public static RenderFrameContext beginFrame(float tickDelta, Matrix4f projection, Matrix4f modelView) {
        return beginFrame(tickDelta, tickDelta, tickDelta, projection, modelView);
    }

    public static RenderFrameContext beginFrame(float tickProgress,
                                                float frameDeltaTicks,
                                                float fixedDeltaTicks,
                                                Matrix4f projection,
                                                Matrix4f modelView) {
        CombatantRhi activeRhi = rhi();
        SodiumFrameContext sodiumFrame;
        if (!frameOpen) {
            frameId++;
            UiPipelineTelemetry.beginFrame(frameId);
            activeRhi.stats().setDetailedPipelineStats(TracyProfiler.isEnabled());
            activeRhi.beginFrame(frameId);
            UNIFORMS.beginFrame(frameId);
            TextRenderSystem.beginFrame();
            sodiumFrame = SODIUM.beginFrame();
            RenderFrameProfiler.beginFrame(frameId);
            frameOpen = true;
            submissionEnded = false;
            lifecycle = FrameLifecycle.RECORDING;
        } else {
            sodiumFrame = SODIUM.currentFrameContext();
        }

        RenderPhase phase = currentContext != null ? currentContext.phase() : RenderPhase.NONE;
        RenderFrameContext ctx = new RenderFrameContext(
                frameId,
                tickProgress,
                frameDeltaTicks,
                fixedDeltaTicks,
                phase,
                CameraContext.capture(projection, modelView),
                ViewportContext.capture(),
                FramebufferContext.capture(),
                WorldSceneDepth::mainDepthView,
                VanillaWorldFogProvider.INSTANCE,
                LightPolicy.VANILLA,
                SODIUM.visibilityProvider(),
                sodiumFrame
        );
        currentContext = ctx;
        RenderState.applyContext(ctx);
        return ctx;
    }

    /**
     * Refreshes only timing data for phases that do not own the world camera/projection
     * themselves, for example Fabric HUD callbacks.
     */
    public static RenderFrameContext updateFrameTiming(float tickProgress,
                                                       float frameDeltaTicks,
                                                       float fixedDeltaTicks) {
        RenderFrameContext base = ensureFrameContext();
        currentContext = base.withTiming(tickProgress, frameDeltaTicks, fixedDeltaTicks);
        RenderState.applyContext(currentContext);
        return currentContext;
    }

    public static RenderFrameContext ensureFrameContext() {
        if (currentContext != null) return currentContext;
        return beginFrame(RenderState.tickProgress, RenderState.frameDeltaTicks, RenderState.fixedDeltaTicks, new Matrix4f(), new Matrix4f(RenderSystem.getModelViewStack()));
    }

    public static RenderPhaseScope phase(RenderPhase phase) {
        return phase(phase, null);
    }

    public static RenderPhaseScope phase(RenderPhase phase, String label) {
        RenderFrameContext before = currentContext;
        RenderFrameContext base = ensureFrameContext();
        RenderPhase next = phase == null ? RenderPhase.NONE : phase;
        currentContext = base.withPhase(next);
        RenderState.applyContext(currentContext);
        return new RenderPhaseScope(next, before, RenderFrameProfiler.phase(next, label));
    }

    static void restorePhase(RenderFrameContext previous) {
        currentContext = previous;
        if (currentContext != null) {
            RenderState.applyContext(currentContext);
        } else {
            RenderState.clearContextBackedState();
        }
    }

    public static void endRenderSubmission() {
        if (!initialized || !frameOpen || submissionEnded) return;
        try {
            ItemBatchRenderer.finishUiItemFrame();
            TextRenderSystem.flush();
            rhi.endRenderSubmission();
            lifecycle = FrameLifecycle.SUBMITTED;
        } finally {
            submissionEnded = true;
        }
    }

    /**
     * Called after the window surface has been presented. Rendering submission must already
     * be closed before GpuSurface.blitFromTexture(), otherwise late Combatant draws miss the
     * presented frame and can leave stale main-target contents for the next frame.
     */
    public static void onFramePresented() {
        if (!initialized) return;
        try {
            if (!submissionEnded) {
                // Fallback for unusual renderFrame paths. Normal visible frames close in
                // GpuSurfaceMixin#combatant$beforeSurfaceBlit, before the surface copy.
                endRenderSubmission();
            }
            rhi.framePresented();
            UNIFORMS.onFramePresented();
            if (FrameStutterProfiler.isEnabled()) {
                FrameStutterProfiler.onFramePresented(rhiStatsSnapshot(), uniformStatsSnapshot(), resourceStatsSnapshot());
            }
            RhiStatsSnapshot rhiSnapshot = rhi().stats().snapshot(TracyProfiler.isEnabled());
            TracyProfiler.plotUiPipeline(UiPipelineTelemetry.snapshot());
            TracyProfiler.plotRhiPipeline(rhiSnapshot);
            TracyProfiler.plotRenderResources(resourceStatsSnapshot());
            RenderFrameProfiler.endFrame(rhiSnapshot, uniformStatsSnapshot());
            lifecycle = FrameLifecycle.PRESENTED;
        } finally {
            currentContext = null;
            frameOpen = false;
            submissionEnded = false;
            lifecycle = FrameLifecycle.IDLE;
            RenderState.clearContextBackedState();
        }
    }

    public static RhiStatsSnapshot rhiStatsSnapshot() {
        return rhi().stats().snapshot();
    }

    public static SodiumVisibilityStatsSnapshot sodiumVisibilityStatsSnapshot() {
        return SODIUM.visibilityStatsSnapshot();
    }

    public static SodiumTerrainInteropStatsSnapshot sodiumTerrainStatsSnapshot() {
        return SODIUM.terrainInterop().statsSnapshot();
    }

    public static IrisRuntimeSnapshot irisSnapshot() {
        return IrisRuntime.snapshot();
    }

    public static ImmediatelyFastRuntimeSnapshot immediatelyFastSnapshot() {
        return ImmediatelyFastRuntime.snapshot();
    }

    public static UniformAllocatorStatsSnapshot uniformStatsSnapshot() {
        return UNIFORMS.statsSnapshot();
    }

    public static RenderResourceStatsSnapshot resourceStatsSnapshot() {
        return resources().statsSnapshot();
    }

    public static WorldRenderStatsSnapshot worldRenderStatsSnapshot() {
        return Renderer3D.worldStatsSnapshot();
    }

    public static TextCommandStatsSnapshot textStatsSnapshot() {
        return TextRenderSystem.statsSnapshot();
    }

    public static void shutdown() {
        if (!initialized) return;
        try {
            UiMsaaClipLayer.shutdown();
            UNIFORMS.close();
            rhi.close();
        } finally {
            initialized = false;
            currentContext = null;
            frameOpen = false;
            submissionEnded = false;
            lifecycle = FrameLifecycle.IDLE;
            rhi = null;
            RenderState.clearContextBackedState();
        }
    }
}
