/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.postprocess.graph;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.features.module.Modules;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.depth.PreTranslucentDepth;
import combatant.client.render.engine.depth.WorldSceneDepth;
import combatant.client.render.engine.postprocess.PostProcessContext;
import combatant.client.render.engine.postprocess.PostProcessExecutionPolicy;
import combatant.client.render.engine.postprocess.PostProcessPass;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageImageDescriptor;
import combatant.client.render.iris.IrisSceneDepth;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/** Owns the post-process ping-pong targets and frame-local scene context. */
public final class PostProcessGraphResources implements AutoCloseable {
    private static final long STORAGE_IDLE_RELEASE_NANOS = 10_000_000_000L;
    private @Nullable RenderTarget mainFramebuffer;
    private @Nullable TextureTarget ping;
    private @Nullable TextureTarget pong;
    private @Nullable RhiStorageImage pingStorage;
    private @Nullable RhiStorageImage pongStorage;
    private @Nullable CombatantRhi storageOwner;
    private boolean storageTargetsFailed;
    /** True when the current graph run ping-pongs through the storage images instead of framebuffers. */
    private boolean storageMode;
    private long storageLastUsedNanos;
    private @Nullable GpuTextureView pingView;
    private @Nullable GpuTextureView pongView;
    private @Nullable PostProcessContext context;
    private boolean usePingAsSource = true;

    public boolean prepare(PostProcessPass.Phase phase, float tickDelta, CombatantRhi rhi, boolean preferStorageTargets) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        if (main == null || main.getColorTextureView() == null) return false;

        int w = Math.max(1, mc.getWindow().getWidth());
        int h = Math.max(1, mc.getWindow().getHeight());
        ensurePingPong(w, h, rhi, preferStorageTargets);
        if (pingView == null || pongView == null) return false;

