/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.ColorTargetState;
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
    public final List<RhiColorAttachment> colorAttachments;
    /** Compatibility alias for color slot zero. */
    public final GpuTextureView colorAttachment;
    public final @Nullable GpuTextureView depthAttachment;
    /** Compatibility alias for the clear value of color slot zero. */
    public final OptionalInt clearColor;
    public final OptionalDouble clearDepth;
    public final GpuMeshHandle mesh;
    public final @Nullable Matrix4f transform;
    public final boolean applyWorldCameraY;
    public final float lineWidth;
    public final List<RhiUniformBinding> uniforms;
    public final List<RhiSamplerBinding> samplers;
    public final boolean uiUnderlayReplay;

    private RhiDrawCommand(Builder b, List<RhiColorAttachment> colors) {
        this.label = b.label;
        this.pipeline = b.pipeline;
        this.pipelineSpec = b.pipelineSpec;
        this.colorAttachments = colors;
        this.colorAttachment = colors.getFirst().view();
        this.depthAttachment = b.depthAttachment;
        this.clearColor = colors.getFirst().clearColor();
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

    public boolean clearsAnyColorAttachment() {
        for (RhiColorAttachment attachment : colorAttachments) {
            if (attachment.clearColor().isPresent()) return true;
        }
        return false;
    }

    /**
     * Replays the same uploaded geometry and bindings into one color attachment. The mesh handle
     * remains owned by the combined command stream and is closed once all replays finish.
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
        for (RhiUniformBinding uniform : uniforms) copy.uniform(uniform.name(), uniform.slice());
        for (RhiSamplerBinding sampler : samplers) copy.sampler(sampler.name(), sampler.view(), sampler.sampler());
        return copy.build();
    }

    public static final class Builder {
        private final String label;
        private List<RhiUniformBinding> uniforms;
        private List<RhiSamplerBinding> samplers;
        private RenderPipeline pipeline;
        private RenderPipelineSpec pipelineSpec;
        private final List<@Nullable GpuTextureView> colorViews = new ArrayList<>(1);
        private final List<OptionalInt> colorClears = new ArrayList<>(1);
        private @Nullable GpuTextureView depthAttachment;
        private OptionalDouble clearDepth = OptionalDouble.empty();
        private GpuMeshHandle mesh;
        private @Nullable Matrix4f transform;
        private boolean applyWorldCameraY;
        private float lineWidth = 1.0f;
        private boolean uiUnderlayReplay;

        private Builder(String label) {
            this.label = label == null || label.isBlank() ? "combatant-draw" : label;
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
            return colorAttachment(0, colorAttachment);
        }

        public Builder colorAttachment(int index, GpuTextureView colorAttachment) {
            validateColorAttachmentIndex(index);
            if (colorAttachment == null) throw new IllegalArgumentException("colorAttachment");
            ensureColorSlot(index);
            colorViews.set(index, colorAttachment);
            return this;
        }

        public Builder unusedColorAttachment(int index) {
            validateColorAttachmentIndex(index);
            ensureColorSlot(index);
            colorViews.set(index, null);
            colorClears.set(index, OptionalInt.empty());
            return this;
        }

        public Builder depthAttachment(@Nullable GpuTextureView depthAttachment) {
            this.depthAttachment = depthAttachment;
            return this;
        }

        public Builder clearColor(@Nullable Integer argb) {
            return clearColor(0, argb);
        }

        public Builder clearColor(int index, @Nullable Integer argb) {
            validateColorAttachmentIndex(index);
            ensureColorSlot(index);
            colorClears.set(index, argb != null ? OptionalInt.of(argb) : OptionalInt.empty());
            return this;
        }

        public Builder clearDepth(OptionalDouble clearDepth) {
            this.clearDepth = clearDepth == null ? OptionalDouble.empty() : clearDepth;
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
            validateBindings();
            List<RhiColorAttachment> colors = buildColorAttachments();
            if (mesh == null) throw new IllegalStateException("RHI draw command without mesh");
            return new RhiDrawCommand(this, colors);
        }

        private void validateBindings() {
            if (pipelineSpec.metadata().domain() == PipelineDomain.UNKNOWN) return;
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

        private List<RhiColorAttachment> buildColorAttachments() {
            if (colorViews.isEmpty() || colorViews.getFirst() == null) {
                throw new IllegalStateException("RHI draw command requires color attachment zero");
            }
            ArrayList<RhiColorAttachment> colors = new ArrayList<>(colorViews.size());
            int width = colorViews.getFirst().getWidth(0);
            int height = colorViews.getFirst().getHeight(0);
            for (int i = 0; i < colorViews.size(); i++) {
                GpuTextureView view = colorViews.get(i);
                OptionalInt clear = colorClears.get(i);
                if (view == null) {
                    if (clear.isPresent()) throw new IllegalStateException("Unused color attachment " + i + " cannot be cleared");
                    colors.add(RhiColorAttachment.unused());
                    continue;
                }
                if (view.getWidth(0) != width || view.getHeight(0) != height) {
                    throw new IllegalStateException("MRT attachment dimensions differ at slot " + i
                            + ": expected=" + width + "x" + height
                            + " actual=" + view.getWidth(0) + "x" + view.getHeight(0));
                }
                colors.add(new RhiColorAttachment(view, clear));
            }
            return List.copyOf(colors);
        }

        private static void validateColorAttachmentIndex(int index) {
            if (index < 0 || index >= ColorTargetState.MAX_COLOR_TARGETS) {
                throw new IllegalArgumentException("color attachment index out of range: " + index);
            }
        }

        private void ensureColorSlot(int index) {
            while (colorViews.size() <= index) {
                colorViews.add(null);
                colorClears.add(OptionalInt.empty());
            }
        }
    }
}
