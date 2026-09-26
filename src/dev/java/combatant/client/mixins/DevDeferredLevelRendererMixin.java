/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.deferred.DevDeferredRuntime;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Primary-view and cloud ownership hooks for the dev deferred renderer. */
@Mixin(LevelRenderer.class)
public abstract class DevDeferredLevelRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void combatant$devDeferredPrimaryView(
            GraphicsResourceAllocator allocator, DeltaTracker tickCounter, boolean renderBlockOutline,
            net.minecraft.client.renderer.state.level.CameraRenderState cameraRenderState,
            Matrix4fc positionMatrix, GpuBufferSlice fogBuffer, org.joml.Vector4f fogColor,
            boolean renderSky, CallbackInfo ci) {
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
}
