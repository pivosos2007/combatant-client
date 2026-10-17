/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.iris.geometry;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.pipeline.DepthTestFunction;
import combatant.client.render.engine.pipeline.ExtendedRenderPipelineBuilder;
import combatant.client.render.engine.rhi.pipeline.PipelineDomain;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

/** Generic Iris submission pipelines. Shaderpack programs are selected through the Iris API. */
public final class IrisImportedGeometryPipelines {
    public static final RenderPipeline GBUFFER_CULL = pipeline("iris_imported_geometry_cull", true, false);
    public static final RenderPipeline GBUFFER_DOUBLE_SIDED = pipeline("iris_imported_geometry_double_sided", false, false);
    public static final RenderPipeline GBUFFER_NO_DEPTH_CULL = gbufferNoDepth("iris_imported_geometry_no_depth_cull", true);
    public static final RenderPipeline GBUFFER_NO_DEPTH_DOUBLE_SIDED = gbufferNoDepth("iris_imported_geometry_no_depth_double_sided", false);
    public static final RenderPipeline SHADOW_CULL = pipeline("iris_imported_shadow_cull", true, true);
    public static final RenderPipeline SHADOW_DOUBLE_SIDED = pipeline("iris_imported_shadow_double_sided", false, true);
    public static final RenderPipeline TRANSLUCENT_BLEND_CULL = translucent(
            "iris_imported_translucent_blend_cull", true, true, false);
    public static final RenderPipeline TRANSLUCENT_BLEND_DOUBLE_SIDED = translucent(
            "iris_imported_translucent_blend_double_sided", false, true, false);
    public static final RenderPipeline TRANSLUCENT_NO_DEPTH_CULL = translucent(
            "iris_imported_translucent_no_depth_cull", true, false, false);
    public static final RenderPipeline TRANSLUCENT_NO_DEPTH_DOUBLE_SIDED = translucent(
            "iris_imported_translucent_no_depth_double_sided", false, false, false);

    private IrisImportedGeometryPipelines() {}

    public static boolean owns(RenderPipeline pipeline) {
        return pipeline == GBUFFER_CULL || pipeline == GBUFFER_DOUBLE_SIDED
                || pipeline == GBUFFER_NO_DEPTH_CULL || pipeline == GBUFFER_NO_DEPTH_DOUBLE_SIDED
                || pipeline == SHADOW_CULL || pipeline == SHADOW_DOUBLE_SIDED
                || pipeline == TRANSLUCENT_BLEND_CULL
                || pipeline == TRANSLUCENT_BLEND_DOUBLE_SIDED
                || pipeline == TRANSLUCENT_NO_DEPTH_CULL
                || pipeline == TRANSLUCENT_NO_DEPTH_DOUBLE_SIDED;
    }

    private static RenderPipeline pipeline(String path, boolean cull, boolean shadow) {
        RenderPipeline pipeline = entityBuilder()
                .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/" + path))
                .withDomain(PipelineDomain.WORLD)
                .withVertexFormat(IrisVertexFormats.ENTITY, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
                .withVertexShader(Identifier.withDefaultNamespace("core/entity"))
                .withFragmentShader(Identifier.withDefaultNamespace("core/entity"))
                .withDepthTestFunction(shadow
                        ? DepthTestFunction.LEQUAL_DEPTH_TEST
                        : DepthTestFunction.GEQUAL_DEPTH_TEST)
                .withDepthWrite(true)
                .withCull(cull)
                .build();
        return CombatantRenderPipelines.registerAddonPipeline(pipeline);
    }


    private static RenderPipeline gbufferNoDepth(String path, boolean cull) {
        RenderPipeline pipeline = entityBuilder()
                .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/" + path))
                .withDomain(PipelineDomain.WORLD)
                .withVertexFormat(IrisVertexFormats.ENTITY, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
                .withVertexShader(Identifier.withDefaultNamespace("core/entity"))
                .withFragmentShader(Identifier.withDefaultNamespace("core/entity"))
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(cull)
                .build();
        return CombatantRenderPipelines.registerAddonPipeline(pipeline);
    }

    private static RenderPipeline translucent(String path, boolean cull,
                                              boolean depthTest, boolean depthWrite) {
        RenderPipeline pipeline = entityBuilder()
                .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/" + path))
                .withDomain(PipelineDomain.WORLD)
                .withVertexFormat(IrisVertexFormats.ENTITY, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
                .withVertexShader(Identifier.withDefaultNamespace("core/entity"))
                .withFragmentShader(Identifier.withDefaultNamespace("core/entity"))
                .withDepthTestFunction(depthTest
                        ? DepthTestFunction.GEQUAL_DEPTH_TEST
                        : DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(depthWrite)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withCull(cull)
                .build();
        return CombatantRenderPipelines.registerAddonPipeline(pipeline);
    }

    private static ExtendedRenderPipelineBuilder entityBuilder() {
        return new ExtendedRenderPipelineBuilder()
                .withExternalBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withExternalBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withExternalBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withExternalBindGroupLayout(BindGroupLayouts.FOG)
                .withExternalBindGroupLayout(BindGroupLayouts.LIGHTING)
                .withExternalBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1_SAMPLER2)
                .withExternalSampler("Sampler0")
                .withExternalSampler("Sampler1")
                .withExternalSampler("Sampler2");
    }
}
