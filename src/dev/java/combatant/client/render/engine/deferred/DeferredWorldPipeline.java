/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.world.WorldRenderState;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.resources.asset.AssetAutoLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Transitional renderer-service owner.
 *
 * <p>Minecraft/Sodium/Iris own world geometry and lighting. This class only keeps the reusable
 * lifecycle, camera/motion capture, temporal history and post-processing boundary until those
 * services are moved to backend-neutral owners.</p>
 */
public final class DeferredWorldPipeline {
    private volatile boolean requestedEnabled = Boolean.parseBoolean(
            System.getProperty("combatant.render.deferred", "false")
    );
    private volatile boolean activationFailureLatched;
    private volatile LifecycleState lifecycleState = LifecycleState.DISABLED;

    private long frameStateId = Long.MIN_VALUE;
    private DeferredRuntimeConfig.Snapshot frameSettings = DeferredRuntimeConfig.current();
    private long frameSettingsGeneration = DeferredRuntimeConfig.generation();
    private long temporalPolicyGeneration = Long.MIN_VALUE;
    private int temporalWidth = -1;
    private int temporalHeight = -1;
    private int temporalOutputWidth = -1;
    private int temporalOutputHeight = -1;
    private int temporalSamples = -1;
    private @Nullable Object worldOwner;

    private final AtomicReference<DeferredHistoryResetReason> pendingExternalHistoryReset =
            new AtomicReference<>(DeferredHistoryResetReason.NONE);
    private final DeferredResourceBindings resourceBindings = new DeferredResourceBindings();
    private final DeferredSecondaryViewRegistry secondaryViews = new DeferredSecondaryViewRegistry();
    private final DeferredPrimaryViewSource primaryView = new DeferredPrimaryViewSource();
    private final DeferredTemporalHistoryRegistry temporalHistory = new DeferredTemporalHistoryRegistry();
    private final DeferredObjectMotionTracker objectMotion = new DeferredObjectMotionTracker();
    private final DeferredWorldRenderStateSource worldStateSource = new DeferredWorldRenderStateSource();
    private WorldRenderState worldRenderState = WorldRenderState.unknown(0L);

    public boolean enabled() {
        return lifecycleState == LifecycleState.ACTIVE;
    }

    public boolean requestedEnabled() {
        return requestedEnabled;
    }

    public void requestEnabled(boolean enabled) {
        if (!enabled) {
            requestedEnabled = false;
            activationFailureLatched = false;
            return;
        }
        if (!activationFailureLatched) requestedEnabled = true;
    }

    public DeferredSmokeTestState smokeTestState() {
        return DeferredSmokeTestState.global();
    }

    public void setFeatureOverride(DeferredFeature feature, DeferredFeatureOverride override) {
        DeferredSmokeTestState.global().setFeatureOverride(feature, override);
    }

    public void clearFeatureOverrides() {
        DeferredSmokeTestState.global().clearFeatureOverrides();
    }

    public void setSmokeIsolationMode(boolean enabled) {
        DeferredSmokeTestState.global().setIsolationMode(enabled);
    }

    public void setDebugView(DeferredDebugView view) {
        DeferredSmokeTestState.global().setDebugView(view);
    }

    public void setDebugVolumeSlice(DeferredDebugVolumeAxis axis, float normalizedPosition) {
        DeferredSmokeTestState.global().setVolumeSlice(axis, normalizedPosition);
    }

    public DeferredDebugDiagnostics debugDiagnostics() {
        DeferredSmokeTestState state = DeferredSmokeTestState.global();
        DeferredSmokeTestState.Snapshot smoke = state.snapshot();
        DeferredHistoryResetReason primaryReason = primaryView.historyDescriptor().resetReason();
        DeferredHistoryResetReason resetReason = primaryReason != null && primaryReason != DeferredHistoryResetReason.NONE
                ? primaryReason : state.lastHistoryResetReason();
        String backend = "unknown";
        try {
            String candidate = CombatantRenderSystem.rhi().capabilities().backendName();
            if (candidate != null && !candidate.isBlank()) backend = candidate;
        } catch (Throwable ignored) { }
        return new DeferredDebugDiagnostics(
                lifecycleState, backend, smoke.debugView(), smoke.overrides(), smoke.isolationMode(),
                smoke.volumeAxis(), smoke.volumeSlice(), resetReason, state.unavailableDebugResourceReason(),
                worldStateSource.diagnostics(), resourceBindings.provenanceSnapshot()
        );
    }

