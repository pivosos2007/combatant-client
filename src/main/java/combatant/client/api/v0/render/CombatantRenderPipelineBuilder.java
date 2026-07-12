/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.api.v0.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.pipeline.DepthTestFunction;
import combatant.client.render.engine.pipeline.ExtendedRenderPipelineBuilder;
import combatant.client.render.engine.rhi.clip.ShapeClipRenderPassContract;
import net.minecraft.resources.Identifier;

@SuppressWarnings("unused")
public final class CombatantRenderPipelineBuilder {
    private final ExtendedRenderPipelineBuilder delegate;

    private CombatantRenderPipelineBuilder(RenderPipeline.Snippet... snippets) {
        this.delegate = new ExtendedRenderPipelineBuilder(snippets);
    }

    public static CombatantRenderPipelineBuilder create(RenderPipeline.Snippet... snippets) {
        return new CombatantRenderPipelineBuilder(snippets);
    }

    public static RenderPipeline.Snippet meshUniforms() {
        return CombatantRenderPipelines.meshUniforms();
    }

    public CombatantRenderPipelineBuilder location(Identifier id) {
        delegate.withLocation(id);
        return this;
    }

    public CombatantRenderPipelineBuilder vertexFormat(VertexFormat format, PrimitiveTopology mode) {
        delegate.withVertexFormat(format, mode);
        return this;
    }

    public CombatantRenderPipelineBuilder vertexShader(Identifier id) {
        delegate.withVertexShader(id);
        return this;
    }

    public CombatantRenderPipelineBuilder fragmentShader(Identifier id) {
        delegate.withFragmentShader(id);
        return this;
    }

    public CombatantRenderPipelineBuilder depthTest(DepthTestFunction function) {
        delegate.withDepthTestFunction(function);
        return this;
    }

    public CombatantRenderPipelineBuilder depthWrite(boolean write) {
        delegate.withDepthWrite(write);
        return this;
    }

    public CombatantRenderPipelineBuilder blend(BlendFunction blend) {
        delegate.withBlend(blend);
        return this;
    }

    public CombatantRenderPipelineBuilder cull(boolean cull) {
        delegate.withCull(cull);
        return this;
    }

    public CombatantRenderPipelineBuilder sampler(String name) {
        delegate.withSampler(name);
        return this;
    }

    public CombatantRenderPipelineBuilder uniform(String name, UniformType type) {
        delegate.withUniform(name, type);
        return this;
    }

    public CombatantRenderPipelineBuilder lineSmooth() {
        delegate.withLineSmooth();
        return this;
    }

    public CombatantRenderPipelineBuilder shapeClipSupport() {
        delegate.withShapeClipSupport();
        return this;
    }

    public CombatantRenderPipelineBuilder shapeClipContract(ShapeClipRenderPassContract contract) {
        delegate.withShapeClipContract(contract);
        return this;
    }

    public RenderPipeline build() {
        return delegate.build();
    }

    public RenderPipeline buildAndRegister() {
        return CombatantRenderPipelines.registerAddonPipeline(build());
    }
}
