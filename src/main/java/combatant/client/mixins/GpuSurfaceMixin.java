/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.systems.GpuSurfaceBackend;
import com.mojang.blaze3d.systems.SurfaceException;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.profiler.ProfilerPhase;
import combatant.client.render.engine.profiler.TracyGpuProfiler;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.util.FastFps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GpuSurface.class)
public abstract class GpuSurfaceMixin {
    /**
     * Last safe point before Minecraft copies the main render target into the
     * window surface. Combatant submission must be closed before the blit.
     */
    @Inject(method = "blitFromTexture", at = @At("HEAD"))
    private void combatant$beforeSurfaceBlit(CommandEncoder encoder, GpuTextureView textureView, CallbackInfo info) {
        CombatantRenderSystem.endRenderSubmission();
    }

    @Inject(method = "present", at = @At("HEAD"))
    private void combatant$onPresentHead(CallbackInfo info) {
        FastFps.onFrame();
        AnimationUtility.onFrame();
    }

    @Inject(method = "present", at = @At("TAIL"))
    private void combatant$onPresentTail(CallbackInfo info) {
        if (TracyGpuProfiler.isEnabled()) {
            TracyGpuProfiler.onFrameEnd();
        }
        CombatantRenderSystem.onFramePresented();
        Renderer2D.getBatchStats().onFrameStart();
        Renderer2D.invalidateWorldGlassSource();
    }

    @Redirect(
            method = "configure",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuSurfaceBackend;configure(Lcom/mojang/blaze3d/systems/GpuSurface$Configuration;)V")
    )
    private void combatant$profileSurfaceConfigure(GpuSurfaceBackend backend, GpuSurface.Configuration configuration) throws SurfaceException {
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("surface:configure")) {
            backend.configure(configuration);
        }
    }

    @Redirect(
            method = "acquireNextTexture",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuSurfaceBackend;acquireNextTexture()V")
    )
    private void combatant$profileSurfaceAcquire(GpuSurfaceBackend backend) throws SurfaceException {
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("surface:acquire")) {
            backend.acquireNextTexture();
        }
    }

    @Redirect(
            method = "blitFromTexture",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuSurfaceBackend;blitFromTexture(Lcom/mojang/blaze3d/systems/CommandEncoderBackend;Lcom/mojang/blaze3d/textures/GpuTextureView;)V")
    )
    private void combatant$profileSurfaceBlit(GpuSurfaceBackend backend, CommandEncoderBackend encoder, GpuTextureView textureView) {
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("surface:blit")) {
            backend.blitFromTexture(encoder, textureView);
        }
    }

    @Redirect(
            method = "present",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuSurfaceBackend;present()V")
    )
    private void combatant$profileSurfacePresent(GpuSurfaceBackend backend) {
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("surface:present")) {
            backend.present();
        }
    }
}
