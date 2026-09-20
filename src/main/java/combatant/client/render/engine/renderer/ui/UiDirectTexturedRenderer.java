/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.RhiDrawCommand;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.uniform.impl.UiClipUniforms;
import combatant.client.render.engine.renderer.ui.clip.UiClipSnapshot;
import combatant.client.render.engine.renderer.ui.clip.UiMsaaClipLayer;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.ClipFunction;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/** Submits textured UI meshes. */
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
            Renderer2D.Deferred2DLayer layer = UiDeferredScheduler.layerForCurrentPhase(false);
            String resolvedSamplerName = samplerName != null ? samplerName : "u_Texture";
            UiRenderDispatcher.submitImmediate(
                    "Renderer2D.TEXTURE",
                    1,
                    (context, rhi) -> draw(
                            mesh, pipeline, resolvedSamplerName, samplerView, sampler, clip, layer, rhi)
            );
        } finally {
            UiRenderDispatcher.flushLayer();
        }
    }

    static void draw(MeshBuilder mesh,
                     RenderPipeline requestedPipeline,
                     String samplerName,
                     GpuTextureView samplerView,
                     GpuSampler sampler,
                     UiClipSnapshot clip,
                     Renderer2D.Deferred2DLayer layer,
                     CombatantRhi rhi) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameRenderer == null || rhi == null) return;
        RenderTarget framebuffer = minecraft.gameRenderer.mainRenderTarget();
        if (framebuffer == null) return;

        UiClipSnapshot resolvedClip = clip != null ? clip : UiClipSnapshot.NONE;
        RenderPipeline pipeline = requestedPipeline != null
                ? requestedPipeline
                : CombatantRenderPipelines.UI_TEXTURED;
        boolean analyticClip = pipeline == CombatantRenderPipelines.UI_TEXTURED
                && resolvedClip.usesAnalyticPipeline();
        if (analyticClip) {
            pipeline = CombatantRenderPipelines.analyticClipTexturedPipeline(pipeline);
        }
        MeshRenderer draw = MeshRenderer.begin()
                .attachments(UiMsaaClipLayer.currentColorAttachment(framebuffer.getColorTextureView()), null)
                .pipeline(pipeline)
                .mesh(mesh)
                .sampler(samplerName, samplerView, sampler);
        if (analyticClip) {
            draw.uniform("UIClip", UiClipUniforms.write(resolvedClip));
        }

        List<RhiDrawCommand> commands = new ArrayList<>(2);
        draw.endTo(commands);
        appendUiUnderlayReplay(commands, minecraft, layer, resolvedClip);
        try {
            if (!commands.isEmpty()) rhi.drawMeshes(commands);
        } finally {
            // Mirrors OrderedUiBatcher's exceptional-range cleanup. Backends normally release
            // submitted meshes themselves; handles are idempotent and this covers a partial submit.
            for (RhiDrawCommand command : commands) {
                if (command != null && command.mesh != null) command.mesh.close();
            }
        }
    }

    private static void appendUiUnderlayReplay(List<RhiDrawCommand> commands,
                                                Minecraft minecraft,
                                                Renderer2D.Deferred2DLayer layer,
                                                UiClipSnapshot clip) {
        if (commands == null || commands.isEmpty() || minecraft == null || layer == null) return;
        if (clip != null && clip.usesMsaaStencil()) return;
        if (!UiBlurResources.isUiUnderlayRequested(layer)) return;

        TextureTarget underlay = UiBlurResources.uiUnderlayTarget(minecraft, layer);
        GpuTextureView underlayView = underlay != null ? underlay.getColorTextureView() : null;
        if (underlayView == null) return;

        int baseCount = commands.size();
        for (int i = 0; i < baseCount; i++) {
            RhiDrawCommand command = commands.get(i);
            if (command != null && command.colorAttachment != underlayView) {
                commands.add(command.retargetColor(" [UI underlay]", underlayView));
            }
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
