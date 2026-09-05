/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.backend;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.rhi.*;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.profiler.RenderCostProfiler;
import combatant.client.render.engine.rhi.*;
import combatant.client.render.engine.rhi.backend.gl.clip.GlStencilShapeClipBackend;
import combatant.client.render.engine.rhi.backend.gl.state.SodiumGlPipelineStateBackend;
import combatant.client.render.engine.rhi.blit.Blaze3dTextureBlitter;
import combatant.client.render.engine.rhi.blit.TextureBlitter;
import combatant.client.render.engine.rhi.clip.ShapeClipBackend;
import combatant.client.render.engine.rhi.fullscreen.FullscreenBackend;
import combatant.client.render.engine.rhi.fullscreen.Blaze3dFullscreenBackend;
import combatant.client.render.engine.rhi.msaa.MsaaControl;
import combatant.client.render.engine.rhi.msaa.SodiumGlMsaaControl;
import combatant.client.render.engine.rhi.pipeline.RenderPipelineRegistry;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.render.engine.rhi.pipeline.RenderPipelineSpec;
import combatant.client.render.engine.rhi.resource.RenderResourceManager;
import combatant.client.render.engine.rhi.state.PipelineStateBackend;
import combatant.client.render.engine.rhi.upload.DynamicMeshBackend;
import combatant.client.render.engine.rhi.upload.Blaze3dDynamicMeshBackend;
import combatant.client.render.engine.uniform.impl.MeshUniforms;
import combatant.client.render.engine.uniform.impl.UIBatchUniforms;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.List;

/**
 * Current production RHI backend for the Sodium/GL era.
 * Sodium is the priority integration target; Mojang GPU objects are used as the compatibility surface where needed.
 */
public final class SodiumGlBackend implements CombatantRhi {
    private final RhiStats stats = new RhiStats();
    private final Blaze3dDynamicMeshBackend dynamicMeshes = new Blaze3dDynamicMeshBackend(stats);
    private final Blaze3dFullscreenBackend fullscreen = new Blaze3dFullscreenBackend();
    private final Blaze3dTextureBlitter blitter = new Blaze3dTextureBlitter(stats);
    private final SodiumGlMsaaControl msaa = new SodiumGlMsaaControl();
    private final GlStencilShapeClipBackend shapeClip = new GlStencilShapeClipBackend();
    private final SodiumGlPipelineStateBackend pipelineState = new SodiumGlPipelineStateBackend(msaa, shapeClip);
    private final RenderPipelineRegistry pipelines = RenderPipelineRegistry.global();
    private final RenderResourceManager resources = new RenderResourceManager();
    private final Matrix4f projectionScratch = new Matrix4f();
    private final Matrix4f modelViewScratch = new Matrix4f();

    private static java.util.Optional<Vector4fc> clearColor(OptionalInt clearColor) {
        if (clearColor == null || clearColor.isEmpty()) {
            return java.util.Optional.empty();
        }
        int argb = clearColor.getAsInt();
        float a = ((argb >>> 24) & 0xFF) / 255.0f;
        float r = ((argb >>> 16) & 0xFF) / 255.0f;
        float g = ((argb >>> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        return java.util.Optional.of(new Vector4f(r, g, b, a));
    }

    private Matrix4f meshModelView(RhiDrawCommand command) {
        if (RenderState.rendering3D) {
            return modelViewScratch.set(RenderSystem.getModelViewStack());
        }
        if (command != null && command.transform != null) {
            return modelViewScratch.set(command.transform);
        }
        return modelViewScratch.identity();
    }

    private Matrix4f fullscreenModelView() {
        return RenderState.rendering3D
                ? modelViewScratch.set(RenderSystem.getModelViewStack())
                : modelViewScratch.identity();
    }

    private static void applyCameraPosY(Matrix4fStack mv) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null || mc.gameRenderer.mainCamera() == null) return;
        var cameraPos = mc.gameRenderer.mainCamera().position();
        mv.translate(0.0f, (float) -cameraPos.y, 0.0f);
    }

    @Override
    public DynamicMeshBackend dynamicMeshes() {
        return dynamicMeshes;
    }

    @Override
    public FullscreenBackend fullscreen() {
        return fullscreen;
    }

