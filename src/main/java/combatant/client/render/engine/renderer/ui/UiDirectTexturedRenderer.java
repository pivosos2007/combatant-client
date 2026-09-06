/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.uniform.impl.UiClipUniforms;
import combatant.client.render.engine.renderer.ui.clip.UiClipSnapshot;
import combatant.client.render.engine.renderer.ui.clip.UiMsaaClipLayer;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.ClipFunction;
import net.minecraft.client.Minecraft;

/** Direct textured lowering routed through the production UiPassExecutor. */
public final class UiDirectTexturedRenderer {
    private UiDirectTexturedRenderer() {
    }

    public static void submit(
            MeshBuilder mesh,
            String samplerName,
            GpuTextureView samplerView,
            GpuSampler sampler) {
        submit(mesh, CombatantRenderPipelines.UI_TEXTURED, samplerName, samplerView, sampler);
    }

    public static void submit(
            MeshBuilder mesh,
            RenderPipeline pipeline,
            String samplerName,
            GpuTextureView samplerView,
            GpuSampler sampler) {
        try {
            if (mesh == null || pipeline == null || samplerView == null || sampler == null) return;
            if (mesh.isBuilding()) mesh.end();
            if (mesh.getIndicesCount() <= 0) return;

            if (UiDeferredScheduler.shouldDefer()) {
                OrderedUiBatcher batcher = Renderer2D.UI_BATCHER;
                if (batcher.isActive() && (batcher.hasPendingWork() || UiRenderDispatcher.hasPendingCommands())) {
                    Renderer2D.BATCH_STATS.noteFlushReason(Renderer2D.FlushReason.TEXTURED_DIRECT);
                    batcher.flush(false);
                }
                MeshBuilder copy = copyMesh(mesh, pipeline);
                if (copy != null) {
                    UiDeferredScheduler.enqueue(new DeferredTexturedSubmit(
                            UiDeferredScheduler.layerForCurrentPhase(false),
                            UiDeferredScheduler.snapshotViewport(),
                            ScissorFunction.currentSnapshot(),
                            ClipFunction.currentSnapshot(),
                            samplerName,
                            samplerView,
                            sampler,
                            pipeline,
                            copy
                    ));
                }
                return;
            }

            OrderedUiBatcher batcher = Renderer2D.UI_BATCHER;
            if (batcher.isActive()) {
                if (batcher.hasPendingWork() || UiRenderDispatcher.hasPendingCommands()) {
                    Renderer2D.BATCH_STATS.noteFlushReason(Renderer2D.FlushReason.TEXTURED_DIRECT);
                }
                batcher.flush(false);
            }

            UiClipSnapshot clip = ClipFunction.currentSnapshot();
            String resolvedSamplerName = samplerName != null ? samplerName : "u_Texture";
            UiRenderDispatcher.submitImmediate(
                    "Renderer2D.TEXTURE",
                    1,
                    (context, rhi) -> draw(mesh, pipeline, resolvedSamplerName, samplerView, sampler, clip)
            );
        } finally {
            UiRenderDispatcher.flushLayer();
        }
    }

    private static void draw(MeshBuilder mesh,
                             RenderPipeline requestedPipeline,
                             String samplerName,
                             GpuTextureView samplerView,
                             GpuSampler sampler,
                             UiClipSnapshot clip) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        RenderTarget framebuffer = minecraft.gameRenderer.mainRenderTarget();
        if (framebuffer == null) return;

        RenderPipeline pipeline = requestedPipeline;
        boolean analyticClip = pipeline == CombatantRenderPipelines.UI_TEXTURED && clip.usesAnalyticPipeline();
        if (analyticClip) {
            pipeline = CombatantRenderPipelines.analyticClipTexturedPipeline(pipeline);
        }
        MeshRenderer draw = MeshRenderer.begin()
                    .attachments(UiMsaaClipLayer.currentColorAttachment(framebuffer.getColorTextureView()), null)
                .pipeline(pipeline)
                .mesh(mesh)
                .sampler(samplerName, samplerView, sampler);
        if (analyticClip) {
            draw.uniform("UIClip", UiClipUniforms.write(clip));
        }
        draw.end();
    }

    private static MeshBuilder copyMesh(MeshBuilder source, RenderPipeline pipeline) {
        if (source == null) return null;
        if (source.isBuilding()) source.end();
        if (source.getIndicesCount() <= 0 || source.getVertexCount() <= 0) return null;

        MeshBuilder copy = new MeshBuilder(pipeline);
        copy.beginScreen();
        copy.appendMesh(source);
        copy.end();
        return copy;
    }
}
