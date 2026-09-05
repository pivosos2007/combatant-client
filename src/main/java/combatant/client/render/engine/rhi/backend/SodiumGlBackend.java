/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.backend;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.rhi.*;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.profiler.RenderCostProfiler;
import combatant.client.render.engine.rhi.*;
import combatant.client.render.engine.rhi.backend.gl.clip.GlStencilShapeClipBackend;
import combatant.client.render.engine.rhi.backend.gl.state.SodiumGlPipelineStateBackend;
import combatant.client.render.engine.rhi.blit.GlTextureBlitter;
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
import java.util.HashMap;
import java.util.Map;

/**
 * Current production RHI backend for the Sodium/GL era.
 * Sodium is the priority integration target; Mojang GPU objects are used as the compatibility surface where needed.
 */
public final class SodiumGlBackend implements CombatantRhi {
    private static final int MAX_MULTI_DRAW_GROUP = 1024;
    private final RhiStats stats = new RhiStats();
    private RhiCapabilities capabilities;
    private boolean multiDrawRuntimeDisabled;
    private final Blaze3dDynamicMeshBackend dynamicMeshes = new Blaze3dDynamicMeshBackend(stats);
    private final Blaze3dFullscreenBackend fullscreen = new Blaze3dFullscreenBackend();
    private final GlTextureBlitter blitter = new GlTextureBlitter(stats);
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
    public RhiCapabilities capabilities() {
        if (capabilities == null) capabilities = RhiCapabilities.current();
        return capabilities;
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
            // One Blaze3D encoder owns the whole ordered RHI sequence. Individual render passes still
            // end only on attachment/clear barriers, but we avoid recreating an encoder wrapper for
            // every continuation segment and keep the backend submission shape compact.
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            int cursor = 0;
            while (cursor < commands.size()) {
                while (cursor < commands.size() && !drawable(commands.get(cursor))) cursor++;
                if (cursor >= commands.size()) return;

                int end = cursor + 1;
                RhiDrawCommand first = commands.get(cursor);
                while (end < commands.size() && sharesRenderPass(first, commands.get(end))) {
                    end++;
                }
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
            boolean multiDrawAvailable = !multiDrawRuntimeDisabled && capabilities().multiDrawDirectSeparate();

            int cursor = start;
            while (cursor < end) {
                RhiDrawCommand command = commands.get(cursor);
                int groupEnd = cursor + 1;
                if (multiDrawAvailable && multiDrawCandidate(command)) {
                    int hardEnd = Math.min(end, cursor + MAX_MULTI_DRAW_GROUP);
                    while (groupEnd < hardEnd && canMultiDrawTogether(command, commands.get(groupEnd))) {
                        groupEnd++;
                    }
                }

                boolean bindPipeline = command.pipeline != activePipeline;
                if (groupEnd - cursor >= 2) {
                    boolean multiDrawOk = false;
                    try {
                        drawMultiInPass(pass, commands, cursor, groupEnd, bindPipeline, bindings);
                        multiDrawOk = true;
                    } catch (UnsupportedOperationException | IllegalArgumentException ex) {
                        // DeviceFeatures said this path is available, but the active backend rejected
                        // the concrete call. Disable it for the rest of this RHI lifetime and fall
                        // back to ordinary Blaze3D indexed draws without probing GL ourselves.
                        multiDrawRuntimeDisabled = true;
                        bindings.invalidate();
                        DebugLog.warnOnce("rhi.multidraw.runtime.disabled",
                                "[RHI/GL] Blaze3D multiDrawIndexed rejected at runtime; disabling Combatant coalescing fallback. %s: %s",
                                ex.getClass().getSimpleName(), ex.getMessage());
                    }
                    if (!multiDrawOk) {
                        for (int i = cursor; i < groupEnd; i++) {
                            drawInPass(pass, commands.get(i), i == cursor, bindings);
                        }
                    }
                } else {
                    drawInPass(pass, command, bindPipeline, bindings);
                }
                activePipeline = command.pipeline;
                cursor = groupEnd;
            }
        }
    }

    private void drawInPass(RenderPass pass, RhiDrawCommand command, boolean bindPipeline, PassBindingCache bindings) {
        command.mesh.validateForDraw(command.label);
        try (RenderCostProfiler.Scope ignoredCost = RenderCostProfiler.rhiDraw(command.label)) {
            boolean pushMv = command.transform != null || command.applyWorldCameraY;
            float previousLineWidth = RenderState.lineWidth;
            try {
                RenderState.lineWidth = command.lineWidth > 0.0f ? command.lineWidth : previousLineWidth;
                if (pushMv) RenderSystem.getModelViewStack().pushMatrix();
                if (command.transform != null) RenderSystem.getModelViewStack().mul(command.transform);
                if (command.applyWorldCameraY) applyCameraPosY(RenderSystem.getModelViewStack());

                BindingCounts emitted = bindDrawState(pass, command, bindPipeline, bindings);
                stats.pipelineUse(command.pipeline, command.pipelineSpec, emitted.uniforms(), emitted.samplers(), bindPipeline);
                command.mesh.drawIndexed(pass, command.label);
                stats.drawCall();
            } finally {
                RenderState.lineWidth = previousLineWidth;
                if (pushMv) RenderSystem.getModelViewStack().popMatrix();
            }
        }
    }