    @Override
    public TextureBlitter textureBlitter() {
        return blitter;
    }

    @Override
    public MsaaControl msaa() {
        return msaa;
    }

    @Override
    public ShapeClipBackend shapeClip() {
        return shapeClip;
    }

    @Override
    public PipelineStateBackend pipelineState() {
        return pipelineState;
    }

    @Override
    public RhiStats stats() {
        return stats;
    }

    @Override
    public RenderPipelineRegistry pipelines() {
        return pipelines;
    }

    @Override
    public RenderResourceManager resources() {
        return resources;
    }

    @Override
    public void beginFrame(long frameId) {
        stats.beginFrame(frameId);
        dynamicMeshes.beginFrame(frameId);
    }

    @Override
    public void endRenderSubmission() {
        dynamicMeshes.endSubmission();
    }

    @Override
    public void framePresented() {
        dynamicMeshes.framePresented();
        resources.onFramePresented();
    }

    @Override
    public void drawMeshes(List<RhiDrawCommand> commands) {
        if (commands == null || commands.isEmpty()) return;
        try {
            int cursor = 0;
            while (cursor < commands.size()) {
                while (cursor < commands.size() && !drawable(commands.get(cursor))) cursor++;
                if (cursor >= commands.size()) return;

                int end = cursor + 1;
                RhiDrawCommand first = commands.get(cursor);
                while (end < commands.size() && sharesRenderPass(first, commands.get(end))) {
                    end++;
                }
                drawPass(commands, cursor, end);
                cursor = end;
            }
        } finally {
            closeMeshes(commands);
        }
    }

    private static void closeMeshes(List<RhiDrawCommand> commands) {
        for (RhiDrawCommand command : commands) {
            if (command != null && command.mesh != null) command.mesh.close();
        }
    }

    private void drawPass(List<RhiDrawCommand> commands, int start, int end) {
        RhiDrawCommand first = commands.get(start);
        stats.renderPass(first.colorAttachment, first.depthAttachment);
        try (RenderPass pass = createPass(first.label, first.colorAttachment, first.clearColor, first.depthAttachment, first.clearDepth)) {
            for (int i = start; i < end; i++) {
                drawInPass(pass, commands.get(i));
            }
        }
    }

    private void drawInPass(RenderPass pass, RhiDrawCommand command) {
        command.mesh.validateForDraw(command.label);
        try (RenderCostProfiler.Scope ignoredCost = RenderCostProfiler.rhiDraw(command.label)) {
            boolean pushMv = command.transform != null || command.applyWorldCameraY;
            float previousLineWidth = RenderState.lineWidth;
            try {
                RenderState.lineWidth = command.lineWidth > 0.0f ? command.lineWidth : previousLineWidth;
                if (pushMv) RenderSystem.getModelViewStack().pushMatrix();
                if (command.transform != null) RenderSystem.getModelViewStack().mul(command.transform);
                if (command.applyWorldCameraY) applyCameraPosY(RenderSystem.getModelViewStack());

                GpuBufferSlice meshData = null;
                if (requiresMeshData(command.pipelineSpec)) {
                    MeshUniforms.update(
                            MeshRenderer.copyProjection(projectionScratch),
                            meshModelView(command),
                            command.colorAttachment.getWidth(0),
                            command.colorAttachment.getHeight(0)
                    );
                    meshData = MeshUniforms.get();
                }
                GpuBufferSlice uiBatch = null;
                if (requiresUiBatch(command.pipelineSpec) && !command.hasUniform("UIBatch")) {
                    ViewportContext viewport = ViewportContext.current();
                    UIBatchUniforms.update(
                            viewport != null ? viewport.framebufferWidth() : command.colorAttachment.getWidth(0),
                            viewport != null ? viewport.framebufferHeight() : command.colorAttachment.getHeight(0));
                    uiBatch = UIBatchUniforms.get();
                }

                if (command.pipelineSpec == null) pipelines.require(command.pipeline);
                if (CombatantRenderPipelines.isRigPipeline(command.pipeline)) {
                    IrisRuntime.setNativePipeline(pass, command.pipeline);
                } else {
                    pass.setPipeline(command.pipeline);
                }
                if (meshData != null) pass.setUniform("MeshData", meshData);
                if (uiBatch != null) pass.setUniform("UIBatch", uiBatch);
                for (RhiUniformBinding uniform : command.uniforms) {
                    pass.setUniform(uniform.name(), uniform.slice());
                }
                for (RhiSamplerBinding sampler : command.samplers) {
                    pass.bindTexture(sampler.name(), sampler.view(), sampler.sampler());
                }
                pass.setVertexBuffer(0, command.mesh.vertexBuffer().slice());
                pass.setIndexBuffer(command.mesh.indexBuffer(), command.mesh.indexType());
                command.mesh.drawIndexed(pass, command.label);
                stats.drawCall();
            } finally {
                RenderState.lineWidth = previousLineWidth;
                if (pushMv) RenderSystem.getModelViewStack().popMatrix();
            }
        }
    }

