/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.postprocess.graph;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.ReimaginedVisual;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.depth.PreTranslucentDepth;
import combatant.client.render.engine.depth.WorldSceneDepth;
import combatant.client.render.engine.postprocess.PostProcessContext;
import combatant.client.render.engine.postprocess.PostProcessPass;
import combatant.client.render.iris.IrisSceneDepth;

import java.util.EnumMap;

public final class PostProcessGraphResources implements AutoCloseable {
    private final EnumMap<PostProcessResource, GpuTextureView> views = new EnumMap<>(PostProcessResource.class);

    private @Nullable RenderTarget mainFramebuffer;
    private @Nullable TextureTarget ping;
    private @Nullable TextureTarget pong;
    private @Nullable GpuTextureView pingView;
    private @Nullable GpuTextureView pongView;
    private @Nullable PostProcessContext legacyContext;

    private boolean usePingAsSource = true;
    private boolean prepared;

    public boolean prepare(PostProcessPass.Phase phase, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        if (main == null) return false;

        int w = Math.max(1, mc.getWindow().getWidth());
        int h = Math.max(1, mc.getWindow().getHeight());
        ensurePingPong(w, h);
        if (ping == null || pong == null) return false;

        pingView = ping.getColorTextureView();
        pongView = pong.getColorTextureView();
        if (main.getColorTextureView() == null || pingView == null || pongView == null) return false;

        clear();
        this.mainFramebuffer = main;
        GpuTextureView mainDepth = IrisSceneDepth.isValid()
                ? IrisSceneDepth.mainDepthView()
                : WorldSceneDepth.hasMain() ? WorldSceneDepth.mainDepthView() : main.getDepthTextureView();
        GpuTextureView preTranslucentDepth = IrisSceneDepth.isValid()
                ? IrisSceneDepth.preTranslucentDepthView()
                : needsPreTranslucentDepth() ? PreTranslucentDepth.getDepthView() : null;
        GpuTextureView staticWorldDepth = IrisSceneDepth.isValid()
                ? null
                : WorldSceneDepth.hasItemEntity() ? WorldSceneDepth.itemEntityDepthView() : null;
        put(PostProcessResource.MAIN_COLOR, main.getColorTextureView());
        put(PostProcessResource.MAIN_DEPTH, mainDepth);
        put(PostProcessResource.PRE_TRANSLUCENT_DEPTH, preTranslucentDepth);
        put(PostProcessResource.GRAPH_SOURCE_COLOR, pingView);
        put(PostProcessResource.GRAPH_DEST_COLOR, pongView);
        this.legacyContext = new PostProcessContext(
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
        this.usePingAsSource = true;
        this.prepared = true;
        return true;
    }

    public void resetPingPong() {
        usePingAsSource = true;
        put(PostProcessResource.GRAPH_SOURCE_COLOR, pingView);
        put(PostProcessResource.GRAPH_DEST_COLOR, pongView);
    }

    public void advancePingPong() {
        usePingAsSource = !usePingAsSource;
        put(PostProcessResource.GRAPH_SOURCE_COLOR, currentSource());
        put(PostProcessResource.GRAPH_DEST_COLOR, currentDestination());
    }

    public @Nullable GpuTextureView currentSource() {
        return usePingAsSource ? pingView : pongView;
    }

    public @Nullable GpuTextureView currentDestination() {
        return usePingAsSource ? pongView : pingView;
    }

    public @Nullable GpuTextureView finalColor() {
        return currentSource();
    }

    public boolean isPrepared() {
        return prepared;
    }

    public @Nullable RenderTarget mainFramebuffer() {
        return mainFramebuffer;
    }

    public @Nullable GpuTextureView mainColor() {
        return get(PostProcessResource.MAIN_COLOR);
    }

    public @Nullable PostProcessContext legacyContext() {
        return legacyContext;
    }

    public void put(PostProcessResource resource, @Nullable GpuTextureView view) {
        if (view != null) views.put(resource, view);
    }

    public @Nullable GpuTextureView get(PostProcessResource resource) {
        return views.get(resource);
    }

    public void clear() {
        views.clear();
        mainFramebuffer = null;
        legacyContext = null;
        prepared = false;
    }

    private void ensurePingPong(int w, int h) {
        ping = CombatantRenderSystem.resources().persistentFramebuffer(
                "combatant-postprocess-graph-ping", w, h, false, "PostProcessGraph");
        pong = CombatantRenderSystem.resources().persistentFramebuffer(
                "combatant-postprocess-graph-pong", w, h, false, "PostProcessGraph");
    }

    private static boolean needsPreTranslucentDepth() {
        ReimaginedVisual module = Modules.get(ReimaginedVisual.class);
        return module != null && module.needsPreTranslucentDepthCapture();
    }

    @Override
    public void close() {
        // Framebuffers are owned by RenderResourceManager. This object only drops references.
        ping = null;
        pong = null;
        pingView = null;
        pongView = null;
        clear();
    }
}
