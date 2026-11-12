/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.hdr;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.pipeline.PostProcessRenderPipelines;
import combatant.client.render.engine.msaa.MsaaWorldTarget;
import combatant.client.render.iris.IrisRuntime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Render-thread scene scope shared by forward and deferred producers. Targets are borrowed from
 * the RHI frame pool; presentation ends the override, while submission ends the allocation lifetime.
 */
public final class HdrScene {
    private static final Map<String, BooleanSupplier> CONSUMERS = new LinkedHashMap<>();
    private static RenderTarget scene;
    private static RenderTarget output;

    private HdrScene() { }

    /** Register a render-thread predicate; replacing an owner also replaces its demand. */
    public static void registerConsumer(String owner, BooleanSupplier demand) {
        CONSUMERS.put(owner, demand);
    }

    public static boolean requested() {
        return CONSUMERS.values().stream().anyMatch(BooleanSupplier::getAsBoolean);
    }

    public static void begin(RenderTarget destination) {
        reset();
        // Iris owns its framebuffers and shaderpack output transform, even during pack reloads.
        if (IrisRuntime.isModLoaded()) return;
        scene = CombatantRenderSystem.resources().frameTransientHdr(
                "world.scene_color", destination.width, destination.height, true, "render-engine");
        output = destination;
    }

    public static boolean active() { return scene != null; }
    public static RenderTarget target() { return scene; }

    public static RenderTarget resolveTarget(RenderTarget fallback) {
        return scene != null ? scene : fallback;
    }

    /** Restricts HDR pipeline replacement to this frame's Combatant-owned world color attachment. */
    public static boolean ownsColorAttachment(GpuTextureView attachment) {
        if (scene == null || attachment == null) return false;
        GpuTextureView sceneColor = scene.getColorTextureView();
        if (sceneColor != null && sceneColor.texture() == attachment.texture()) return true;
        if (!MsaaWorldTarget.isActive()) return false;
        RenderTarget msaa = MsaaWorldTarget.getMsaaFramebuffer();
        GpuTextureView msaaColor = msaa != null ? msaa.getColorTextureView() : null;
        return msaaColor != null && msaaColor.texture() == attachment.texture();
    }

    public static void present() {
        if (scene == null) return;
        RenderTarget source = scene;
        RenderTarget destination = output;
        reset();
        FullScreenRenderer.draw(destination, PostProcessRenderPipelines.SCENE_PRESENT)
                .sampler("u_Texture", source.getColorTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))
                .end();
        // Hand depth belongs to the vanilla SDR target. render3dHud clears it before PRE_HAND,
        // so copying HDR world depth here would overwrite that clear and occlude the hand.
    }

    public static void reset() {
        scene = null;
        output = null;
    }

}
