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
import combatant.client.render.engine.rhi.pipeline.PipelineDomain;

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
    public final boolean uiUnderlayReplay;

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
        this.uniforms = b.uniforms == null ? List.of() : List.copyOf(b.uniforms);
        this.samplers = b.samplers == null ? List.of() : List.copyOf(b.samplers);
        this.uiUnderlayReplay = b.uiUnderlayReplay;
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

    /**
     * Replays the same uploaded geometry and bindings into another color attachment. The mesh
     * handle remains owned by the combined command stream and is closed once all replays finish.
     */
    public RhiDrawCommand retargetColor(String labelSuffix, GpuTextureView colorAttachment) {
        Builder copy = builder(label + (labelSuffix != null ? labelSuffix : ""))
                .pipeline(pipeline)
                .pipelineSpec(pipelineSpec)
                .colorAttachment(colorAttachment)
                .depthAttachment(null)
                .mesh(mesh)
                .transform(transform)
                .applyWorldCameraY(applyWorldCameraY)
                .lineWidth(lineWidth)
                .uiUnderlayReplay(true);
        for (RhiUniformBinding uniform : uniforms) {
            copy.uniform(uniform.name(), uniform.slice());
        }
        for (RhiSamplerBinding sampler : samplers) {
            copy.sampler(sampler.name(), sampler.view(), sampler.sampler());
        }
        return copy.build();
    }

    public static final class Builder {
        private final String label;
        private List<RhiUniformBinding> uniforms;
        private List<RhiSamplerBinding> samplers;
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
        private boolean uiUnderlayReplay;

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

        public Builder uiUnderlayReplay(boolean uiUnderlayReplay) {
            this.uiUnderlayReplay = uiUnderlayReplay;
            return this;
        }

        public Builder uniform(String name, GpuBufferSlice slice) {
            if (name != null && slice != null) {
                if (uniforms == null) uniforms = new ArrayList<>(2);
                uniforms.add(new RhiUniformBinding(name, slice));
            }
            return this;
        }

        public Builder sampler(String name, GpuTextureView view, GpuSampler sampler) {
            if (name != null && view != null && sampler != null) {
                if (samplers == null) samplers = new ArrayList<>(2);
                samplers.add(new RhiSamplerBinding(name, view, sampler));
            }
            return this;
        }

        public RhiDrawCommand build() {
            if (pipeline == null) throw new IllegalStateException("RHI draw command without pipeline");
            if (pipelineSpec == null) pipelineSpec = RenderPipelineRegistry.global().require(pipeline);
            if (transform != null && !pipelineSpec.metadata().transformPolicy().supportsObjectTransform()) {
                throw new IllegalStateException("Matrix transform is not supported by pipeline metadata: " + pipeline.getLocation());
            }
            if (pipelineSpec.metadata().domain() != PipelineDomain.UNKNOWN) {
                if (uniforms != null) {
                    for (RhiUniformBinding uniform : uniforms) {
                        if (!pipelineSpec.uniformLayout().hasUniform(uniform.name())) {
                            throw new IllegalStateException("Uniform '" + uniform.name()
                                    + "' is not declared by pipeline metadata: " + pipeline.getLocation());
                        }
                    }
                }
                if (samplers != null) {
                    for (RhiSamplerBinding sampler : samplers) {
                        if (!pipelineSpec.uniformLayout().hasSampler(sampler.name())) {
                            throw new IllegalStateException("Sampler '" + sampler.name()
                                    + "' is not declared by pipeline metadata: " + pipeline.getLocation());
                        }
                    }
                }
            }
            if (colorAttachment == null) throw new IllegalStateException("RHI draw command without color attachment");
            if (mesh == null) throw new IllegalStateException("RHI draw command without mesh");
            return new RhiDrawCommand(this);
        }
    }
}
