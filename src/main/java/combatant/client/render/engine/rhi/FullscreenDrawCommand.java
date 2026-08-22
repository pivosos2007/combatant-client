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
import combatant.client.render.engine.rhi.pipeline.RenderPipelineRegistry;
import combatant.client.render.engine.rhi.pipeline.RenderPipelineSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

public final class FullscreenDrawCommand {
    public final String label;
    public final RenderPipeline pipeline;
    public final RenderPipelineSpec pipelineSpec;
    public final GpuTextureView colorAttachment;
    public final OptionalInt clearColor;
    public final List<RhiUniformBinding> uniforms;
    public final List<RhiSamplerBinding> samplers;

    private FullscreenDrawCommand(Builder b) {
        this.label = b.label;
        this.pipeline = b.pipeline;
        this.pipelineSpec = b.pipelineSpec;
        this.colorAttachment = b.colorAttachment;
        this.clearColor = b.clearColor;
        this.uniforms = b.uniforms == null ? List.of() : List.copyOf(b.uniforms);
        this.samplers = b.samplers == null ? List.of() : List.copyOf(b.samplers);
    }

    public static Builder builder(String label) {
        return new Builder(label);
    }

    public static final class Builder {
        private final String label;
        private List<RhiUniformBinding> uniforms;
        private List<RhiSamplerBinding> samplers;
        private RenderPipeline pipeline;
        private RenderPipelineSpec pipelineSpec;
        private GpuTextureView colorAttachment;
        private OptionalInt clearColor = OptionalInt.empty();

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

        public Builder clearColor(@Nullable Integer argb) {
            this.clearColor = argb != null ? OptionalInt.of(argb) : OptionalInt.empty();
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

        public FullscreenDrawCommand build() {
            if (pipeline == null) throw new IllegalStateException("Fullscreen command without pipeline");
            if (pipelineSpec == null) pipelineSpec = RenderPipelineRegistry.global().require(pipeline);
            if (colorAttachment == null) throw new IllegalStateException("Fullscreen command without color attachment");
            return new FullscreenDrawCommand(this);
        }
    }
}