        clearFrameState();
        mainFramebuffer = main;
        GpuTextureView mainDepth = IrisSceneDepth.isValid()
                ? IrisSceneDepth.mainDepthView()
                : WorldSceneDepth.hasMain() ? WorldSceneDepth.mainDepthView() : main.getDepthTextureView();
        GpuTextureView preTranslucentDepth = IrisSceneDepth.isValid()
                ? IrisSceneDepth.preTranslucentDepthView()
                : PreTranslucentDepth.getCapturedDepthView();
        GpuTextureView staticWorldDepth = IrisSceneDepth.isValid()
                ? null
                : WorldSceneDepth.hasItemEntity() ? WorldSceneDepth.itemEntityDepthView() : null;
        context = new PostProcessContext(
                phase,
                tickDelta,
                main,
                main.getColorTextureView(),
                mainDepth,
                preTranslucentDepth,
                staticWorldDepth,
                main.width,
                main.height
        );
        usePingAsSource = true;
        return true;
    }

    public void resetPingPong() {
        usePingAsSource = true;
    }

    public void advancePingPong() {
        usePingAsSource = !usePingAsSource;
    }

    public @Nullable GpuTextureView currentSource() {
        return usePingAsSource ? pingView : pongView;
    }

    public @Nullable GpuTextureView currentDestination() {
        return usePingAsSource ? pongView : pingView;
    }

    public @Nullable RhiStorageImage currentSourceStorage() {
        if (!storageMode) return null;
        return usePingAsSource ? pingStorage : pongStorage;
    }

    public @Nullable RhiStorageImage currentDestinationStorage() {
        if (!storageMode) return null;
        return usePingAsSource ? pongStorage : pingStorage;
    }

    public @Nullable GpuTextureView finalColor() {
        return currentSource();
    }

    public @Nullable GpuTextureView mainColor() {
        return mainFramebuffer != null ? mainFramebuffer.getColorTextureView() : null;
    }

    public @Nullable PostProcessContext context() {
        return context;
    }

    private void clearFrameState() {
        mainFramebuffer = null;
        context = null;
    }

    private void ensurePingPong(int w, int h, CombatantRhi rhi, boolean preferStorageTargets) {
        if (storageOwner != null && storageOwner != rhi) {
            closeStoragePingPong();
            storageTargetsFailed = false;
        }
        long now = System.nanoTime();
        if (preferStorageTargets && PostProcessExecutionPolicy.useCompute(rhi) && !storageTargetsFailed) {
            try {
                ensureStoragePingPong(w, h, rhi);
                pingView = pingStorage != null ? pingStorage.view() : null;
                pongView = pongStorage != null ? pongStorage.view() : null;
                if (pingView != null && pongView != null) {
                    storageMode = true;
                    storageLastUsedNanos = now;
                    return;
                }
            } catch (Throwable t) {
                PostProcessExecutionPolicy.warnRuntimeFallback(
                        "postprocess-storage-targets",
                        "Post-process storage targets",
                        t);
                closeStoragePingPong();
                storageTargetsFailed = true;
            }
        }

        // The graph runs once per phase and consecutive runs can disagree on storage preference (a compute
        // pass is active in one phase only). Destroying and re-creating two full-resolution textures on
        // every disagreement showed up as glDeleteTextures stalls every frame, so the storage pair is kept
        // alive while this run uses framebuffers and only released after it has gone unused for a while.
        storageMode = false;
        if ((pingStorage != null || pongStorage != null) && now - storageLastUsedNanos > STORAGE_IDLE_RELEASE_NANOS) {
            closeStoragePingPong();
        }
        ping = CombatantRenderSystem.resources().persistentFramebuffer(
                "combatant-postprocess-graph-ping", w, h, false, "PostProcessGraph");
        pong = CombatantRenderSystem.resources().persistentFramebuffer(
                "combatant-postprocess-graph-pong", w, h, false, "PostProcessGraph");
        pingView = ping != null ? ping.getColorTextureView() : null;
        pongView = pong != null ? pong.getColorTextureView() : null;
    }

    private void ensureStoragePingPong(int w, int h, CombatantRhi rhi) {
        boolean ownerChanged = storageOwner != rhi;
        boolean sizeChanged = pingStorage == null || pongStorage == null
                || pingStorage.descriptor().width() != w || pingStorage.descriptor().height() != h
                || pongStorage.descriptor().width() != w || pongStorage.descriptor().height() != h;
        if (!ownerChanged && !sizeChanged) return;

        closeStoragePingPong();
        storageOwner = rhi;
        pingStorage = rhi.advancedShaders().createStorageImage(new StorageImageDescriptor(
                "combatant-postprocess-graph-ping-storage", w, h, GpuFormat.RGBA8_UNORM,
                StorageAccess.READ_WRITE, true, true));
        try {
            pongStorage = rhi.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "combatant-postprocess-graph-pong-storage", w, h, GpuFormat.RGBA8_UNORM,
                    StorageAccess.READ_WRITE, true, true));
        } catch (Throwable t) {
            closeStoragePingPong();
            throw t;
        }
    }

    private void closeStoragePingPong() {
        if (pingStorage != null) {
            try { pingStorage.close(); } catch (Throwable ignored) { }
            pingStorage = null;
        }
        if (pongStorage != null) {
            try { pongStorage.close(); } catch (Throwable ignored) { }
            pongStorage = null;
        }
        storageOwner = null;
        storageMode = false;
    }


    public void releaseBackendResources(CombatantRhi owner) {
        if (storageOwner == null || owner == null || storageOwner == owner) {
            closeStoragePingPong();
            storageTargetsFailed = false;
            storageMode = false;
            pingView = ping != null ? ping.getColorTextureView() : null;
            pongView = pong != null ? pong.getColorTextureView() : null;
        }
    }

    @Override
    public void close() {
        closeStoragePingPong();
        ping = null;
        pong = null;
        pingView = null;
        pongView = null;
        clearFrameState();
    }
}
