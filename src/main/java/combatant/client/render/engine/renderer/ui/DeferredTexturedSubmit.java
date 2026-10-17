/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.Renderer2D.Deferred2DLayer;
import combatant.client.render.engine.renderer.ui.clip.UiClipSnapshot;
import combatant.client.render.engine.renderer.ui.clip.UiScissorSnapshot;
import combatant.client.render.engine.uniform.MeshBuilder;

public record DeferredTexturedSubmit(Deferred2DLayer layer, ViewportContext viewport,
                                     UiScissorSnapshot scissorSnapshot, UiClipSnapshot clipSnapshot, String samplerName,
                                     GpuTextureView samplerView, GpuSampler sampler, RenderPipeline pipeline,
                                     MeshBuilder mesh) implements Deferred2DSubmit {
    @Override
    public void submit() {
        if (mesh == null || samplerView == null || sampler == null) return;
        UiClipSnapshot clip = clipSnapshot != null ? clipSnapshot : UiClipSnapshot.NONE;
        String resolvedSamplerName = samplerName != null ? samplerName : "u_Texture";
        UiRenderDispatcher.submitImmediate(
                "Renderer2D.TEXTURE.deferred",
                1,
                (context, rhi) -> UiDirectTexturedRenderer.draw(
                        mesh, pipeline, resolvedSamplerName, samplerView, sampler, clip, layer, rhi)
        );
        Renderer2D.flushUiLayer();
    }

    @Override
    public void release() {
        if (mesh != null) {
            mesh.close();
        }
    }
}
