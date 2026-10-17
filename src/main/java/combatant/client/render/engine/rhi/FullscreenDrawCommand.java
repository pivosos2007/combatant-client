/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;
import combatant.client.render.engine.rhi.pipeline.RenderPipelineRegistry;
import combatant.client.render.engine.rhi.pipeline.RenderPipelineSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Clip-space fullscreen draw. The persistent quad vertices already cover NDC [-1,+1], so backend
 * implementations must never inherit the active world projection/model-view for this command.
 * Pipelines may still declare the legacy MeshData block; in that case the backend binds identity
 * transforms and only the viewport payload is meaningful.
 */
public final class FullscreenDrawCommand {
    public final String label;
    public final RenderPipeline pipeline;
    public final RenderPipelineSpec pipelineSpec;
    public final GpuTextureView colorAttachment;
    public final @Nullable GpuTextureView depthAttachment;
    public final OptionalInt clearColor;
    public final OptionalDouble clearDepth;
    public final List<RhiUniformBinding> uniforms;
    public final List<RhiSamplerBinding> samplers;

    private FullscreenDrawCommand(Builder b) {
        this.label = b.label;
        this.pipeline = b.pipeline;
        this.pipelineSpec = b.pipelineSpec;
        this.colorAttachment = b.colorAttachment;
        this.depthAttachment = b.depthAttachment;
        this.clearColor = b.clearColor;
        this.clearDepth = b.clearDepth;
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
        private @Nullable GpuTextureView depthAttachment;
        private OptionalInt clearColor = OptionalInt.empty();
        private OptionalDouble clearDepth = OptionalDouble.empty();

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

        /** Attaches scene depth so a fullscreen/postprocess pipeline may use hardware depth testing. */
        public Builder depthAttachment(@Nullable GpuTextureView depthAttachment) {
            this.depthAttachment = depthAttachment;
            return this;
        }

        public Builder clearColor(@Nullable Integer argb) {
            this.clearColor = argb != null ? OptionalInt.of(argb) : OptionalInt.empty();
            return this;
        }

        public Builder clearDepth(OptionalDouble clearDepth) {
            this.clearDepth = clearDepth == null ? OptionalDouble.empty() : clearDepth;
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
            if (depthAttachment != null && (depthAttachment.getWidth(0) != colorAttachment.getWidth(0)
                    || depthAttachment.getHeight(0) != colorAttachment.getHeight(0))) {
                throw new IllegalStateException("Fullscreen color/depth dimensions differ: color="
                        + colorAttachment.getWidth(0) + "x" + colorAttachment.getHeight(0)
                        + ", depth=" + depthAttachment.getWidth(0) + "x" + depthAttachment.getHeight(0));
            }
            return new FullscreenDrawCommand(this);
        }
    }
}
