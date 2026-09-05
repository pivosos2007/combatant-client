/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.backend.vulkan;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.CommandEncoder;
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
import combatant.client.render.engine.rhi.backend.vulkan.clip.VulkanShapeClipBackend;
import combatant.client.render.engine.rhi.backend.vulkan.msaa.VulkanMsaaControl;
import combatant.client.render.engine.rhi.backend.vulkan.state.VulkanPipelineStateBackend;
import combatant.client.render.engine.rhi.backend.vulkan.util.VulkanRenderStateBridge;
import combatant.client.render.engine.rhi.blit.Blaze3dTextureBlitter;
import combatant.client.render.engine.rhi.blit.TextureBlitter;
import combatant.client.render.engine.rhi.fullscreen.FullscreenBackend;
import combatant.client.render.engine.rhi.fullscreen.Blaze3dFullscreenBackend;
import combatant.client.render.engine.rhi.msaa.MsaaControl;
import combatant.client.render.engine.rhi.pipeline.RenderPipelineRegistry;
import combatant.client.render.engine.rhi.pipeline.RenderPipelineSpec;
import combatant.client.render.engine.rhi.resource.RenderResourceManager;
import combatant.client.render.engine.rhi.state.PipelineStateBackend;
import combatant.client.render.engine.rhi.upload.DynamicMeshBackend;
import combatant.client.render.engine.rhi.upload.Blaze3dDynamicMeshBackend;
import combatant.client.render.engine.uniform.impl.MeshUniforms;
import combatant.client.render.engine.uniform.impl.UIBatchUniforms;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Combatant RHI backend for Mojang's Vulkan renderer.
 */
public final class CombatantVulkanBackend implements CombatantRhi {
    private final RhiStats stats = new RhiStats();
    private final Blaze3dDynamicMeshBackend dynamicMeshes = new Blaze3dDynamicMeshBackend(stats);
    private final Blaze3dFullscreenBackend fullscreen = new Blaze3dFullscreenBackend();
    private final Blaze3dTextureBlitter blitter = new Blaze3dTextureBlitter(stats);
    private final VulkanMsaaControl msaa = new VulkanMsaaControl();
    private final VulkanShapeClipBackend shapeClip = new VulkanShapeClipBackend();
    private final VulkanPipelineStateBackend pipelineState = new VulkanPipelineStateBackend(msaa, shapeClip);
    private final RenderPipelineRegistry pipelines = RenderPipelineRegistry.global();
    private final RenderResourceManager resources = new RenderResourceManager();
    private final Matrix4f projectionScratch = new Matrix4f();
    private final Matrix4f modelViewScratch = new Matrix4f();

    public CombatantVulkanBackend() {
        VulkanRenderStateBridge.setVulkanBackendActive(true);
    }