    public void requestHistoryReset(DeferredHistoryResetReason reason) {
        if (reason == null || reason == DeferredHistoryResetReason.NONE) return;
        if (!requestedEnabled && !enabled()) return;
        pendingExternalHistoryReset.getAndUpdate(current -> DeferredHistoryResetReason.merge(current, reason));
    }

    public LifecycleState lifecycleState() {
        return lifecycleState;
    }

    /** Commit renderer-service activation/deactivation only at a clean frame boundary. */
    public void serviceRuntimeLifecycle() {
        RenderSystem.assertOnRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        boolean inWorld = minecraft != null && minecraft.level != null;
        if (lifecycleState == LifecycleState.FAILED) {
            recoverFailedRuntime(minecraft);
            if (lifecycleState == LifecycleState.FAILED) return;
        }
        if (requestedEnabled && inWorld && lifecycleState == LifecycleState.DISABLED) {
            activateRuntime(minecraft);
        } else if ((!requestedEnabled || !inWorld) && lifecycleState == LifecycleState.ACTIVE) {
            deactivateRuntime(minecraft);
        }
    }

    private void recoverFailedRuntime(@Nullable Minecraft minecraft) {
        try {
            releasePhysicalResources();
            DevDeferredRuntime.graph().releaseBackendResources(CombatantRenderSystem.rhi());
            if (AssetAutoLoader.isScopeActive(DeferredRuntimeAssets.SCOPE)) {
                if (minecraft == null) throw new IllegalStateException("Minecraft unavailable during renderer cleanup");
                AssetAutoLoader.deactivate(DeferredRuntimeAssets.SCOPE, minecraft.getResourceManager());
            }
            lifecycleState = LifecycleState.DISABLED;
        } catch (Throwable cleanupError) {
            lifecycleState = LifecycleState.FAILED;
            DebugLog.warnOnChange(
                    "combatant.renderer.services.recovery.failed",
                    cleanupError.getClass().getSimpleName() + "|" + cleanupError.getMessage(),
                    "[RendererServices] failed-state cleanup will retry: %s: %s",
                    cleanupError.getClass().getSimpleName(), cleanupError.getMessage()
            );
        }
    }

    private void activateRuntime(Minecraft minecraft) {
        lifecycleState = LifecycleState.ACTIVATING;
        try {
            AssetAutoLoader.activate(DeferredRuntimeAssets.SCOPE, minecraft.getResourceManager());
            resetFrameState();
            activationFailureLatched = false;
            lifecycleState = LifecycleState.ACTIVE;
        } catch (Throwable error) {
            requestedEnabled = false;
            activationFailureLatched = true;
            lifecycleState = LifecycleState.FAILED;
            DebugLog.error("[RendererServices] activation failed", error);
        }
    }

    public void onBackendChanged() {
        if (!enabled()) return;
        RenderSystem.assertOnRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        try {
            DeferredRuntimeAssets.reprepareAfterBackendSwitch(minecraft.getResourceManager());
            DevDeferredRuntime.graph().releasePhysicalResources(CombatantRenderSystem.rhi());
            resourceBindings.detachFrameGraph();
            primaryView.queueHistoryReset(DeferredHistoryResetReason.BACKEND_RECREATION);
        } catch (Throwable error) {
            requestedEnabled = false;
            activationFailureLatched = true;
            lifecycleState = LifecycleState.FAILED;
            releasePhysicalResources();
            DebugLog.error("[RendererServices] backend reprepare failed", error);
        }
    }

    public void shutdownRuntime() {
        RenderSystem.assertOnRenderThread();
        requestedEnabled = false;
        activationFailureLatched = false;
        releasePhysicalResources();
        Minecraft minecraft = Minecraft.getInstance();
        try {
            if (AssetAutoLoader.isScopeActive(DeferredRuntimeAssets.SCOPE)) {
                if (minecraft == null) throw new IllegalStateException("Minecraft unavailable during renderer shutdown");
                AssetAutoLoader.deactivate(DeferredRuntimeAssets.SCOPE, minecraft.getResourceManager());
            }
            lifecycleState = LifecycleState.DISABLED;
        } catch (Throwable error) {
            lifecycleState = LifecycleState.FAILED;
            DebugLog.error("[RendererServices] shutdown cleanup failed", error);
        }
    }

