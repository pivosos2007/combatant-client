/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.deferred.DevDeferredRuntime;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

@Mixin(LevelRenderer.class)
public abstract class DevDeferredLevelRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void combatant$devDeferredPrimaryView(
            GraphicsResourceAllocator allocator, boolean renderBlockOutline,
            net.minecraft.client.renderer.state.level.CameraRenderState cameraRenderState,
            GpuBufferSlice fogBuffer, org.joml.Vector4f fogColor,
            boolean renderSky, boolean consistentDepthRequired, CallbackInfo ci) {
        Matrix4fc positionMatrix = cameraRenderState != null ? cameraRenderState.viewRotationMatrix : null;
        if (cameraRenderState == null || cameraRenderState.pos == null || positionMatrix == null) return;
        var frame = CombatantRenderSystem.ensureFrameContext();
        var projection = CombatantWorldMatrices.renderProjectionMatrix();
        var unjittered = CombatantWorldMatrices.unjitteredRenderProjectionMatrix();
        if (projection == null) projection = new org.joml.Matrix4f(cameraRenderState.projectionMatrix);
        if (unjittered == null) unjittered = new org.joml.Matrix4f(projection);
        DevDeferredRuntime.world().capturePrimaryView(
                frame.frameId(), positionMatrix, projection, unjittered,
                CombatantWorldMatrices.jitterPixels(), cameraRenderState.pos, cameraRenderState.depthFar);
    }

    @Shadow @Final private LevelTargetBundle targets;

    @WrapOperation(method = "lambda$addMainPass$0", remap = false,
            at = @At(value = "INVOKE", target =
                    "Lcom/mojang/renderpearl/api/commands/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/renderpearl/api/commands/RenderPass;"))
    private RenderPass combatant$beginOpaqueMrt(CommandEncoder encoder, Supplier<String> label,
             GpuTextureView color, Optional<Vector4fc> clearColor, GpuTextureView depth,
             OptionalDouble clearDepth, Operation<RenderPass> original) {
        if (!DevDeferredRuntime.world().geometryCaptureEnabled()) {
            return original.call(encoder, label, color, clearColor, depth, clearDepth);
        }
        return DevDeferredRuntime.world().openGeometryPass(encoder, label, color, clearColor, depth, clearDepth);
    }

    @WrapOperation(method = "executeSolid", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeSolid(Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void combatant$finishTerrainBeforeFeatures(FeatureRenderDispatcher.PreparedFrame frame,
            RenderPass terrainPass, Operation<Void> original) {
        if (DevDeferredRuntime.world().gbufferSurface() == null) {
            original.call(frame, terrainPass);
            return;
        }
        // The caller's try-with-resources may close terrainPass again: same idempotent pattern
        // already used by the main Combatant LevelRendererMixin for pre-translucent snapshots.
        terrainPass.close();
        ResourceHandle<RenderTarget> main = targets.main;
        RenderTarget target = main == null ? null : main.get();
        if (target == null) throw new IllegalStateException("No main target for deferred terrain resolve");
        DevDeferredRuntime.world().finishOpaqueGeometry(target.getColorTextureView());
        try (RenderPass featuresPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Minecraft solid features after Combatant opaque MRT",
                target.getColorTextureView(), Optional.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(featuresPass);
            original.call(frame, featuresPass);
        }
        DevDeferredRuntime.world().finishForwardOpaque(target.getColorTextureView(), target.getDepthTextureView());
    }
}