    private void drawMultiInPass(RenderPass pass, List<RhiDrawCommand> commands, int start, int end,
                                 boolean bindPipeline, PassBindingCache bindings) {
        RhiDrawCommand first = commands.get(start);
        String label = first.label + " [multi x" + (end - start) + "]";
        try (RenderCostProfiler.Scope ignoredCost = RenderCostProfiler.rhiDraw(label);
             MemoryStack stack = MemoryStack.stackPush()) {
            for (int i = start; i < end; i++) {
                commands.get(i).mesh.validateForDraw(commands.get(i).label);
            }

            boolean pushMv = first.transform != null || first.applyWorldCameraY;
            float previousLineWidth = RenderState.lineWidth;
            try {
                RenderState.lineWidth = first.lineWidth > 0.0f ? first.lineWidth : previousLineWidth;
                if (pushMv) RenderSystem.getModelViewStack().pushMatrix();
                if (first.transform != null) RenderSystem.getModelViewStack().mul(first.transform);
                if (first.applyWorldCameraY) applyCameraPosY(RenderSystem.getModelViewStack());
                BindingCounts emitted = bindDrawState(pass, first, bindPipeline, bindings);

                int drawCount = end - start;
                PointerBuffer firstIndexOffsets = stack.mallocPointer(drawCount);
                java.nio.IntBuffer indexCounts = stack.mallocInt(drawCount);
                java.nio.IntBuffer baseVertices = stack.mallocInt(drawCount);
                int indexBytes = first.mesh.indexType().bytes;
                for (int i = start; i < end; i++) {
                    GpuMeshHandle mesh = commands.get(i).mesh;
                    int out = i - start;
                    firstIndexOffsets.put(out, (long) mesh.firstIndex() * indexBytes);
                    indexCounts.put(out, mesh.indexCount());
                    baseVertices.put(out, mesh.baseVertex());
                }

                pass.multiDrawIndexed(firstIndexOffsets, indexCounts, baseVertices, drawCount);
                stats.pipelineUse(first.pipeline, first.pipelineSpec, emitted.uniforms(), emitted.samplers(), bindPipeline);
                for (int i = start + 1; i < end; i++) {
                    RhiDrawCommand command = commands.get(i);
                    stats.pipelineUse(command.pipeline, command.pipelineSpec, 0, 0, false);
                }
                stats.multiDrawCall(drawCount);
            } finally {
                RenderState.lineWidth = previousLineWidth;
                if (pushMv) RenderSystem.getModelViewStack().popMatrix();
            }
        }
    }

    private BindingCounts bindDrawState(RenderPass pass, RhiDrawCommand command, boolean bindPipeline, PassBindingCache bindings) {
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
        if (bindPipeline) {
            if (CombatantRenderPipelines.isRigPipeline(command.pipeline)) {
                IrisRuntime.setNativePipeline(pass, command.pipeline);
            } else {
                pass.setPipeline(command.pipeline);
            }
        }

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
        return new BindingCounts(uniformBinds, samplerBinds);
    }

    private static boolean multiDrawCandidate(RhiDrawCommand command) {
        return drawable(command)
                && !CombatantRenderPipelines.isRigPipeline(command.pipeline);
    }

    private static boolean canMultiDrawTogether(RhiDrawCommand first, RhiDrawCommand next) {
        if (!multiDrawCandidate(next)) return false;
        return first.pipeline == next.pipeline
                && first.mesh.vertexBuffer() == next.mesh.vertexBuffer()
                && first.mesh.indexBuffer() == next.mesh.indexBuffer()
                && first.mesh.indexType() == next.mesh.indexType()
                && Float.compare(first.lineWidth, next.lineWidth) == 0
                && first.applyWorldCameraY == next.applyWorldCameraY
                && java.util.Objects.equals(first.transform, next.transform)
                && first.uniforms.equals(next.uniforms)
                && first.samplers.equals(next.samplers);
    }

    private record BindingCounts(int uniforms, int samplers) {
    }

    private record SamplerState(com.mojang.blaze3d.textures.GpuTextureView view,
                                com.mojang.blaze3d.textures.GpuSampler sampler) {
    }

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

        void invalidate() {
            uniforms.clear();
            samplers.clear();
            vertexBuffer = null;
            indexBuffer = null;
            indexType = null;
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
        msaa.close();
        dynamicMeshes.close();
        fullscreen.close();
        resources.close();
    }
}
