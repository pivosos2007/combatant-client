/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.world;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.core.policy.DepthProvider;
import combatant.client.render.engine.depth.PreTranslucentDepth;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.rhi.GpuMeshHandle;
import combatant.client.render.engine.rhi.RhiDrawCommand;
import combatant.client.render.engine.rhi.pipeline.DepthPolicy;
import combatant.client.render.engine.rhi.pipeline.RenderPipelineRegistry;
import combatant.client.render.engine.rhi.pipeline.RenderPipelineSpec;
import combatant.client.render.engine.uniform.MeshBuilder;

import java.util.List;

/**
 * Converts world commands into RHI draw commands and submits them through CombatantRHI.
 */
public final class WorldPassCompiler {
    private final WorldRenderStats stats = new WorldRenderStats();

    private static DepthPolicy effectiveDepthPolicy(Renderer3D.DepthMode mode, RenderPipelineSpec spec) {
        if (mode == Renderer3D.DepthMode.NONE) return DepthPolicy.NONE;
        if (mode == Renderer3D.DepthMode.PRE_DEPTH) return DepthPolicy.PRE_TRANSLUCENT;
        return spec != null ? spec.depthPolicy() : DepthPolicy.MAIN_FRAMEBUFFER;
    }

    public void beginFrame(long frameId) {
        stats.beginFrame(frameId);
    }

    public WorldRenderStatsSnapshot statsSnapshot() {
        return stats.snapshot();
    }

    public void submit(WorldCommandBuffer buffer, @Nullable RenderTarget framebuffer, @Nullable PoseStack matrices) {
        if (buffer == null || buffer.size() == 0) return;
        RenderFrameContext ctx = CombatantRenderSystem.ensureFrameContext();
        RenderTarget target = framebuffer != null
                ? framebuffer
                : (ctx.framebuffer() != null ? ctx.framebuffer().mainFramebuffer() : null);
        if (target == null) return;

        GpuTextureView colorView = target.getColorTextureView();
        if (colorView == null) return;

        Matrix4f transform = matrices != null ? matrices.last().pose() : null;
        Minecraft mc = Minecraft.getInstance();
        List<WorldDrawCommand> commands = buffer.commands();
        try {
            for (WorldDrawCommand command : commands) {
                stats.recordedCommand();
                submitOne(ctx, mc, target, colorView, transform, command);
            }
        } finally {
            buffer.clearAfterSubmit();
        }
    }

    private void submitOne(RenderFrameContext ctx,
                           @Nullable Minecraft mc,
                           RenderTarget framebuffer,
                           GpuTextureView colorView,
                           @Nullable Matrix4f transform,
                           WorldDrawCommand command) {
        MeshBuilder mesh = command.mesh();
        if (mesh == null) {
            stats.skippedEmptyCommand();
            return;
        }
        if (mesh.isBuilding()) mesh.end();
        if (mesh.getIndicesCount() <= 0) {
            stats.skippedEmptyCommand();
            return;
        }
        validateWorldMeshAnchor(ctx, mesh, command);

        GpuMeshHandle uploaded = null;
        MeshBuilder drawMesh = mesh;
        RenderPipeline drawPipeline = command.pipeline();
        boolean temporaryMesh = false;
        try {
            if (WorldWideLineMeshCompiler.shouldExpand(command)) {
                MeshBuilder wideMesh = WorldWideLineMeshCompiler.compile(command);
                if (wideMesh != null) {
                    drawMesh = wideMesh;
                    drawPipeline = WorldWideLineMeshCompiler.widePipeline(command.pipeline());
                    temporaryMesh = true;
                }
            }
            if (drawMesh.getIndicesCount() <= 0) {
                stats.skippedEmptyCommand();
                return;
            }

            uploaded = CombatantRenderSystem.rhi().dynamicMeshes().upload(drawMesh);
            RenderPipelineSpec spec = RenderPipelineRegistry.global().require(drawPipeline);
            RhiDrawCommand.Builder draw = RhiDrawCommand.builder("Combatant Renderer3D World Command")
                    .pipeline(drawPipeline)
                    .pipelineSpec(spec)
                    .colorAttachment(colorView)
                    .depthAttachment(resolveDepthView(ctx, command.depthMode(), spec, framebuffer, colorView))
                    .mesh(uploaded)
                    .transform(transform)
                    .applyWorldCameraY(true)
                    .lineWidth(1.0f);

            bindWorldPolicy(ctx, spec, draw);
            command.bindings().applyTo(draw, mc);

            CombatantRenderSystem.rhi().drawMesh(draw.build());
            uploaded = null;
            stats.submittedCommand(drawMesh.getVertexCount(), drawMesh.getIndicesCount());
        } finally {
            if (uploaded != null) uploaded.close();
            if (temporaryMesh && drawMesh != null) drawMesh.close();
        }
    }

    private void validateWorldMeshAnchor(RenderFrameContext ctx, MeshBuilder mesh, WorldDrawCommand command) {
        if (mesh.isWorldCameraAnchored()) {
            return;
        }
        String camera = ctx != null && ctx.camera() != null ? String.valueOf(ctx.camera().position()) : "<no-frame-camera>";
        throw new IllegalStateException("Renderer3D world command was built without explicit camera anchoring: pipeline="
                + command.pipeline()
                + ", depthMode=" + command.depthMode()
                + ", vertices=" + mesh.getVertexCount()
                + ", indices=" + mesh.getIndicesCount()
                + ", frameCamera=" + camera
                + ". Use MeshBuilder.beginWorld(cameraPos) for world meshes; begin()/beginScreen() is only safe for screen-space meshes.");
    }

    private void bindWorldPolicy(RenderFrameContext ctx, RenderPipelineSpec spec, RhiDrawCommand.Builder draw) {
        if (spec != null && spec.bindsWorldFog()) {
            GpuBufferSlice fog = ctx != null && ctx.fogProvider() != null ? ctx.fogProvider().fogUniformSlice() : null;
            if (fog != null) {
                draw.uniform("Fog", fog);
                stats.fogBinding();
            }
        }
    }

    private GpuTextureView resolveDepthView(RenderFrameContext ctx,
                                            Renderer3D.DepthMode mode,
                                            RenderPipelineSpec spec,
                                            RenderTarget framebuffer,
                                            GpuTextureView colorView) {
        DepthPolicy policy = effectiveDepthPolicy(mode, spec);
        if (!policy.needsDepthAttachment()) {
            stats.depthDisabledBinding();
            return null;
        }

        GpuTextureView depth = null;
        if (policy == DepthPolicy.PRE_TRANSLUCENT) {
            depth = PreTranslucentDepth.getDepthViewFor(colorView);
            stats.depthPrePassBinding();
        } else {
            DepthProvider provider = ctx != null ? ctx.depthProvider() : null;
            depth = provider != null ? provider.depthAttachment() : null;
            stats.depthMainBinding();
        }

        int requiredSamples = samples(colorView);
        if (depth != null && samples(depth) == requiredSamples) {
            return depth;
        }
        GpuTextureView framebufferDepth = framebuffer != null ? framebuffer.getDepthTextureView() : null;
        return framebufferDepth != null && samples(framebufferDepth) == requiredSamples
                ? framebufferDepth
                : null;
    }

    private static int samples(GpuTextureView view) {
        if (view == null || view.texture() == null) return 1;
        return view.texture() instanceof IMsaaTexture msaa && msaa.combatant$isMsaa()
                ? msaa.combatant$getSamples()
                : 1;
    }
}