    private static boolean drawable(RhiDrawCommand command) {
        return command != null && command.mesh != null && command.mesh.indexCount() > 0;
    }

    private static boolean sharesRenderPass(RhiDrawCommand first, RhiDrawCommand next) {
        if (!drawable(next)) return false;
        return RenderPassCompatibility.canContinue(
                first.colorAttachment, first.depthAttachment,
                next.colorAttachment, next.depthAttachment,
                next.clearColor.isPresent(), next.clearDepth.isPresent()
        );
    }

    private static boolean requiresMeshData(RenderPipelineSpec pipeline) {
        return pipeline != null && pipeline.metadata().requiresUniform("MeshData");
    }

    private static boolean requiresUiBatch(RenderPipelineSpec pipeline) {
        return pipeline != null && pipeline.metadata().requiresUniform("UIBatch");
    }

    @Override
    public void drawFullscreen(FullscreenDrawCommand command) {
        try (RenderCostProfiler.Scope ignoredCost = RenderCostProfiler.rhiDraw(command.label)) {
            fullscreen.ensureInitialized();

            /*
             * Fullscreen pipelines still use the shared MeshData uniform block through their vertex shader
             * (for example damage_tint.vert). The old MeshRenderer path always wrote
             * this UBO before drawing. The RHI fullscreen path must do the same; otherwise the shader reads
             * a stale/undefined projection and the NDC quad can be transformed into a tiny corner viewport.
             */
            MeshUniforms.update(
                    MeshRenderer.copyProjection(projectionScratch),
                    fullscreenModelView(),
                    command.colorAttachment != null ? command.colorAttachment.getWidth(0) : 1.0f,
                    command.colorAttachment != null ? command.colorAttachment.getHeight(0) : 1.0f
            );
            GpuBufferSlice meshData = MeshUniforms.get();

            stats.renderPass(command.colorAttachment, null);
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> command.label,
                    command.colorAttachment,
                    clearColor(command.clearColor)
            )) {
                if (command.pipelineSpec == null) pipelines.require(command.pipeline);
                pass.setPipeline(command.pipeline);
                pass.setUniform("MeshData", meshData);
                for (RhiUniformBinding uniform : command.uniforms) {
                    pass.setUniform(uniform.name(), uniform.slice());
                }
                for (RhiSamplerBinding sampler : command.samplers) {
                    pass.bindTexture(sampler.name(), sampler.view(), sampler.sampler());
                }
                GpuMeshHandle quad = fullscreen.quad();
                pass.setVertexBuffer(0, quad.vertexBuffer().slice());
                pass.setIndexBuffer(quad.indexBuffer(), quad.indexType());
                quad.drawIndexed(pass, command.label + " fullscreen quad");
                stats.drawCall();
                stats.fullscreenPass();
            }
        }
    }

    private RenderPass createPass(String label,
                                  com.mojang.blaze3d.textures.GpuTextureView color,
                                  OptionalInt clearColor,
                                  com.mojang.blaze3d.textures.GpuTextureView depth,
                                  OptionalDouble clearDepth) {
        if (depth != null) {
            return RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> label, color, clearColor(clearColor), depth, clearDepth);
        }
        return RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> label, color, clearColor(clearColor));
    }

    @Override
    public void close() {
        dynamicMeshes.close();
        fullscreen.close();
        resources.close();
    }
}
