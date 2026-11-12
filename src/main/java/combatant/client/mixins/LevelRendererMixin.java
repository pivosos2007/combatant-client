/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import combatant.client.config.MainConfig;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
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
import combatant.client.render.engine.depth.EntityDepthLayer;
import combatant.client.render.engine.depth.SeeInvisiblesVisualDepth;
import combatant.client.render.engine.depth.PreTranslucentDepth;
import combatant.client.render.engine.depth.WorldSceneDepth;
import combatant.client.render.engine.msaa.MsaaWorldTarget;
import combatant.client.render.engine.world.VanillaBeforeTranslucentPass;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.render.sky.CustomSkyboxRenderer;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.renderer.oit.OitRenderPassProvider;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.OptionalDouble;

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
    private void combatant$beginPlayerGpuFrame(GraphicsResourceAllocator allocator,
            boolean renderBlockOutline,
            net.minecraft.client.renderer.state.level.CameraRenderState cameraRenderState,
            GpuBufferSlice fogBuffer, org.joml.Vector4f fogColor,
            boolean renderSky, boolean renderTerrain, CallbackInfo ci) {
        combatant.client.features.playermodel.PlayerModelGpuRenderer.beginWorldFrame();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void combatant$endPlayerGpuFrame(GraphicsResourceAllocator allocator,
            boolean renderBlockOutline,
            net.minecraft.client.renderer.state.level.CameraRenderState cameraRenderState,
            GpuBufferSlice fogBuffer, org.joml.Vector4f fogColor,
            boolean renderSky, boolean renderTerrain, CallbackInfo ci) {
        combatant.client.features.playermodel.PlayerModelGpuRenderer.endWorldFrame();
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
        SeeInvisiblesVisualDepth.snapshot(state.entityRenderStates);
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

    /**
     * In Minecraft 26.3 solid and classic translucent geometry share the SAME frame-graph
     * pass and the SAME RenderPass. A separate frame-graph pass after addMainPass() is
     * already too late. Close the solid pass, snapshot its depth, then resume translucent
     * drawing in a new pass that LOADS the existing color/depth attachments.
     *
     * FrontendRenderPass.close() is idempotent; the vanilla try-with-resources closes
     * the original pass again on exit.
     */
    @WrapOperation(
            method = "lambda$addMainPass$0",
            // Only the synthetic lambda name stays unmapped. The invoked Minecraft method
            // still needs to be remapped in production Fabric builds.
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/client/renderer/LevelRenderer;executeClassicTransparency(Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/commands/RenderPass;)V",
                    remap = true),
            remap = false
    )
    private void combatant$snapshotBeforeClassicTransparency(
            LevelRenderer renderer,
            ChunkSectionsToRender sections,
            FeatureRenderDispatcher.PreparedFrame prepared,
            RenderPass solidPass,
            Operation<Void> original
    ) {
        if (IrisRuntime.isShaderpackRendererActive()) {
            original.call(renderer, sections, prepared, solidPass);
            return;
        }

        RenderTarget framebuffer = combatant$getFramebuffer(targets.main);
        if (framebuffer == null || framebuffer.getColorTextureView() == null
                || framebuffer.getDepthTextureView() == null) {
            original.call(renderer, sections, prepared, solidPass);
            return;
        }

        solidPass.close();
        // Must draw on the SAME opaque color/depth attachments before they are copied
        // for water and post-processing. GameRenderer's world hook is too late.
        combatant.client.features.playermodel.PlayerModelGpuRenderer.renderBeforeTranslucency(framebuffer);
        PreTranslucentDepth.captureFrom(framebuffer);
        combatant$renderDreamClouds(framebuffer);
        VanillaBeforeTranslucentPass.render(framebuffer);
        try (RenderPass translucentPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Minecraft translucent (pre-translucent depth snapshot)",
                framebuffer.getColorTextureView(), Optional.empty(),
                framebuffer.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(translucentPass);
            original.call(renderer, sections, prepared, translucentPass);
        }
        combatant.client.features.playermodel.PlayerModelGpuRenderer.renderAfterTranslucency(framebuffer);
    }

    /** In the OIT path executeOit starts AFTER the opaque RenderPass has closed. */
    @Inject(method = "executeOit", at = @At("HEAD"))
    private void combatant$snapshotBeforeOit(ChunkSectionsToRender sections,
                                             FeatureRenderDispatcher.PreparedFrame prepared,
                                             CallbackInfo ci) {
        if (!IrisRuntime.isShaderpackRendererActive()) {
            RenderTarget mainFramebuffer = combatant$getFramebuffer(targets.main);
            combatant.client.features.playermodel.PlayerModelGpuRenderer.renderBeforeTranslucency(mainFramebuffer);
            PreTranslucentDepth.captureFrom(mainFramebuffer);
            combatant$renderDreamClouds(mainFramebuffer);
            VanillaBeforeTranslucentPass.render(mainFramebuffer);
        }
    }

    /**
     * Minecraft 26.3's OIT targets are single-sampled, but executeOit and its
     * depth-bounds cull take mainRenderTarget().getDepthTextureView() directly.
     * When main is the MSAA world target that view is multisampled: binding it
     * alongside an OIT color attachment creates a mixed-sample render pass and
     * binding it as the OIT depth sampler is invalid as well.
     *
     * The opaque pass has already finished when executeOit starts, and our
     * pre-translucent snapshot therefore contains exactly the depth OIT needs.
     * Leave the actual multisampled main attachment untouched for later draws.
     */
    @WrapOperation(
            method = {"executeOit", "executeDepthBoundsCull"},
            at = @At(value = "INVOKE", target =
                    "Lcom/mojang/blaze3d/pipeline/RenderTarget;getDepthTextureView()Lcom/mojang/renderpearl/api/textures/GpuTextureView;")
    )
    private GpuTextureView combatant$useResolvedOitDepth(RenderTarget framebuffer,
                                                          Operation<GpuTextureView> original) {
        if (MsaaWorldTarget.isActive() && framebuffer == MsaaWorldTarget.getMsaaFramebuffer()) {
            GpuTextureView resolved = PreTranslucentDepth.getCapturedDepthView();
            if (resolved != null) {
                return resolved;
            }
        }
        return original.call(framebuffer);
    }

    @Inject(method = "executeOit", at = @At("RETURN"))
    private void combatant$finishPlayerGpuOit(ChunkSectionsToRender sections,
                                              FeatureRenderDispatcher.PreparedFrame prepared,
                                              CallbackInfo ci) {
        if (!IrisRuntime.isShaderpackRendererActive()) {
            combatant.client.features.playermodel.PlayerModelGpuRenderer.renderAfterTranslucency(
                    combatant$getFramebuffer(targets.main));
        }
    }


    @Unique
    private static boolean combatant$replaceVanillaClouds() {
        if (IrisRuntime.isModLoaded()) return false;
        ReimaginedVisual visual = Modules.get(ReimaginedVisual.class);
        if (visual == null || !visual.isShaderSkyboxEnabled()) return false;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return false;
        LevelRenderer renderer = client.levelRenderer;
        if (!(renderer instanceof LevelRendererAccessor accessor)) return false;
        SkyRenderState sky = accessor.combatant$getWorldRenderState().skyRenderState;
        return sky != null && sky.skybox == DimensionType.Skybox.OVERWORLD;
    }

    /**
     * Override only this renderer's local cloud choice. Besides skipping draw calls, this
     * avoids allocating/declaring the otherwise unused OIT cloud depth target in addMainPass.
     * Vanilla's actual Options and CloudStatus remain unchanged.
     */
    @ModifyExpressionValue(method = {"addMainPass", "prepareTranslucents", "executeOit",
            "executeClassicTransparency"}, at = @At(value = "FIELD", target =
            "Lnet/minecraft/client/renderer/state/OptionsRenderState;cloudStatus:Lnet/minecraft/client/CloudStatus;"))
    private CloudStatus combatant$useLocalCloudStatus(CloudStatus original) {
        return combatant$replaceVanillaClouds() ? CloudStatus.OFF : original;
    }

    // Do not change the global CloudStatus option; skip only vanilla cloud submission.
    @WrapOperation(method = "prepareTranslucents", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/CloudRenderer;prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V"))
    private void combatant$skipVanillaCloudPreparation(CloudRenderer renderer, int color,
            CloudStatus status, float height, int range, Vec3 position, long gameTime,
            float partialTicks, Operation<Void> original) {
        if (!combatant$replaceVanillaClouds()) {
            original.call(renderer, color, status, height, range, position, gameTime, partialTicks);
        }
    }

    @WrapOperation(method = "executeClassicTransparency", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/CloudRenderer;render(Lnet/minecraft/client/CloudStatus;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void combatant$skipVanillaClassicClouds(CloudRenderer renderer, CloudStatus status,
            RenderPass renderPass, Operation<Void> original) {
        if (!combatant$replaceVanillaClouds()) original.call(renderer, status, renderPass);
    }

    @WrapOperation(method = "executeOit", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/CloudRenderer;renderOit(Lnet/minecraft/client/CloudStatus;Lnet/minecraft/client/renderer/oit/OitStage;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lnet/minecraft/client/renderer/oit/OitRenderPassProvider$Parameters;)V"))
    private void combatant$skipVanillaOitClouds(CloudRenderer renderer, CloudStatus status,
            OitStage stage, GpuTextureView texture, OitRenderPassProvider.Parameters parameters,
            Operation<Void> original) {
        if (!combatant$replaceVanillaClouds()) original.call(renderer, status, stage, texture, parameters);
    }

    @Unique
    private void combatant$renderDreamClouds(RenderTarget framebuffer) {
        if (!combatant$replaceVanillaClouds()) return;
        LevelRendererAccessor accessor = (LevelRendererAccessor) this;
        var cameraState = accessor.combatant$getWorldRenderState().cameraRenderState;
        if (cameraState == null || shouldSkipSky(cameraState)) return;
        ReimaginedVisual visual = Modules.get(ReimaginedVisual.class);
        if (visual != null && visual.areDreamCloudsEnabled()) {
            visual.dreamCloudRenderer().render(framebuffer, visual,
                    accessor.combatant$getWorldRenderState().skyRenderState);
        }
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
        if (visual == null || !visual.isShaderSkyboxEnabled()) return;
        FramePass pass = frameGraphBuilder.addPass("combatant_sky");
        targets.main = pass.readsAndWrites(targets.main);
        ResourceHandle<RenderTarget> skyFramebuffer = targets.main;
        pass.executes(() -> CustomSkyboxRenderer.render(cameraRenderState, fog, sky,
                combatant$getFramebuffer(skyFramebuffer)));
        ci.cancel();
    }

}