    private void deactivateRuntime(Minecraft minecraft) {
        lifecycleState = LifecycleState.DEACTIVATING;
        releasePhysicalResources();
        try {
            if (AssetAutoLoader.isScopeActive(DeferredRuntimeAssets.SCOPE)) {
                AssetAutoLoader.deactivate(DeferredRuntimeAssets.SCOPE, minecraft.getResourceManager());
            }
            lifecycleState = LifecycleState.DISABLED;
        } catch (Throwable error) {
            requestedEnabled = false;
            activationFailureLatched = true;
            lifecycleState = LifecycleState.FAILED;
            DebugLog.error("[RendererServices] deactivation failed", error);
        }
    }

    private void beginFrameState() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel currentWorld = minecraft != null ? minecraft.level : null;
        if (worldOwner != currentWorld) {
            DevDeferredRuntime.graph().releasePhysicalResources(CombatantRenderSystem.rhi());
            resourceBindings.detachFrameGraph();
            objectMotion.reset();
            DeferredTemporalCoverageBridge.reset();
            primaryView.beginWorld(currentWorld);
            worldStateSource.reset();
            worldRenderState = worldStateSource.current();
            worldOwner = currentWorld;
            frameStateId = Long.MIN_VALUE;
            temporalWidth = temporalHeight = temporalOutputWidth = temporalOutputHeight = temporalSamples = -1;
        }

        long frameId = CombatantRenderSystem.ensureFrameContext().frameId();
        if (frameStateId == frameId) return;
        frameStateId = frameId;
        DeferredSmokeTestState.global().beginFrame(frameId);
        DeferredHistoryResetReason externalReset = pendingExternalHistoryReset.getAndSet(DeferredHistoryResetReason.NONE);
        if (externalReset != DeferredHistoryResetReason.NONE) primaryView.invalidateHistory(frameId, externalReset);

        frameSettings = DeferredRuntimeConfig.current();
        frameSettingsGeneration = DeferredRuntimeConfig.generation();
        if (temporalPolicyGeneration != Long.MIN_VALUE && temporalPolicyGeneration != frameSettingsGeneration) {
            primaryView.invalidateHistory(frameId, DeferredHistoryResetReason.POLICY_CHANGE);
        }
        temporalPolicyGeneration = frameSettingsGeneration;
        worldRenderState = worldStateSource.capture(currentWorld, primaryView.current());

