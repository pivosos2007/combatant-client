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

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
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
    /**
     * Static replacement-player rig: at most two bone influences and no procedural deformation.
     * Uses the regular rig vertex ABI so the shared persistent mesh cache needs no copies.
     */
    @PipelineRegister
    public static final RenderPipeline PLAYER_RIG_FAST_CUTOUT = playerRigPipeline(
            "player_rig_fast_cutout", shader("rig_player_lit_cutout.frag"), false);
    @PipelineRegister
    public static final RenderPipeline PLAYER_RIG_FAST_TRANSLUCENT = playerRigPipeline(
            "player_rig_fast_translucent", shader("rig_player_lit_translucent.frag"), true);

    /** HDR equivalents for explicitly allocated RGBA16F targets; never override Iris MRT. */
    @PipelineRegister
    public static final RenderPipeline PLAYER_RIG_FAST_CUTOUT_HDR = playerRigPipeline(
            "player_rig_fast_cutout_hdr", shader("rig_player_lit_cutout.frag"), false, GpuFormat.RGBA16_FLOAT);
    @PipelineRegister
    public static final RenderPipeline PLAYER_RIG_FAST_TRANSLUCENT_HDR = playerRigPipeline(
            "player_rig_fast_translucent_hdr", shader("rig_player_lit_translucent.frag"), true, GpuFormat.RGBA16_FLOAT);

    @PipelineRegister
    public static final RenderPipeline RIG_ENTITY_CUTOUT_HDR = rigEntityPipeline(
            "rig_entity_cutout_hdr", shader("rig_entity_cutout.frag"), false, true, false, GpuFormat.RGBA16_FLOAT);
    @PipelineRegister
    public static final RenderPipeline RIG_ENTITY_CUTOUT_CULL_HDR = rigEntityPipeline(
            "rig_entity_cutout_cull_hdr", shader("rig_entity_cutout.frag"), true, true, false, GpuFormat.RGBA16_FLOAT);
    @PipelineRegister
    public static final RenderPipeline RIG_ENTITY_TRANSLUCENT_HDR = rigEntityPipeline(
            "rig_entity_translucent_hdr", shader("rig_entity_translucent.frag"), false, true, true, GpuFormat.RGBA16_FLOAT);
    @PipelineRegister
    public static final RenderPipeline RIG_ENTITY_TRANSLUCENT_CULL_HDR = rigEntityPipeline(
            "rig_entity_translucent_cull_hdr", shader("rig_entity_translucent.frag"), true, true, true, GpuFormat.RGBA16_FLOAT);
    @PipelineRegister
    public static final RenderPipeline RIG_ENTITY_TRANSLUCENT_NO_DEPTH_WRITE_HDR = rigEntityPipeline(
            "rig_entity_translucent_no_depth_write_hdr", shader("rig_entity_translucent.frag"), false, false, true, GpuFormat.RGBA16_FLOAT);
    @PipelineRegister
    public static final RenderPipeline RIG_ENTITY_TRANSLUCENT_NO_DEPTH_WRITE_CULL_HDR = rigEntityPipeline(
            "rig_entity_translucent_no_depth_write_cull_hdr", shader("rig_entity_translucent.frag"), true, false, true, GpuFormat.RGBA16_FLOAT);

    /** The deferred player pass supplies its full camera view * model pose explicitly. */
    public static boolean isPlayerGpuPipeline(RenderPipeline pipeline) {
        return pipeline == PLAYER_RIG_FAST_CUTOUT
                || pipeline == PLAYER_RIG_FAST_TRANSLUCENT
                || pipeline == PLAYER_RIG_FAST_CUTOUT_HDR
                || pipeline == PLAYER_RIG_FAST_TRANSLUCENT_HDR;
    }

    /** Choose an exact attachment-compatible RHI pipeline; other formats are not silently coerced. */
    public static RenderPipeline playerPipeline(boolean translucent, GpuFormat format) {
        if (format == GpuFormat.RGBA8_UNORM) {
            return translucent ? PLAYER_RIG_FAST_TRANSLUCENT : PLAYER_RIG_FAST_CUTOUT;
        }
        if (format == GpuFormat.RGBA16_FLOAT) {
            return translucent ? PLAYER_RIG_FAST_TRANSLUCENT_HDR : PLAYER_RIG_FAST_CUTOUT_HDR;
        }
        return null;
    }

    public static RenderPipeline entityPipeline(boolean cull, boolean depthWrite,
                                                boolean translucent, GpuFormat format) {
        if (format != GpuFormat.RGBA8_UNORM && format != GpuFormat.RGBA16_FLOAT) return null;
        boolean hdr = format == GpuFormat.RGBA16_FLOAT;
        if (!translucent) return hdr ? (cull ? RIG_ENTITY_CUTOUT_CULL_HDR : RIG_ENTITY_CUTOUT_HDR)
                                    : (cull ? RIG_ENTITY_CUTOUT_CULL : RIG_ENTITY_CUTOUT);
        if (!depthWrite) return hdr
                ? (cull ? RIG_ENTITY_TRANSLUCENT_NO_DEPTH_WRITE_CULL_HDR : RIG_ENTITY_TRANSLUCENT_NO_DEPTH_WRITE_HDR)
                : (cull ? RIG_ENTITY_TRANSLUCENT_NO_DEPTH_WRITE_CULL : RIG_ENTITY_TRANSLUCENT_NO_DEPTH_WRITE);
        return hdr ? (cull ? RIG_ENTITY_TRANSLUCENT_CULL_HDR : RIG_ENTITY_TRANSLUCENT_HDR)
                   : (cull ? RIG_ENTITY_TRANSLUCENT_CULL : RIG_ENTITY_TRANSLUCENT);
    }

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

    /** Fully opaque/masked static geometry, safe to reorder and instance with depth writes. */
    @PipelineRegister
    public static final RenderPipeline ASSET_COMPATIBILITY_OPAQUE_CULL = assetCompatibilityOpaquePipeline(
            "asset_compatibility_opaque_cull", true, true, false);
    @PipelineRegister
    public static final RenderPipeline ASSET_COMPATIBILITY_OPAQUE_DOUBLE_SIDED = assetCompatibilityOpaquePipeline(
            "asset_compatibility_opaque_double_sided", false, true, false);
    @PipelineRegister
    public static final RenderPipeline ASSET_COMPATIBILITY_OPAQUE_NO_DEPTH_CULL = assetCompatibilityOpaquePipeline(
            "asset_compatibility_opaque_no_depth_cull", true, false, false);
    @PipelineRegister
    public static final RenderPipeline ASSET_COMPATIBILITY_OPAQUE_NO_DEPTH_DOUBLE_SIDED = assetCompatibilityOpaquePipeline(
            "asset_compatibility_opaque_no_depth_double_sided", false, false, false);

    /** Same material/depth state but the vertex shader fetches gl_InstanceID from AssetInstances. */
    @PipelineRegister
    public static final RenderPipeline ASSET_COMPATIBILITY_INSTANCED_CULL = assetCompatibilityOpaquePipeline(
            "asset_compatibility_instanced_cull", true, true, true);
    @PipelineRegister
    public static final RenderPipeline ASSET_COMPATIBILITY_INSTANCED_DOUBLE_SIDED = assetCompatibilityOpaquePipeline(
            "asset_compatibility_instanced_double_sided", false, true, true);
    @PipelineRegister
    public static final RenderPipeline ASSET_COMPATIBILITY_INSTANCED_NO_DEPTH_CULL = assetCompatibilityOpaquePipeline(
            "asset_compatibility_instanced_no_depth_cull", true, false, true);
    @PipelineRegister
    public static final RenderPipeline ASSET_COMPATIBILITY_INSTANCED_NO_DEPTH_DOUBLE_SIDED = assetCompatibilityOpaquePipeline(
            "asset_compatibility_instanced_no_depth_double_sided", false, false, true);

    public static RenderPipeline assetOpaquePipeline(boolean cull, boolean depth, boolean instanced) {
        if (depth) return instanced
                ? (cull ? ASSET_COMPATIBILITY_INSTANCED_CULL : ASSET_COMPATIBILITY_INSTANCED_DOUBLE_SIDED)
                : (cull ? ASSET_COMPATIBILITY_OPAQUE_CULL : ASSET_COMPATIBILITY_OPAQUE_DOUBLE_SIDED);
        return instanced
                ? (cull ? ASSET_COMPATIBILITY_INSTANCED_NO_DEPTH_CULL : ASSET_COMPATIBILITY_INSTANCED_NO_DEPTH_DOUBLE_SIDED)
                : (cull ? ASSET_COMPATIBILITY_OPAQUE_NO_DEPTH_CULL : ASSET_COMPATIBILITY_OPAQUE_NO_DEPTH_DOUBLE_SIDED);
    }

    private static RenderPipeline assetCompatibilityOpaquePipeline(String path, boolean cull,
                                                                    boolean depthTest, boolean instanced) {
        ExtendedRenderPipelineBuilder builder = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
                .withUniform("AssetMaterial", UniformType.UNIFORM_BUFFER)
                .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/" + path))
                .withDomain(PipelineDomain.WORLD)
                .withVertexFormat(CombatantVertexFormats.ASSET_PBR_STATIC,
                        com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
                .withVertexShader(shader(instanced ? "asset_compat_instanced.vert" : "asset_compat.vert"))
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
                .withCull(cull);
        if (instanced) builder.withUniform("AssetInstances", UniformType.UNIFORM_BUFFER);
        return builder.build();
    }

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

    private static RenderPipeline playerRigPipeline(String path, Identifier fragmentShader, boolean translucent) {
        return playerRigPipeline(path, fragmentShader, translucent, GpuFormat.RGBA8_UNORM);
    }

    private static RenderPipeline playerRigPipeline(String path, Identifier fragmentShader,
                                                    boolean translucent, GpuFormat colorFormat) {
        ExtendedRenderPipelineBuilder builder = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
                .withUniform("RigBones", UniformType.UNIFORM_BUFFER)
                .withUniform("RigPlayerLighting", UniformType.UNIFORM_BUFFER)
                .withUniform("Lighting", UniformType.UNIFORM_BUFFER)
                .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/" + path))
                .withDomain(PipelineDomain.WORLD)
                .withVertexFormat(CombatantVertexFormats.RIG_POSITION_TEXTURE_NORMAL_COLOR_BONES_DEFORM,
                        com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
                .withVertexShader(shader("rig_player_fast.vert"))
                .withFragmentShader(fragmentShader)
                .withSampler("u_Texture")
                .withSampler("u_Lightmap")
                .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
                .withDepthWrite(!translucent)
                .withCull(false);
        // RGBA16F stores scene-linear RGB; the texture/lightmap shader otherwise emits SDR-encoded color.
        if (colorFormat == GpuFormat.RGBA16_FLOAT) {
            builder.withShaderDefine("COMBATANT_RIG_SCENE_LINEAR");
        }
        // RenderPearl requires an explicit target state for opaque draws too.
        // Its format must match the actual pass attachment, including optional RGBA16F.
        builder.withColorTarget(0, colorFormat,
                translucent ? BlendFunction.TRANSLUCENT : null, ColorTargetState.WRITE_ALL);
        return builder.build();
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
        return rigEntityPipeline(path, fragmentShader, cull, depthWrite, translucent, GpuFormat.RGBA8_UNORM);
    }

    private static RenderPipeline rigEntityPipeline(String path, Identifier fragmentShader, boolean cull,
                                                    boolean depthWrite, boolean translucent, GpuFormat colorFormat) {
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
        if (colorFormat == GpuFormat.RGBA16_FLOAT) {
            builder.withShaderDefine("COMBATANT_RIG_SCENE_LINEAR");
        }
        // Opaque rig variants still require one color target; only the blend function is absent.
        builder.withColorTarget(0, colorFormat,
                translucent ? BlendFunction.TRANSLUCENT : null, ColorTargetState.WRITE_ALL);
        return builder.build();
    }

    private static Identifier shader(String path) {
        return Identifier.fromNamespaceAndPath("combatant", "shaders/" + path);
    }
}
