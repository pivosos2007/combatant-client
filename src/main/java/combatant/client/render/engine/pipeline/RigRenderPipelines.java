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

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import combatant.client.render.engine.rhi.pipeline.PipelineDomain;
import combatant.client.render.engine.vertex.CombatantVertexFormats;
import net.minecraft.resources.Identifier;

public final class RigRenderPipelines {
    private static final RenderPipeline.Snippet MESH_UNIFORMS = CombatantRenderPipelines.meshUniforms();
    private static final RenderPipeline.Snippet RIG_UNIFORMS = CombatantRenderPipelines.rigUniforms();


    private RigRenderPipelines() {
    }

    /**
     * Backend-neutral rigged entity geometry. Geometry/deformation stays identical between variants;
     * only the entity alpha/cull/depth policy changes. This avoids forcing cutout skin geometry through
     * a translucent pipeline and keeps no-depth-write fades separate from ordinary translucent layers.
     */
    @PipelineRegister
    public static final RenderPipeline RIG_ENTITY_CUTOUT = rigEntityPipeline(
            "rig_entity_cutout", shader("rig_entity_cutout.frag"), false, true, false
    );
    @PipelineRegister
    public static final RenderPipeline RIG_ENTITY_CUTOUT_CULL = rigEntityPipeline(
            "rig_entity_cutout_cull", shader("rig_entity_cutout.frag"), true, true, false
    );
    @PipelineRegister
    public static final RenderPipeline RIG_ENTITY_TRANSLUCENT = rigEntityPipeline(
            "rig_entity_translucent", shader("rig_entity_translucent.frag"), false, true, true
    );
    @PipelineRegister
    public static final RenderPipeline RIG_ENTITY_TRANSLUCENT_CULL = rigEntityPipeline(
            "rig_entity_translucent_cull", shader("rig_entity_translucent.frag"), true, true, true
    );
    @PipelineRegister
    public static final RenderPipeline RIG_ENTITY_TRANSLUCENT_NO_DEPTH_WRITE = rigEntityPipeline(
            "rig_entity_translucent_no_depth_write", shader("rig_entity_translucent.frag"), false, false, true
    );
    @PipelineRegister
    public static final RenderPipeline RIG_ENTITY_TRANSLUCENT_NO_DEPTH_WRITE_CULL = rigEntityPipeline(
            "rig_entity_translucent_no_depth_write_cull", shader("rig_entity_translucent.frag"), true, false, true
    );
    /** Rig-deformed, untextured procedural trail surfaces. Vertex deformation is shared with entity rigs. */
    @PipelineRegister
    public static final RenderPipeline RIG_TRAIL_CLASSIC_DEPTH = rigTrailPipeline(
            "rig_trail_classic_depth", shader("rig_trail_classic.frag"), true, BlendFunction.TRANSLUCENT
    );
    @PipelineRegister
    public static final RenderPipeline RIG_TRAIL_CLASSIC = rigTrailPipeline(
            "rig_trail_classic", shader("rig_trail_classic.frag"), false, BlendFunction.TRANSLUCENT
    );

    /** Shaderpack-off entity-translucent path for static imported PBR geometry. */
    @PipelineRegister
    public static final RenderPipeline ASSET_COMPATIBILITY_TRANSLUCENT_CULL = assetCompatibilityPipeline(
            "asset_compatibility_translucent_cull", true, true
    );
    @PipelineRegister
    public static final RenderPipeline ASSET_COMPATIBILITY_TRANSLUCENT_DOUBLE_SIDED = assetCompatibilityPipeline(
            "asset_compatibility_translucent_double_sided", false, true
    );
    /** Explicit overlay variant; never selected for ordinary ENTITY instances. */
    @PipelineRegister
    public static final RenderPipeline ASSET_COMPATIBILITY_TRANSLUCENT_NO_DEPTH_CULL = assetCompatibilityPipeline(
            "asset_compatibility_translucent_no_depth_cull", true, false
    );
    @PipelineRegister
    public static final RenderPipeline ASSET_COMPATIBILITY_TRANSLUCENT_NO_DEPTH_DOUBLE_SIDED = assetCompatibilityPipeline(
            "asset_compatibility_translucent_no_depth_double_sided", false, false
    );

    private static RenderPipeline assetCompatibilityPipeline(String path, boolean cull, boolean depthTest) {
        return new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
                .withUniform("AssetMaterial", UniformType.UNIFORM_BUFFER)
                .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/" + path))
                .withDomain(PipelineDomain.WORLD)
                .withVertexFormat(CombatantVertexFormats.ASSET_PBR_STATIC,
                        com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
                .withVertexShader(shader("asset_compat.vert"))
                .withFragmentShader(shader("asset_compat.frag"))
                .withSampler("u_BaseColor")
                .withSampler("u_MetallicRoughness")
                .withSampler("u_Normal")
                .withSampler("u_Occlusion")
                .withSampler("u_Emissive")
                .withDepthTestFunction(depthTest
                        ? DepthTestFunction.GEQUAL_DEPTH_TEST
                        : DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(depthTest)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withCull(cull)
                .build();
    }

    private static RenderPipeline rigTrailPipeline(String path, Identifier fragmentShader, boolean depthTest) {
        return rigTrailPipeline(path, fragmentShader, depthTest, BlendFunctions.ALPHA_ADDITIVE);
    }

    private static RenderPipeline rigTrailPipeline(String path, Identifier fragmentShader, boolean depthTest, BlendFunction blend) {
        return new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, RIG_UNIFORMS)
                .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/" + path))
                .withDomain(PipelineDomain.WORLD)
                .withVertexFormat(CombatantVertexFormats.RIG_POSITION_TEXTURE_NORMAL_COLOR_BONES_DEFORM,
                        com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
                .withVertexShader(shader("rig_textured.vert"))
                .withFragmentShader(fragmentShader)
                .withDepthTestFunction(depthTest ? DepthTestFunction.GEQUAL_DEPTH_TEST : DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withBlend(blend)
                .withCull(false)
                .build();
    }

    private static RenderPipeline rigEntityPipeline(String path, Identifier fragmentShader, boolean cull,
                                                    boolean depthWrite, boolean translucent) {
        ExtendedRenderPipelineBuilder builder = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, RIG_UNIFORMS)
                .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/" + path))
                .withDomain(PipelineDomain.WORLD)
                .withVertexFormat(CombatantVertexFormats.RIG_POSITION_TEXTURE_NORMAL_COLOR_BONES_DEFORM,
                        com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
                .withVertexShader(shader("rig_textured.vert"))
                .withFragmentShader(fragmentShader)
                .withSampler("u_Texture")
                .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
                .withDepthWrite(depthWrite)
                .withCull(cull);
        if (translucent) builder.withBlend(BlendFunction.TRANSLUCENT);
        return builder.build();
    }

    private static Identifier shader(String path) {
        return Identifier.fromNamespaceAndPath("combatant", "shaders/" + path);
    }
}
