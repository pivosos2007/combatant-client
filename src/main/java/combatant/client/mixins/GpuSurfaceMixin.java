/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.backend.api.CommandEncoderBackend;
import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.renderpearl.frontend.FrontendGpuSurface;
import com.mojang.renderpearl.backend.api.GpuSurfaceBackend;
import com.mojang.renderpearl.api.device.SurfaceException;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.profiler.ProfilerPhase;
import combatant.client.render.engine.profiler.TracyGpuProfiler;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.UiBlurResources;
import combatant.client.util.FastFps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FrontendGpuSurface.class)
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

    @Redirect(
            method = "configure",
            at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/backend/api/GpuSurfaceBackend;configure(Lcom/mojang/renderpearl/api/device/GpuSurface$Configuration;)V")
    )
    private void combatant$profileSurfaceConfigure(GpuSurfaceBackend backend, GpuSurface.Configuration configuration) throws SurfaceException {
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("surface:configure")) {
            backend.configure(configuration);
        }
    }

    @Redirect(
            method = "acquireNextTexture",
            at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/backend/api/GpuSurfaceBackend;acquireNextTexture()V")
    )
    private void combatant$profileSurfaceAcquire(GpuSurfaceBackend backend) throws SurfaceException {
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("surface:acquire")) {
            backend.acquireNextTexture();
        }
    }

    @Redirect(
            method = "blitFromTexture",
            at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/backend/api/GpuSurfaceBackend;blitFromTexture(Lcom/mojang/renderpearl/backend/api/CommandEncoderBackend;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V")
    )
    private void combatant$profileSurfaceBlit(GpuSurfaceBackend backend, CommandEncoderBackend encoder, GpuTextureView textureView) {
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("surface:blit")) {
            backend.blitFromTexture(encoder, textureView);
        } catch (RuntimeException | Error failure) {
            // A lost/minimized surface can fail before present() is reached. Retire Combatant's
            // frame here as well so transient resources and logical frame identity cannot leak.
            combatant$finishFrameBoundary();
            throw failure;
        }
    }

    @Redirect(
            method = "present",
            at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/backend/api/GpuSurfaceBackend;present()V")
    )
    private void combatant$profileSurfacePresent(GpuSurfaceBackend backend) {
        try {
            try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("surface:present")) {
                backend.present();
            }
        } finally {
            // Surface loss/minimize may throw out of backend.present(). Combatant must still retire
            // the logical frame or the next world render reuses stale frame id/uniform/transient state.
            combatant$finishFrameBoundary();
        }
    }

    private void combatant$finishFrameBoundary() {
        if (TracyGpuProfiler.isEnabled()) {
            TracyGpuProfiler.onFrameEnd();
        }
        CombatantRenderSystem.onFramePresented();
        Renderer2D.getBatchStats().onFrameStart();
        UiBlurResources.invalidateWorldSource();
    }
}
