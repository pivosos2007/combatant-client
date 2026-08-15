/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import combatant.client.render.engine.rhi.pipeline.RenderPipelineRegistry;
import combatant.client.render.engine.rhi.pipeline.RenderPipelineSpec;
import combatant.client.mixininterface.IRenderPipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class RhiDrawCommand {
    public final String label;
    public final RenderPipeline pipeline;
    public final RenderPipelineSpec pipelineSpec;
    public final GpuTextureView colorAttachment;
    public final @Nullable GpuTextureView depthAttachment;
    public final OptionalInt clearColor;
    public final OptionalDouble clearDepth;
    public final GpuMeshHandle mesh;
    public final @Nullable Matrix4f transform;
    public final boolean applyWorldCameraY;
    public final float lineWidth;
    public final List<RhiUniformBinding> uniforms;
    public final List<RhiSamplerBinding> samplers;

    private RhiDrawCommand(Builder b) {
        this.label = b.label;
        this.pipeline = b.pipeline;
        this.pipelineSpec = b.pipelineSpec;
        this.colorAttachment = b.colorAttachment;
        this.depthAttachment = b.depthAttachment;
        this.clearColor = b.clearColor;
        this.clearDepth = b.clearDepth;
        this.mesh = b.mesh;
        this.transform = b.transform;
        this.applyWorldCameraY = b.applyWorldCameraY;
        this.lineWidth = b.lineWidth;
        this.uniforms = List.copyOf(b.uniforms);
        this.samplers = List.copyOf(b.samplers);
    }

    public static Builder builder(String label) {
        return new Builder(label);
    }

    public boolean hasUniform(String name) {
        if (name == null) return false;
        for (RhiUniformBinding binding : uniforms) {
            if (name.equals(binding.name())) return true;
        }
        return false;
    }

    public static final class Builder {
        private final String label;
        private final List<RhiUniformBinding> uniforms = new ArrayList<>();
        private final List<RhiSamplerBinding> samplers = new ArrayList<>();
        private RenderPipeline pipeline;
        private RenderPipelineSpec pipelineSpec;
        private GpuTextureView colorAttachment;
        private @Nullable GpuTextureView depthAttachment;
        private OptionalInt clearColor = OptionalInt.empty();
        private OptionalDouble clearDepth = OptionalDouble.empty();
        private GpuMeshHandle mesh;
        private @Nullable Matrix4f transform;
        private boolean applyWorldCameraY;
        private float lineWidth = 1.0f;

        private Builder(String label) {
            this.label = label;
        }

        public Builder pipeline(RenderPipeline pipeline) {
            this.pipeline = pipeline;
            return this;
        }

        public Builder pipelineSpec(RenderPipelineSpec pipelineSpec) {
            this.pipelineSpec = pipelineSpec;
            return this;
        }

        public Builder colorAttachment(GpuTextureView colorAttachment) {
            this.colorAttachment = colorAttachment;
            return this;
        }

        public Builder depthAttachment(@Nullable GpuTextureView depthAttachment) {
            this.depthAttachment = depthAttachment;
            return this;
        }

        public Builder clearColor(@Nullable Integer argb) {
            this.clearColor = argb != null ? OptionalInt.of(argb) : OptionalInt.empty();
            return this;
        }

        public Builder clearDepth(OptionalDouble clearDepth) {
            this.clearDepth = clearDepth;
            return this;
        }

        public Builder mesh(GpuMeshHandle mesh) {
            this.mesh = mesh;
            return this;
        }

        public Builder transform(@Nullable Matrix4f transform) {
            this.transform = transform != null ? new Matrix4f(transform) : null;
            return this;
        }

        public Builder applyWorldCameraY(boolean applyWorldCameraY) {
            this.applyWorldCameraY = applyWorldCameraY;
            return this;
        }

        public Builder lineWidth(float lineWidth) {
            this.lineWidth = lineWidth > 0.0f ? lineWidth : 1.0f;
            return this;
        }

        public Builder uniform(String name, GpuBufferSlice slice) {
            if (name != null && slice != null) uniforms.add(new RhiUniformBinding(name, slice));
            return this;
        }

        public Builder sampler(String name, GpuTextureView view, GpuSampler sampler) {
            if (name != null && view != null && sampler != null)
                samplers.add(new RhiSamplerBinding(name, view, sampler));
            return this;
        }

        public RhiDrawCommand build() {
            if (pipeline == null) throw new IllegalStateException("RHI draw command without pipeline");
            if (transform != null && pipeline instanceof IRenderPipeline combatantPipeline
                    && !combatantPipeline.combatant$getContract().meshDataRequired()) {
                throw new IllegalStateException("Matrix transform requires an EXTENDED pipeline: " + pipeline.getLocation());
            }
            if (pipelineSpec == null) pipelineSpec = RenderPipelineRegistry.global().require(pipeline);
            if (colorAttachment == null) throw new IllegalStateException("RHI draw command without color attachment");
            if (mesh == null) throw new IllegalStateException("RHI draw command without mesh");
            return new RhiDrawCommand(this);
        }
    }
}
