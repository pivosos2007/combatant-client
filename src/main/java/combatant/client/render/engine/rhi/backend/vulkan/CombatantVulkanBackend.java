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
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.rhi.*;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import combatant.client.render.engine.RenderState;
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
import combatant.client.render.engine.rhi.resource.RenderResourceManager;
import combatant.client.render.engine.rhi.state.PipelineStateBackend;
import combatant.client.render.engine.rhi.upload.DynamicMeshBackend;
import combatant.client.render.engine.rhi.upload.Blaze3dDynamicMeshBackend;
import combatant.client.render.engine.uniform.impl.MeshUniforms;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

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

    private static Matrix4f meshModelView(RhiDrawCommand command) {
        if (RenderState.rendering3D) {
            return new Matrix4f(RenderSystem.getModelViewStack());
        }
        if (command != null && command.transform != null) {
            return new Matrix4f(command.transform);
        }
        return new Matrix4f();
    }

    private static Matrix4f fullscreenModelView() {
        return RenderState.rendering3D
                ? new Matrix4f(RenderSystem.getModelViewStack())
                : new Matrix4f();
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
    public void drawMesh(RhiDrawCommand command) {
        if (command == null || command.mesh.indexCount() <= 0) return;
        command.mesh.validateForDraw(command.label);
        try (RenderCostProfiler.Scope ignoredCost = RenderCostProfiler.rhiDraw(command.label)) {
            boolean pushMv = command.transform != null || command.applyWorldCameraY;
            try {
                if (pushMv) RenderSystem.getModelViewStack().pushMatrix();
                if (command.transform != null) RenderSystem.getModelViewStack().mul(command.transform);
                if (command.applyWorldCameraY) applyCameraPosY(RenderSystem.getModelViewStack());

                MeshUniforms.update(
                        MeshRenderer.projection(),
                        meshModelView(command),
                        command.colorAttachment != null ? command.colorAttachment.getWidth(0) : 1.0f,
                        command.colorAttachment != null ? command.colorAttachment.getHeight(0) : 1.0f
                );
                GpuBufferSlice meshData = MeshUniforms.get();

                stats.renderPass(command.colorAttachment, command.depthAttachment);
                try (RenderPass pass = createPass(command.label, command.colorAttachment, command.clearColor, command.depthAttachment, command.clearDepth)) {
                    if (command.pipelineSpec == null) pipelines.require(command.pipeline);
                    pass.setPipeline(command.pipeline);
                    pass.setUniform("MeshData", meshData);
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
                }
            } finally {
                if (pushMv) RenderSystem.getModelViewStack().popMatrix();
                command.mesh.close();
            }
        }
    }

    @Override
    public void drawFullscreen(FullscreenDrawCommand command) {
        try (RenderCostProfiler.Scope ignoredCost = RenderCostProfiler.rhiDraw(command.label)) {
            fullscreen.ensureInitialized();
            MeshUniforms.update(
                    MeshRenderer.projection(),
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
