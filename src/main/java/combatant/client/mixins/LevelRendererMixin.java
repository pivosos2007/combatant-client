/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import combatant.client.config.MainConfig;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.module.ModuleManager;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.BlockHighlight;
import combatant.client.features.module.modules.visuals.MotionBlur;
import combatant.client.features.module.modules.visuals.ReimaginedVisual;
import combatant.client.features.module.modules.visuals.WorldTweaks;
import combatant.client.mixins.accessors.GameRendererAccessor;
import combatant.client.mixins.accessors.LevelRendererAccessor;
import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.asset.gltf.render.ImportedAssetCompatibilityRenderer;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderPhase;
import combatant.client.render.engine.core.RenderPhaseScope;
import combatant.client.render.engine.depth.PreTranslucentDepth;
import combatant.client.render.engine.depth.EntityDepthLayer;
import combatant.client.render.engine.depth.WorldSceneDepth;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.render.sky.CustomSkyboxRenderer;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {


    @Shadow
    @Final
    private LevelTargetBundle targets;

    @WrapOperation(
            method = "addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"
            )
    )
    private RenderTarget combatant$stableSkyRendererTarget(GameRenderer instance, Operation<RenderTarget> original) {
        return ((GameRendererAccessor) instance).combatant$getMainRenderTargetRaw();
    }

    @Unique
    private static void combatant$dependsOn(FramePass pass, ResourceHandle<RenderTarget> handle) {
        if (pass != null && handle != null) {
            pass.reads(handle);
        }
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static ResourceHandle<RenderTarget> combatant$asRenderTargetHandle(ResourceHandle<?> handle) {
        return (ResourceHandle<RenderTarget>) handle;
    }

    @Unique
    private static RenderTarget combatant$getFramebuffer(ResourceHandle<RenderTarget> handle) {
        if (handle == null) {
            return null;
        }
        try {
            return handle.get();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Unique
    private static boolean combatant$needsWorldSceneDepthCapture() {
        ReimaginedVisual visual = Modules.get(ReimaginedVisual.class);
        return (visual != null && visual.needsWorldSceneDepthCapture())
                || MotionBlur.isActiveStatic()
                || MainConfig.get().isTaaRuntimeActive();
    }

    @Unique
    private static boolean shouldSkipSky(net.minecraft.client.renderer.state.level.CameraRenderState cameraRenderState) {
        FogType submersion = cameraRenderState.fogType;
        if (submersion == FogType.POWDER_SNOW || submersion == FogType.LAVA) {
            return true;
        }
        return cameraRenderState.entityRenderState != null
                && cameraRenderState.entityRenderState.doesMobEffectBlockSky;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void combatant$captureIrisModelView(
            GraphicsResourceAllocator allocator,
            boolean renderBlockOutline,
            net.minecraft.client.renderer.state.level.CameraRenderState cameraRenderState,
            GpuBufferSlice fogBuffer,
            org.joml.Vector4f fogColor,
            boolean renderSky,
            boolean renderTerrain,
            CallbackInfo ci
    ) {
        org.joml.Matrix4fc positionMatrix = cameraRenderState != null ? cameraRenderState.viewRotationMatrix : null;
        if (positionMatrix == null) {
            return;
        }

        if (CombatantWorldMatrices.isValid()) {
            CombatantWorldMatrices.capturePosition(
                    positionMatrix,
                    cameraRenderState != null && cameraRenderState.pos != null
                            ? cameraRenderState.pos
                            : null
            );
        } else if (cameraRenderState != null && cameraRenderState.projectionMatrix != null) {
            CombatantWorldMatrices.capture(
                    positionMatrix,
                    cameraRenderState.projectionMatrix,
                    cameraRenderState.projectionMatrix,
                    cameraRenderState.pos
            );
        }

    }

    @Inject(
            method = "submitBlockOutline",
            at = @At("HEAD"),
            cancellable = true
    )
    private void combatant$disableVanillaOutline(PoseStack matrices,
                                                 SubmitNodeCollector consumers,
                                                 LevelRenderState renderState,
                                                 CallbackInfo ci) {
        BlockHighlight blockHighlight = Modules.get(BlockHighlight.class);
        if (blockHighlight != null && blockHighlight.isOutlineActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "submitFeatures", at = @At("HEAD"))
    private void combatant$snapshotEntityStates(LevelRenderState state,
                                                SubmitNodeCollector collector,
                                                boolean special, CallbackInfo ci) {
        if (ModuleManager.needsEntityOnlyDepth()) {
            EntityDepthLayer.snapshotEntities(state.entityRenderStates);
        } else {
            EntityDepthLayer.reset();
        }
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Z)V",
                    shift = At.Shift.AFTER
            )
    )
    private void combatant$captureWorldSceneDepth(
            GraphicsResourceAllocator allocator,
            boolean renderBlockOutline,
            net.minecraft.client.renderer.state.level.CameraRenderState cameraRenderState,
            GpuBufferSlice fogBuffer,
            org.joml.Vector4f fogColor,
            boolean renderSky,
            boolean extraFlag,
            CallbackInfo ci,
            @Local FrameGraphBuilder frameGraphBuilder
    ) {
        if (frameGraphBuilder == null || targets == null || targets.main == null) {
            EntityDepthLayer.reset();
            return;
        }

        ResourceHandle<RenderTarget> main = targets.main;
        if (ModuleManager.needsEntityOnlyDepth()) {
            FramePass entityDepthPass = frameGraphBuilder.addPass("combatant_entity_only_depth");
            combatant$dependsOn(entityDepthPass, main);
            entityDepthPass.disableCulling();
            entityDepthPass.executes(() -> {
                RenderTarget mainFramebuffer = combatant$getFramebuffer(main);
                Minecraft client = Minecraft.getInstance();
                RenderTarget fallbackFramebuffer = client != null ? client.gameRenderer.mainRenderTarget() : null;
                int width = mainFramebuffer != null ? mainFramebuffer.width : fallbackFramebuffer != null ? fallbackFramebuffer.width : 1;
                int height = mainFramebuffer != null ? mainFramebuffer.height : fallbackFramebuffer != null ? fallbackFramebuffer.height : 1;
                LevelRenderState state = ((LevelRendererAccessor) (Object) this).combatant$getWorldRenderState();
                EntityDepthLayer.capture((LevelRenderer) (Object) this, state, width, height);
            });
        } else {
            EntityDepthLayer.reset();
        }

        if (IrisRuntime.isShaderpackRendererActive()) {
            WorldSceneDepth.reset();
            return;
        }
        if (!combatant$needsWorldSceneDepthCapture()) {
            WorldSceneDepth.reset();
            return;
        }

        // Minecraft 26.3 no longer exposes 26.2's separate itemEntity/particles/weather/translucent
        // framegraph targets. Capture only actual available main depth. Never alias those old
        // categories to OIT accumulate/transmittance (those are COLOR attachments).
        FramePass pass = frameGraphBuilder.addPass("combatant_scene_depth_capture");
        combatant$dependsOn(pass, main);
        pass.disableCulling();
        pass.executes(() -> {
            RenderTarget mainFramebuffer = combatant$getFramebuffer(main);
            Minecraft client = Minecraft.getInstance();
            RenderTarget fallbackFramebuffer = client != null ? client.gameRenderer.mainRenderTarget() : null;
            RenderTarget source = mainFramebuffer != null ? mainFramebuffer : fallbackFramebuffer;
            if (source == null) {
                WorldSceneDepth.reset();
                return;
            }
            WorldSceneDepth.beginFrame(source.width, source.height);
            WorldSceneDepth.captureMain(source);
        });
    }

    @Inject(method = "addSkyPass", at = @At("HEAD"), cancellable = true)
    private void combatant$renderSky(FrameGraphBuilder frameGraphBuilder,
                                     net.minecraft.client.renderer.state.level.CameraRenderState cameraRenderState,
                                     GpuBufferSlice fog, CallbackInfo ci) {
        if (IrisRuntime.isModLoaded() || shouldSkipSky(cameraRenderState)) return;
        LevelRendererAccessor accessor = (LevelRendererAccessor) this;
        SkyRenderState sky = accessor.combatant$getWorldRenderState().skyRenderState;
        if (sky == null || sky.skybox != DimensionType.Skybox.OVERWORLD) return;
        WorldTweaks worldTweaks = Modules.get(WorldTweaks.class);
        if (worldTweaks != null) worldTweaks.applySkyOverrides(sky);
        ReimaginedVisual visual = Modules.get(ReimaginedVisual.class);
        if (visual == null || !visual.isEnabled()) return;
        Minecraft client = Minecraft.getInstance();
        net.minecraft.client.Camera camera = client != null && client.gameRenderer != null
                ? client.gameRenderer.mainCamera() : null;
        if (camera == null) return;
        FramePass pass = frameGraphBuilder.addPass("combatant_sky");
        targets.main = pass.readsAndWrites(targets.main);
        pass.executes(() -> CustomSkyboxRenderer.render(camera, fog, sky, accessor.combatant$getSkyRendering()));
        ci.cancel();
    }

    /**
     * In 26.3 the classic (non-OIT) translucent path runs inside the same open render pass as the
     * solid terrain. Depth copies and Combatant's own passes need no pass to be open, so the pass is
     * closed here (close is idempotent in RenderPearl) and a continuation pass on the same main
     * target is opened for vanilla's translucent features and terrain.
     */
    @WrapOperation(
            method = "lambda$addMainPass$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;executeClassicTransparency(Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"
            ),
            remap = false
    )
    private void combatant$captureBeforeClassicTransparency(
            LevelRenderer instance,
            net.minecraft.client.renderer.chunk.ChunkSectionsToRender sections,
            net.minecraft.client.renderer.feature.FeatureRenderDispatcher.PreparedFrame preparedFrame,
            com.mojang.renderpearl.api.commands.RenderPass pass,
            Operation<Void> original
    ) {
        RenderTarget target = combatant$mainTarget();
        if (target == null) {
            original.call(instance, sections, preparedFrame, pass);
            return;
        }
        pass.close();
        combatant$renderImportedWorldEntities();
        combatant$capturePreWaterScene();
        com.mojang.renderpearl.api.commands.RenderPass continuation = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Main pass (Combatant continuation)", target.getColorTextureView(),
                        java.util.Optional.empty(), target.getDepthTextureView(), java.util.OptionalDouble.empty());
        try {
            RenderSystem.bindDefaultUniforms(continuation);
            original.call(instance, sections, preparedFrame, continuation);
        } finally {
            continuation.close();
        }
    }

    @Inject(method = "executeOit", at = @At("HEAD"), remap = false)
    private void combatant$captureBeforeOit(CallbackInfo ci) {
        combatant$renderImportedWorldEntities();
        combatant$capturePreWaterScene();
    }

    @Unique
    private RenderTarget combatant$mainTarget() {
        RenderTarget main = combatant$getFramebuffer(targets != null ? targets.main : null);
        if (main == null) {
            Minecraft mc = Minecraft.getInstance();
            main = mc != null && mc.gameRenderer != null ? mc.gameRenderer.mainRenderTarget() : null;
        }
        return main;
    }

    @Unique
    private void combatant$capturePreWaterScene() {
        if (IrisRuntime.isShaderpackRendererActive()) {
            return;
        }
        PreTranslucentDepth.captureFrom(combatant$mainTarget());
    }

    @Unique
    private void combatant$renderImportedWorldEntities() {
        if (IrisRuntime.isShaderpackRendererActive() || !CombatantWorldMatrices.isValid()) {
            return;
        }
        RenderTarget renderTarget = combatant$mainTarget();
        org.joml.Matrix4f position = CombatantWorldMatrices.positionMatrix();
        org.joml.Matrix4f projection = CombatantWorldMatrices.renderProjectionMatrix();
        if (renderTarget == null || position == null || projection == null) {
            return;
        }

        var modelView = RenderSystem.getModelViewStack();
        boolean previousRendering3D = RenderState.rendering3D;
        modelView.pushMatrix();
        try (RenderPhaseScope ignored = CombatantRenderSystem.phase(RenderPhase.WORLD_BEFORE_TRANSLUCENT)) {
            modelView.identity();
            modelView.mul(position);
            RenderState.rendering3D = true;
            RenderState.worldProjection.set(projection);
            MeshRenderer.setProjection(projection);
            ImportedAssetCompatibilityRenderer.renderWorldEntities(renderTarget);
        } finally {
            RenderState.rendering3D = previousRendering3D;
            modelView.popMatrix();
        }
    }
}
