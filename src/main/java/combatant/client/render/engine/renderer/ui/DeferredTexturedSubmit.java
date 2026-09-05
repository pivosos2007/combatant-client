/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.Renderer2D.Deferred2DLayer;
import combatant.client.render.engine.renderer.ui.clip.UiClipSnapshot;
import combatant.client.render.engine.renderer.ui.clip.UiScissorSnapshot;
import combatant.client.render.engine.renderer.ui.clip.UiMsaaClipLayer;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.uniform.impl.UiClipUniforms;

public record DeferredTexturedSubmit(Deferred2DLayer layer, ViewportContext viewport,
                                    UiScissorSnapshot scissorSnapshot, UiClipSnapshot clipSnapshot, String samplerName,
                                    GpuTextureView samplerView, GpuSampler sampler, MeshBuilder mesh) implements Deferred2DSubmit {
    @Override
    public void submit() {
        if (mesh == null || samplerView == null || sampler == null) return;
        UiClipSnapshot clip = clipSnapshot != null ? clipSnapshot : UiClipSnapshot.NONE;
        String resolvedSamplerName = samplerName != null ? samplerName : "u_Texture";
        UiRenderDispatcher.submitImmediate(
                "Renderer2D.TEXTURE.deferred",
                1,
                (context, rhi) -> draw(mesh, resolvedSamplerName, samplerView, sampler, clip)
        );
        Renderer2D.flushUiLayer();
    }

    private static void draw(MeshBuilder mesh,
                             String samplerName,
                             GpuTextureView samplerView,
                             GpuSampler sampler,
                             UiClipSnapshot clip) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null) return;
        RenderTarget fb = mc.gameRenderer.mainRenderTarget();
        if (fb == null) return;

        com.mojang.blaze3d.pipeline.RenderPipeline pipeline = CombatantRenderPipelines.UI_TEXTURED;
        if (clip.usesAnalyticPipeline()) {
            pipeline = CombatantRenderPipelines.analyticClipTexturedPipeline(pipeline);
        }
        MeshRenderer draw = MeshRenderer.begin()
                .attachments(UiMsaaClipLayer.currentColorAttachment(fb.getColorTextureView()), null)
                .pipeline(pipeline)
                .mesh(mesh)
                .sampler(samplerName, samplerView, sampler);
        if (clip.usesAnalyticPipeline()) {
            draw.uniform("UIClip", UiClipUniforms.write(clip));
        }
        draw.end();
    }

    @Override
    public void release() {
        if (mesh != null) {
            mesh.close();
        }
    }
}