    private static Optional<Vector4fc> clearColor(OptionalInt clearColor) {
        if (clearColor == null || clearColor.isEmpty()) return Optional.empty();
        int argb = clearColor.getAsInt();
        float a = ((argb >>> 24) & 0xFF) / 255.0f;
        float r = ((argb >>> 16) & 0xFF) / 255.0f;
        float g = ((argb >>> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        return Optional.of(new Vector4f(r, g, b, a));
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

    private static void applyCameraPosY(Matrix4fStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null || mc.gameRenderer.mainCamera() == null) return;
        double y = mc.gameRenderer.mainCamera().position().y;
        stack.translate(0.0f, (float) -y, 0.0f);
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
    public VulkanShapeClipBackend shapeClip() {
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
        resources.beginFrame();
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
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            int cursor = 0;
            while (cursor < commands.size()) {
                while (cursor < commands.size() && !drawable(commands.get(cursor))) cursor++;
                if (cursor >= commands.size()) return;

                int end = cursor + 1;
                RhiDrawCommand first = commands.get(cursor);
                while (end < commands.size() && sharesRenderPass(first, commands.get(end))) end++;
                drawPass(encoder, commands, cursor, end);
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

    private void drawPass(CommandEncoder encoder, List<RhiDrawCommand> commands, int start, int end) {
        RhiDrawCommand first = commands.get(start);
        stats.renderPass(first.colorAttachment, first.depthAttachment);
        try (RenderPass pass = createPass(encoder, first.label, first.colorAttachment, first.clearColor, first.depthAttachment, first.clearDepth)) {
            PassBindingCache bindings = new PassBindingCache();
            com.mojang.blaze3d.pipeline.RenderPipeline activePipeline = null;
            for (int i = start; i < end; i++) {
                RhiDrawCommand command = commands.get(i);
                boolean bindPipeline = command.pipeline != activePipeline;
                drawInPass(pass, command, bindPipeline, bindings);
                activePipeline = command.pipeline;
            }
        }
    }

    private void drawInPass(RenderPass pass,
                            RhiDrawCommand command,
                            boolean bindPipeline,
                            PassBindingCache bindings) {
        command.mesh.validateForDraw(command.label);
        try (RenderCostProfiler.Scope ignoredCost = RenderCostProfiler.rhiDraw(command.label)) {
            boolean pushMv = command.transform != null || command.applyWorldCameraY;
            try {
                if (pushMv) RenderSystem.getModelViewStack().pushMatrix();
                if (command.transform != null) RenderSystem.getModelViewStack().mul(command.transform);
                if (command.applyWorldCameraY) applyCameraPosY(RenderSystem.getModelViewStack());

                GpuBufferSlice meshData = null;
                if (requiresMeshData(command.pipelineSpec)) {
                    MeshUniforms.update(MeshRenderer.copyProjection(projectionScratch), meshModelView(command),
                            command.colorAttachment.getWidth(0), command.colorAttachment.getHeight(0));
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
                if (bindPipeline) pass.setPipeline(command.pipeline);
                int uniformBinds = 0;
                int samplerBinds = 0;
                if (meshData != null && bindings.bindUniform(pass, "MeshData", meshData)) uniformBinds++;
                if (uiBatch != null && bindings.bindUniform(pass, "UIBatch", uiBatch)) uniformBinds++;
                for (RhiUniformBinding uniform : command.uniforms) {
                    if (bindings.bindUniform(pass, uniform.name(), uniform.slice())) uniformBinds++;
                }
                for (RhiSamplerBinding sampler : command.samplers) {
                    if (bindings.bindSampler(pass, sampler)) samplerBinds++;
                }
                bindings.bindMesh(pass, command.mesh);
                stats.pipelineUse(
                        command.pipeline,
                        command.pipelineSpec,
                        uniformBinds,
                        samplerBinds,
                        bindPipeline
                );
                command.mesh.drawIndexed(pass, command.label);
                stats.drawCall();
            } finally {
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

    private record SamplerState(com.mojang.blaze3d.textures.GpuTextureView view,
                                com.mojang.blaze3d.textures.GpuSampler sampler) {
    }

    /** Avoids descriptor/buffer setter churn inside one compatible Vulkan dynamic-rendering scope. */
    private static final class PassBindingCache {
        private final Map<String, GpuBufferSlice> uniforms = new HashMap<>();
        private final Map<String, SamplerState> samplers = new HashMap<>();
        private com.mojang.blaze3d.buffers.GpuBuffer vertexBuffer;
        private com.mojang.blaze3d.buffers.GpuBuffer indexBuffer;
        private com.mojang.blaze3d.IndexType indexType;

        boolean bindUniform(RenderPass pass, String name, GpuBufferSlice slice) {
            if (slice.equals(uniforms.get(name))) return false;
            pass.setUniform(name, slice);
            uniforms.put(name, slice);
            return true;
        }

        boolean bindSampler(RenderPass pass, RhiSamplerBinding binding) {
            SamplerState next = new SamplerState(binding.view(), binding.sampler());
            if (next.equals(samplers.get(binding.name()))) return false;
            pass.bindTexture(binding.name(), binding.view(), binding.sampler());
            samplers.put(binding.name(), next);
            return true;
        }

        void bindMesh(RenderPass pass, GpuMeshHandle mesh) {
            if (vertexBuffer != mesh.vertexBuffer()) {
                pass.setVertexBuffer(0, mesh.vertexBuffer().slice());
                vertexBuffer = mesh.vertexBuffer();
            }
            if (indexBuffer != mesh.indexBuffer() || indexType != mesh.indexType()) {
                pass.setIndexBuffer(mesh.indexBuffer(), mesh.indexType());
                indexBuffer = mesh.indexBuffer();
                indexType = mesh.indexType();
            }
        }
    }

    @Override
    public void drawFullscreen(FullscreenDrawCommand command) {
        try (RenderCostProfiler.Scope ignoredCost = RenderCostProfiler.rhiDraw(command.label)) {
            fullscreen.ensureInitialized();
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
                stats.pipelineUse(command.pipeline, command.pipelineSpec,
                        command.uniforms.size() + 1, command.samplers.size(), true);
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

    private RenderPass createPass(CommandEncoder encoder,
                                  String label,
                                  com.mojang.blaze3d.textures.GpuTextureView color,
                                  OptionalInt clearColor,
                                  com.mojang.blaze3d.textures.GpuTextureView depth,
                                  OptionalDouble clearDepth) {
        if (depth != null) {
            return encoder.createRenderPass(() -> label, color, clearColor(clearColor), depth, clearDepth);
        }
        return encoder.createRenderPass(() -> label, color, clearColor(clearColor));
    }

    @Override
    public void close() {
        try {
            dynamicMeshes.close();
            fullscreen.close();
            shapeClip.close();
            resources.close();
        } finally {
            VulkanRenderStateBridge.setVulkanBackendActive(false);
        }
    }
}