        DeferredHistoryDescriptor history = primaryView.historyDescriptor();
        resourceBindings.beginFrame(frameId, history.epoch());
        temporalHistory.beginFrame(frameId, history, resourceBindings, frameSettings);
        for (DeferredFeature feature : DeferredSmokeTestState.global().consumePendingTemporalResetFeatures()) {
            for (DeferredTemporalHistoryId historyId : feature.temporalHistories()) {
                temporalHistory.invalidate(historyId, DeferredHistoryResetReason.SUBSYSTEM_REENABLED);
            }
        }
        objectMotion.beginFrame(frameId, history.epoch());
        secondaryViews.beginFrame(frameId);
    }

    private void bindSceneFrame(GpuTextureView sceneColor, @Nullable GpuTextureView depth) {
        beginFrameState();
        int width = Math.max(1, sceneColor.getWidth(0));
        int height = Math.max(1, sceneColor.getHeight(0));
        Minecraft minecraft = Minecraft.getInstance();
        int outputWidth = minecraft != null && minecraft.getWindow() != null
                ? Math.max(1, minecraft.getWindow().getWidth()) : width;
        int outputHeight = minecraft != null && minecraft.getWindow() != null
                ? Math.max(1, minecraft.getWindow().getHeight()) : height;
        int samples = depth != null && depth.texture() instanceof IMsaaTexture msaa
                ? Math.max(1, msaa.combatant$getSamples()) : 1;

        if (temporalWidth >= 0) {
            if (temporalOutputWidth != outputWidth || temporalOutputHeight != outputHeight) {
                primaryView.invalidateHistory(frameStateId, DeferredHistoryResetReason.RESIZE);
            } else if (temporalWidth != width || temporalHeight != height) {
                primaryView.invalidateHistory(frameStateId, DeferredHistoryResetReason.RENDER_SCALE_CHANGE);
            } else if (temporalSamples != samples) {
                primaryView.invalidateHistory(frameStateId, DeferredHistoryResetReason.POLICY_CHANGE);
            }
        }
        temporalWidth = width;
        temporalHeight = height;
        temporalOutputWidth = outputWidth;
        temporalOutputHeight = outputHeight;
        temporalSamples = samples;
        resourceBindings.setOutputResolution(outputWidth, outputHeight);
        primaryView.updateResolutions(frameStateId, width, height, outputWidth, outputHeight);
        resourceBindings.bindTexture(DeferredResource.SCENE_COLOR, sceneColor);
        resourceBindings.bindTexture(DeferredResource.MAIN_DEPTH, depth);
    }

    public void beforePostProcess(GpuTextureView sceneColor, @Nullable GpuTextureView depth) {
        if (!enabled() || sceneColor == null) return;
        bindSceneFrame(sceneColor, depth);
        executeStage(DeferredStage.POST_TRANSLUCENCY);
        executeStage(DeferredStage.TEMPORAL_RESOLVE);
        executeStage(DeferredStage.PRE_POST_PROCESS);
    }

    public void afterPostProcess(GpuTextureView sceneColor, @Nullable GpuTextureView depth) {
        if (!enabled() || sceneColor == null) return;
        bindSceneFrame(sceneColor, depth);
        executeStage(DeferredStage.POST_PROCESS);
    }

    public void finalComposite(GpuTextureView sceneColor, @Nullable GpuTextureView depth) {
        if (!enabled() || sceneColor == null) return;
        bindSceneFrame(sceneColor, depth);
        executeStage(DeferredStage.FINAL_COMPOSITE);
    }

    public void releasePhysicalResources() {
        RenderSystem.assertOnRenderThread();
        DevDeferredRuntime.graph().releasePhysicalResources(CombatantRenderSystem.rhi());
        resetFrameState();
    }

    private void resetFrameState() {
        resourceBindings.reset();
        secondaryViews.reset();
        worldOwner = null;
        frameStateId = Long.MIN_VALUE;
        temporalPolicyGeneration = Long.MIN_VALUE;
        temporalWidth = temporalHeight = temporalOutputWidth = temporalOutputHeight = temporalSamples = -1;
        pendingExternalHistoryReset.set(DeferredHistoryResetReason.NONE);
        primaryView.reset();
        temporalHistory.reset(DeferredHistoryResetReason.RENDERER_RESET);
        objectMotion.reset();
        DeferredTemporalCoverageBridge.reset();
        worldStateSource.reset();
        worldRenderState = worldStateSource.current();
    }

    public @Nullable DeferredMotionState captureEntityMotion(
            net.minecraft.world.entity.Entity entity,
            net.minecraft.client.renderer.entity.state.EntityRenderState state) {
        if (!enabled() || entity == null || state == null) return null;
        beginFrameState();
        DeferredHistoryDescriptor history = primaryView.historyDescriptor();
        objectMotion.beginFrame(frameStateId, history.epoch());
        return objectMotion.captureEntity(entity, state);
    }

    public @Nullable DeferredMotionState captureBlockEntityMotion(
            net.minecraft.world.level.block.entity.BlockEntity blockEntity,
            net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState state) {
        if (!enabled() || blockEntity == null || state == null) return null;
        beginFrameState();
        DeferredHistoryDescriptor history = primaryView.historyDescriptor();
        objectMotion.beginFrame(frameStateId, history.epoch());
        return objectMotion.captureBlockEntity(blockEntity, state);
    }

    public void capturePrimaryView(long frameId,
                                   org.joml.Matrix4fc view,
                                   org.joml.Matrix4fc jitteredProjection,
                                   org.joml.Matrix4fc unjitteredProjection,
                                   org.joml.Vector2fc jitter,
                                   @Nullable net.minecraft.world.phys.Vec3 cameraPosition,
                                   float farPlane) {
        if (!enabled()) return;
        Minecraft minecraft = Minecraft.getInstance();
        primaryView.beginWorld(minecraft != null ? minecraft.level : null);
        primaryView.capture(frameId, view, jitteredProjection, unjitteredProjection, jitter, cameraPosition, farPlane);
    }

    private void executeStage(DeferredStage stage) {
        DeferredHistoryDescriptor history = primaryView.historyDescriptor();
        resourceBindings.setHistoryEpoch(history.epoch());
        temporalHistory.beginFrame(frameStateId, history, resourceBindings, frameSettings);
        DevDeferredRuntime.graph().execute(
                stage, CombatantRenderSystem.ensureFrameContext(), resourceBindings, secondaryViews,
                primaryView, temporalHistory, worldRenderState, frameSettings
        );
    }

    public enum LifecycleState {
        DISABLED,
        ACTIVATING,
        ACTIVE,
        DEACTIVATING,
        FAILED
    }
}
