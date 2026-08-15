/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on, adapted from, or implemented
 * with reference to Meteor Client
 * (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Licensed under the GNU General Public License v3.0.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.render.engine.pipeline;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import combatant.client.mixininterface.IRenderPipeline;
import combatant.client.render.engine.rhi.clip.ShapeClipRenderPassContract;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Small delegating builder so we can tag RenderPipeline with additional features.
 */
public class ExtendedRenderPipelineBuilder {
    private final RenderPipeline.Builder delegate;
    private final List<String> samplers = new ArrayList<>();
    private final List<UniformSpec> uniforms = new ArrayList<>();
    private boolean lineSmooth;
    private ShapeClipRenderPassContract shapeClipContract = ShapeClipRenderPassContract.NONE;
    private DepthTestFunction depthTestFunction;
    private net.minecraft.resources.Identifier location;
    private boolean depthWrite = true;
    private boolean customDepthState;
    private boolean bindGroupLayoutApplied;
    private RenderPipelineContract contract = RenderPipelineContract.EXTENDED;

    public ExtendedRenderPipelineBuilder(RenderPipeline.Snippet... snippets) {
        this.delegate = RenderPipeline.builder(snippets);
    }

    public ExtendedRenderPipelineBuilder withLocation(net.minecraft.resources.Identifier id) {
        this.location = id;
        delegate.withLocation(id);
        return this;
    }

    public ExtendedRenderPipelineBuilder withVertexFormat(VertexFormat format, com.mojang.blaze3d.PrimitiveTopology mode) {
        delegate.withVertexBinding(0, format);
        delegate.withPrimitiveTopology(mode);
        return this;
    }

    public ExtendedRenderPipelineBuilder withVertexShader(net.minecraft.resources.Identifier id) {
        delegate.withVertexShader(id);
        return this;
    }

    public ExtendedRenderPipelineBuilder withFragmentShader(net.minecraft.resources.Identifier id) {
        delegate.withFragmentShader(id);
        return this;
    }

    public ExtendedRenderPipelineBuilder withDepthTestFunction(DepthTestFunction fn) {
        this.depthTestFunction = fn;
        this.customDepthState = true;
        applyDepthStencilState();
        return this;
    }

    public ExtendedRenderPipelineBuilder withDepthWrite(boolean write) {
        this.depthWrite = write;
        this.customDepthState = true;
        applyDepthStencilState();
        return this;
    }

    public ExtendedRenderPipelineBuilder withBlend(BlendFunction blend) {
        delegate.withColorTargetState(new ColorTargetState(Optional.of(blend), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL));
        return this;
    }

    public ExtendedRenderPipelineBuilder withCull(boolean cull) {
        delegate.withCull(isUiCanvasPipeline() ? false : cull);
        return this;
    }

    public ExtendedRenderPipelineBuilder withSampler(String name) {
        samplers.add(name);
        return this;
    }

    public ExtendedRenderPipelineBuilder withUniform(String name, UniformType type) {
        uniforms.add(new UniformSpec(name, type));
        return this;
    }

    public ExtendedRenderPipelineBuilder withContract(RenderPipelineContract contract) {
        this.contract = contract == null ? RenderPipelineContract.EXTENDED : contract;
        return this;
    }

    public ExtendedRenderPipelineBuilder withLineSmooth() {
        this.lineSmooth = true;
        return this;
    }

    /**
     * Declares that this pipeline participates in shape clipping. The backend decides how the
     * attachment is represented. GL uses stencil; Vulkan must create/route the equivalent
     * render-pass/mask attachment explicitly.
     */
    public ExtendedRenderPipelineBuilder withShapeClipSupport() {
        this.shapeClipContract = ShapeClipRenderPassContract.WHEN_ACTIVE;
        return this;
    }

    public ExtendedRenderPipelineBuilder withShapeClipContract(ShapeClipRenderPassContract contract) {
        this.shapeClipContract = contract == null ? ShapeClipRenderPassContract.NONE : contract;
        return this;
    }

    public RenderPipeline build() {
        applyBindGroupLayout();
        RenderPipeline pipeline = delegate.build();
        IRenderPipeline combatantPipeline = (IRenderPipeline) pipeline;
        combatantPipeline.combatant$setLineSmooth(lineSmooth);
        combatantPipeline.combatant$setShapeClipContract(shapeClipContract);
        combatantPipeline.combatant$setContract(contract);
        return pipeline;
    }

    public RenderPipeline.Snippet buildSnippet() {
        applyBindGroupLayout();
        return delegate.buildSnippet();
    }

    private boolean isUiCanvasPipeline() {
        if (location == null || !"combatant".equals(location.getNamespace())) return false;
        String path = location.getPath();
        return path.startsWith("pipeline/ui_")
                || path.startsWith("pipeline/menu_")
                || path.equals("pipeline/gui_texture_lookup");
    }

    private void applyDepthStencilState() {
        if (!customDepthState) return;
        if (depthTestFunction == null || depthTestFunction.compareOp() == null) {
            delegate.withDepthStencilState(Optional.empty());
            return;
        }
        delegate.withDepthStencilState(new DepthStencilState(depthTestFunction.compareOp(), depthWrite));
    }

    private void applyBindGroupLayout() {
        if (bindGroupLayoutApplied || samplers.isEmpty() && uniforms.isEmpty()) {
            return;
        }
        BindGroupLayout.Builder layout = BindGroupLayout.builder();
        for (String sampler : samplers) {
            layout.withSampler(sampler);
        }
        for (UniformSpec uniform : uniforms) {
            layout.withUniform(uniform.name(), uniform.type());
        }
        delegate.withBindGroupLayout(layout.build());
        bindGroupLayoutApplied = true;
    }

    private record UniformSpec(String name, UniformType type) {
    }
}
