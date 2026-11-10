/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import org.joml.Vector4fc;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.world.WorldRenderState;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.resources.asset.AssetAutoLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

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
    private final DeferredMrtCapture geometryCapture = new DeferredMrtCapture();
    private long geometryPipelineGeneration;
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

    /** Sodium 0.9.2 / RenderPearl 26.3 geometry contract. No Iris dependency. */
    public boolean geometryCaptureEnabled() {
        // MRT attachments must match the main target sample count; capture currently uses single-sample images.
        return enabled() && !combatant.client.render.iris.IrisRuntime.isModLoaded()
                && !combatant.client.render.engine.msaa.MsaaWorldTarget.isActive()
                && geometryCapture.canCapture();
    }
    public void setDebugGbuffer(boolean enabled) { geometryCapture.setDebugEnabled(enabled); }
    public long geometryPipelineGeneration() { return geometryPipelineGeneration; }
    public void configureGeometryPipeline(RenderPipeline.Builder builder) {
        if (geometryCaptureEnabled()) geometryCapture.configureSodiumPipeline(builder);
    }
    public RenderPass openGeometryPass(CommandEncoder encoder, Supplier<String> label,
                                       GpuTextureView sceneColor, Optional<Vector4fc> clearColor,
                                       GpuTextureView depth, OptionalDouble clearDepth) {
        return geometryCapture.openGeometryPass(encoder, label, sceneColor, clearColor, depth, clearDepth);
    }
    private LightingState lightingState = new LightingState(0, 0, 0, 0,
            Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
    private long lightingFrameId = Long.MIN_VALUE;

    public void captureLightingState(LightingState state) { lightingState = state; }
    public LightingState lightingState() { return lightingState; }

    public void finishOpaqueGeometry(GpuTextureView sceneColor) {
        if (!geometryCaptureEnabled() || geometryCapture.surface() == null) return;
        bindSceneFrame(sceneColor, geometryCapture.sceneDepth());
        resourceBindings.bindTexture(DeferredResource.GBUFFER_SURFACE, geometryCapture.surface());
        resourceBindings.bindTexture(DeferredResource.GBUFFER_GEOMETRY, geometryCapture.geometry());
        resourceBindings.bindTexture(DeferredResource.GBUFFER_MATERIAL, geometryCapture.material());
        resourceBindings.bindTexture(DeferredResource.GBUFFER_MATERIAL_ID, geometryCapture.materialId());
        resourceBindings.bindTexture(DeferredResource.GBUFFER_AUXILIARY, geometryCapture.auxiliary());
        DevDeferredRuntime.graph().completeExternalPass("world.geometry.capture", resourceBindings);
        for (DeferredStage stage : new DeferredStage[] {
                DeferredStage.FRAME_SETUP, DeferredStage.SHADOW_PREPARE, DeferredStage.SHADOW_MAP,
                DeferredStage.PRE_GEOMETRY_COMPUTE, DeferredStage.POST_GEOMETRY_COMPUTE,
                DeferredStage.DEPTH_RESOLVE, DeferredStage.VELOCITY_RESOLVE, DeferredStage.DEPTH_PYRAMID,
                DeferredStage.AMBIENT_OCCLUSION, DeferredStage.PRE_LIGHTING,
                DeferredStage.LIGHTING, DeferredStage.POST_LIGHTING}) {
            executeStage(stage);
        }
        if (resourceBindings.isValid(DeferredResource.DIRECT_LIGHTING_COLOR)) lightingFrameId = frameStateId;
    }

    public void finishForwardOpaque(GpuTextureView sceneColor, GpuTextureView depth) {
        if (!lightingReady()) return;
        bindSceneFrame(sceneColor, depth);
        DevDeferredRuntime.graph().completeExternalPass("world.forward.opaque", resourceBindings);
        for (DeferredStage stage : new DeferredStage[] {
                DeferredStage.FORWARD_OPAQUE, DeferredStage.PRE_TRANSLUCENCY_DEPTH_RESOLVE,
                DeferredStage.PRE_TRANSLUCENCY_VELOCITY_RESOLVE, DeferredStage.PRE_TRANSLUCENCY_DEPTH_PYRAMID,
                DeferredStage.PRE_TRANSLUCENCY_TEMPORAL_VALIDATION, DeferredStage.RADIANCE_CAPTURE,
                DeferredStage.INDIRECT_PREPARE, DeferredStage.INDIRECT_TRACE, DeferredStage.INDIRECT_TEMPORAL,
                DeferredStage.INDIRECT_HISTORY, DeferredStage.INDIRECT_COMPOSITE,
                DeferredStage.REFLECTION_PREPARE, DeferredStage.REFLECTION_TRACE, DeferredStage.REFLECTION_RESOLVE,
                DeferredStage.REFLECTION_TEMPORAL, DeferredStage.REFLECTION_DENOISE,
                DeferredStage.REFLECTION_HISTORY, DeferredStage.REFLECTION_COMPOSITE,
                DeferredStage.PRE_TRANSLUCENCY}) {
            executeStage(stage);
        }
    }

    private boolean lightingReady() {
        return enabled() && lightingFrameId == CombatantRenderSystem.ensureFrameContext().frameId();
    }
    public GpuTextureView gbufferSurface() { return geometryCapture.surface(); }
    public GpuTextureView gbufferGeometry() { return geometryCapture.geometry(); }
    public GpuTextureView gbufferAuxiliary() { return geometryCapture.auxiliary(); }
    public GpuTextureView gbufferMaterial() { return geometryCapture.material(); }
    public GpuTextureView gbufferMaterialId() { return geometryCapture.materialId(); }

    /** Fog values at the opaque -> feature boundary; kept independent of old shaderpack fog. */
    public record LightingState(float fogRed, float fogGreen, float fogBlue, float fogAlpha,
                                float environmentalFogStart, float environmentalFogEnd,
                                float renderFogStart, float renderFogEnd) { }

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
            if (enabled()) geometryPipelineGeneration++;
        } else if ((!requestedEnabled || !inWorld) && lifecycleState == LifecycleState.ACTIVE) {
            deactivateRuntime(minecraft);
            geometryPipelineGeneration++;
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
        if (!lightingReady() || sceneColor == null) return;
        bindSceneFrame(sceneColor, depth);
        executeStage(DeferredStage.POST_TRANSLUCENCY);
        executeStage(DeferredStage.TEMPORAL_RESOLVE);
        executeStage(DeferredStage.PRE_POST_PROCESS);
    }

    public void afterPostProcess(GpuTextureView sceneColor, @Nullable GpuTextureView depth) {
        if (!lightingReady() || sceneColor == null) return;
        bindSceneFrame(sceneColor, depth);
        executeStage(DeferredStage.POST_PROCESS);
    }

    public void finalComposite(GpuTextureView sceneColor, @Nullable GpuTextureView depth) {
        if (!lightingReady() || sceneColor == null) return;
        bindSceneFrame(sceneColor, depth);
        executeStage(DeferredStage.FINAL_COMPOSITE);
        geometryCapture.publishDebug(sceneColor);
    }

    public void releasePhysicalResources() {
        geometryCapture.reset();
        RenderSystem.assertOnRenderThread();
        DevDeferredRuntime.graph().releasePhysicalResources(CombatantRenderSystem.rhi());
        resetFrameState();
    }

    private void resetFrameState() {
        lightingFrameId = Long.MIN_VALUE;
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
