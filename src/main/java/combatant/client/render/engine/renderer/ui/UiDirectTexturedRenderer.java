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
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.ClipFunction;
import net.minecraft.client.Minecraft;

/** Immediate textured submission path kept outside the Renderer2D drawing facade. */
public final class UiDirectTexturedRenderer {
    private UiDirectTexturedRenderer() {
    }

    public static void submit(
            MeshBuilder mesh,
            String samplerName,
            GpuTextureView samplerView,
            GpuSampler sampler) {
        try {
            if (mesh == null || samplerView == null || sampler == null) return;
            if (mesh.isBuilding()) mesh.end();
            if (mesh.getIndicesCount() <= 0) return;

            if (UiDeferredScheduler.shouldDefer()) {
                MeshBuilder copy = copyMesh(mesh, CombatantRenderPipelines.UI_TEXTURED);
                if (copy != null) {
                    UiDeferredScheduler.enqueue(new DeferredTexturedSubmit(
                            UiDeferredScheduler.layerForCurrentPhase(false),
                            UiDeferredScheduler.snapshotViewport(),
                            ScissorFunction.currentSnapshot(),
                            ClipFunction.currentSnapshot(),
                            samplerName,
                            samplerView,
                            sampler,
                            copy
                    ));
                }
                return;
            }

            UiRenderDispatcher.recordBackendCommand("TEXTURED_DIRECT");
            OrderedUiBatcher batcher = Renderer2D.UI_BATCHER;
            if (batcher.isActive()) {
                if (batcher.hasPendingWork() || UiRenderDispatcher.hasPendingCommands()) {
                    Renderer2D.BATCH_STATS.noteFlushReason(Renderer2D.FlushReason.TEXTURED_DIRECT);
                }
                batcher.flush(false);
            }

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) return;
            RenderTarget framebuffer = minecraft.gameRenderer.mainRenderTarget();
            if (framebuffer == null) return;

            UiClipSnapshot clip = ClipFunction.currentSnapshot();
            RenderPipeline pipeline = CombatantRenderPipelines.UI_TEXTURED;
            if (clip.usesAnalyticPipeline()) {
                pipeline = CombatantRenderPipelines.analyticClipTexturedPipeline(pipeline);
            }
            MeshRenderer draw = MeshRenderer.begin()
                    .attachments(framebuffer.getColorTextureView(), null)
                    .pipeline(pipeline)
                    .mesh(mesh)
                    .sampler(samplerName, samplerView, sampler);
            if (clip.usesAnalyticPipeline()) {
                draw.uniform("UIClip", UiClipUniforms.write(clip));
            }
            draw.end();
        } finally {
            UiRenderDispatcher.flushLayer();
        }
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
