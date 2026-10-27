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
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import combatant.client.render.engine.rhi.pipeline.PipelineDomain;
import combatant.client.render.engine.vertex.CombatantVertexFormats;
import net.minecraft.resources.Identifier;

public final class WorldRenderPipelines {
    private static final float COPLANAR_SURFACE_DEPTH_BIAS_SLOPE = 0.0f;
    private static final float COPLANAR_SURFACE_DEPTH_BIAS_CONSTANT = 1.0f;
    private static final float COPLANAR_DECAL_DEPTH_BIAS_SLOPE = 1.0f;
    private static final float COPLANAR_DECAL_DEPTH_BIAS_CONSTANT = 1.0f;

    private static final RenderPipeline.Snippet MESH_UNIFORMS = CombatantRenderPipelines.meshUniforms();


    private WorldRenderPipelines() {
    }

    /**
     * No depth test; translucent; triangles.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_COLORED = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_colored"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_color.vert"))
            .withFragmentShader(shader("pos_color.frag"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();

    /**
     * Smooth procedural membrane for WorldParticles bubbles. Geometry owns depth; the shader only
     * performs a tiny radial deformation and surface shading, so depth stays tied to the visible shell.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_BUBBLE_SURFACE = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_bubble_surface"))
            .withDomain(PipelineDomain.WORLD)
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("world_bubble_surface.vert"))
            .withFragmentShader(shader("world_bubble_surface.frag"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build();

    /** Procedural additive glow sprite (no texture); drop-in for textured additive billboards. */
    @PipelineRegister
    public static final RenderPipeline WORLD_GLOW_SPRITE = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_glow_sprite"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_tex_color.vert"))
            .withFragmentShader(shader("world_glow_sprite.frag"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(false)
            .build();

    /** Glow sprite against the pre-translucent depth attachment. */
    @PipelineRegister
    public static final RenderPipeline WORLD_GLOW_SPRITE_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_glow_sprite_depth"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_tex_color.vert"))
            .withFragmentShader(shader("world_glow_sprite.frag"))
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(false)
            .build();

    /** Procedural additive ground ring (no texture); drop-in for textured jump circles. */
    @PipelineRegister
    public static final RenderPipeline WORLD_GLOW_RING = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_glow_ring"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_tex_color.vert"))
            .withFragmentShader(shader("world_glow_ring.frag"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(false)
            .build();

    /** Glow ring against the pre-translucent depth attachment. */
    @PipelineRegister
    public static final RenderPipeline WORLD_GLOW_RING_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_glow_ring_depth"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_tex_color.vert"))
            .withFragmentShader(shader("world_glow_ring.frag"))
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(false)
            .build();

    /**
     * Animated ESP surface (pixel-width rim, climbing scan band, fresnel + sheen). Same vertex payload
     * as the bubble surface: UV0 = face uv, Params = normal + vertical fade, Params2 = time/phase/scan/rim.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_GLOW_SURFACE = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_glow_surface"))
            .withDomain(PipelineDomain.WORLD)
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("world_glow_surface.vert"))
            .withFragmentShader(shader("world_glow_surface.frag"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();

    /** Glow surface against the pre-translucent world depth attachment. */
    @PipelineRegister
    public static final RenderPipeline WORLD_GLOW_SURFACE_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_glow_surface_depth"))
            .withDomain(PipelineDomain.WORLD)
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("world_glow_surface.vert"))
            .withFragmentShader(shader("world_glow_surface.frag"))
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();

    /** Same bubble membrane against the pre-translucent world depth attachment. */
    @PipelineRegister
    public static final RenderPipeline WORLD_BUBBLE_SURFACE_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_bubble_surface_depth"))
            .withDomain(PipelineDomain.WORLD)
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("world_bubble_surface.vert"))
            .withFragmentShader(shader("world_bubble_surface.frag"))
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build();

    /** Analytic world-surface shell overlay used by HitEffect wave and TotemFX world-contact outlines. */
    @PipelineRegister
    public static final RenderPipeline WORLD_SURFACE_SHELL = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_surface_shell"))
            .withDomain(PipelineDomain.WORLD)
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("world_surface_shell.vert"))
            .withFragmentShader(shader("world_surface_shell.frag"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();

    /** Depth-tested world-surface shell overlay. Depth bias keeps the analytic shell coplanar-safe. */
    @PipelineRegister
    public static final RenderPipeline WORLD_SURFACE_SHELL_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_surface_shell_depth"))
            .withDomain(PipelineDomain.WORLD)
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("world_surface_shell.vert"))
            .withFragmentShader(shader("world_surface_shell.frag"))
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(COPLANAR_SURFACE_DEPTH_BIAS_SLOPE, COPLANAR_SURFACE_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();

    // ======================
    // World (3D)
    // ======================
    /**
     * No depth test; translucent; lines.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_COLORED_LINES = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLineSmooth()
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_colored_lines"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.DEBUG_LINES)
            .withVertexShader(shader("pos_color.vert"))
            .withFragmentShader(shader("pos_color.frag"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    /**
     * Screen-space wide line expansion for WORLD_COLORED_LINES. Uses triangles; never depends on glLineWidth.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_WIDE_COLORED_LINES = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_wide_colored_lines"))
            .withVertexFormat(CombatantVertexFormats.POS3_COLOR_LINE, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("wide_line.vert"))
            .withFragmentShader(shader("pos_color.frag"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();

    /**
     * Depth test (GEQUAL); translucent; triangles.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_COLORED_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_colored_depth"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_color.vert"))
            .withFragmentShader(shader("pos_color.frag"))
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();

    /** Depth-tested colored geometry that is intentionally coplanar with scene surfaces. */
    @PipelineRegister
    public static final RenderPipeline WORLD_COLORED_COPLANAR_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_colored_coplanar_depth"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_color.vert"))
            .withFragmentShader(shader("pos_color.frag"))
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(COPLANAR_SURFACE_DEPTH_BIAS_SLOPE, COPLANAR_SURFACE_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    /**
     * No depth test; translucent; textured triangles.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_TEXTURED = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_textured"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_tex_color.vert"))
            .withFragmentShader(shader("pos_tex_color.frag"))
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build();

    @PipelineRegister
    public static final RenderPipeline WORLD_TEXTURED_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_textured_depth"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_tex_color.vert"))
            .withFragmentShader(shader("pos_tex_color.frag"))
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    /**
     * No depth test; premultiplied-alpha textured triangles. Used for GuiItemAtlas output.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_TEXTURED_PREMULTIPLIED_ALPHA = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_textured_premultiplied_alpha"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_tex_color.vert"))
            .withFragmentShader(shader("pos_tex_color.frag"))
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
            .withCull(true)
            .build();
    /**
     * Depth test (GEQUAL); additive; colored triangles.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_COLORED_ADDITIVE_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_colored_additive_depth"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_color.vert"))
            .withFragmentShader(shader("pos_color.frag"))
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(false)
            .build();
    /**
     * No depth test; translucent; world decal quads with SDF fill/stroke.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_DECAL_SDF = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_decal_sdf"))
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_tex_color_params2.vert"))
            .withFragmentShader(shader("world_decal_sdf.frag"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    /**
     * Dedicated aiming decal. Kept separate from generic impact decals so the aiming marker can evolve
     * independently without changing projectile impact rendering.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_AIM_DECAL = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_aim_decal"))
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_tex_color_params2.vert"))
            .withFragmentShader(shader("world_aim_decal.frag"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();

    /** Aiming decal tested against the pre-translucent scene depth. */
    @PipelineRegister
    public static final RenderPipeline WORLD_AIM_DECAL_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_aim_decal_depth"))
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_tex_color_params2.vert"))
            .withFragmentShader(shader("world_aim_decal.frag"))
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(COPLANAR_DECAL_DEPTH_BIAS_SLOPE, COPLANAR_DECAL_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();

    /**
     * No depth test; camera-facing world UI rounded fills and single-pass soft shadows.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_BILLBOARD_SDF = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_billboard_sdf"))
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_tex_color_params2.vert"))
            .withFragmentShader(shader("world_billboard_sdf.frag"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    /**
     * Depth test (GEQUAL); translucent; world decal quads with SDF fill/stroke.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_DECAL_SDF_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_decal_sdf_depth"))
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_tex_color_params2.vert"))
            .withFragmentShader(shader("world_decal_sdf.frag"))
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(COPLANAR_DECAL_DEPTH_BIAS_SLOPE, COPLANAR_DECAL_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    /**
     * No depth test; translucent; world text quads.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_TEXT = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_text"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("text.vert"))
            .withFragmentShader(shader("text.frag"))
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    /**
     * Depth test (GEQUAL); translucent; world text quads.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_TEXT_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_text_depth"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("text.vert"))
            .withFragmentShader(shader("text.frag"))
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    /**
     * No depth test; translucent; world MSDF text quads.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_TEXT_MSDF = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_text_msdf"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("text.vert"))
            .withFragmentShader(shader("text_msdf.frag"))
            .withSampler("u_Texture")
            .withUniform("MsdfText", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    /**
     * Depth test (GEQUAL); translucent; world MSDF text quads.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_TEXT_MSDF_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_text_msdf_depth"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("text.vert"))
            .withFragmentShader(shader("text_msdf.frag"))
            .withSampler("u_Texture")
            .withUniform("MsdfText", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    /**
     * No depth test; additive; textured triangles.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_TEXTURED_ADDITIVE = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_textured_additive"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_tex_color.vert"))
            .withFragmentShader(shader("pos_tex_color.frag"))
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(false)
            .build();
    /**
     * No depth test; additive; textured triangles with shader-side tint.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_TEXTURED_TINT_ADDITIVE = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_textured_tint_additive"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_tex_color.vert"))
            .withFragmentShader(shader("pos_tex_color_tint.frag"))
            .withSampler("u_Texture")
            .withUniform("TextureTint", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(false)
            .build();
    /**
     * Depth test (GEQUAL); additive; textured triangles.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_TEXTURED_ADDITIVE_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_textured_additive_depth"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_tex_color.vert"))
            .withFragmentShader(shader("pos_tex_color.frag"))
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(false)
            .build();

    @PipelineRegister
    public static final RenderPipeline WORLD_POST_DISTORTION_MASK = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_post_distortion_mask"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_tex_color.vert"))
            .withFragmentShader(shader("world_post_distortion_mask.frag"))
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
            .withCull(false)
            .build();

    @PipelineRegister
    public static final RenderPipeline WORLD_POST_DISTORTION_MASK_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_post_distortion_mask_depth"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_tex_color.vert"))
            .withFragmentShader(shader("world_post_distortion_mask.frag"))
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
            .withCull(false)
            .build();

    @PipelineRegister
    public static final RenderPipeline WORLD_POST_DISTORTION_MASK_SAMPLED_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_post_distortion_mask_sampled_depth"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_tex_color.vert"))
            .withFragmentShader(shader("world_post_distortion_mask_sampled_depth.frag"))
            .withSampler("u_Texture")
            .withSampler("u_Depth")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
            .withCull(false)
            .build();

    @PipelineRegister
    public static final RenderPipeline WORLD_SPHERE_DISTORTION_MASK = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_sphere_distortion_mask"))
            .withDomain(PipelineDomain.WORLD)
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("world_totem_burst.vert"))
            .withFragmentShader(shader("world_sphere_distortion_mask.frag"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
            .withCull(true)
            .build();

    @PipelineRegister
    public static final RenderPipeline WORLD_SPHERE_DISTORTION_MASK_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_sphere_distortion_mask_depth"))
            .withDomain(PipelineDomain.WORLD)
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("world_totem_burst.vert"))
            .withFragmentShader(shader("world_sphere_distortion_mask.frag"))
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
            .withCull(true)
            .build();

    @PipelineRegister
    public static final RenderPipeline WORLD_SPHERE_DISTORTION_MASK_SAMPLED_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_sphere_distortion_mask_sampled_depth"))
            .withDomain(PipelineDomain.WORLD)
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("world_totem_burst.vert"))
            .withFragmentShader(shader("world_sphere_distortion_mask_sampled_depth.frag"))
            .withSampler("u_Depth")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
            .withCull(true)
            .build();

    @PipelineRegister
    public static final RenderPipeline WORLD_TOTEM_BURST = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_totem_burst"))
            .withDomain(PipelineDomain.WORLD)
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("world_totem_burst.vert"))
            .withFragmentShader(shader("world_totem_burst.frag"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(true)
            .build();

    @PipelineRegister
    public static final RenderPipeline WORLD_TOTEM_BURST_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_totem_burst_depth"))
            .withDomain(PipelineDomain.WORLD)
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("world_totem_burst.vert"))
            .withFragmentShader(shader("world_totem_burst.frag"))
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(true)
            .build();

    @PipelineRegister
    public static final RenderPipeline WORLD_TARGET_ORBIT_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_target_orbit_depth"))
            .withDomain(PipelineDomain.WORLD)
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("world_totem_burst.vert"))
            .withFragmentShader(shader("world_target_orbit.frag"))
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(false)
            .build();

    @PipelineRegister
    public static final RenderPipeline WORLD_TARGET_RING_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_target_ring_depth"))
            .withDomain(PipelineDomain.WORLD)
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("world_totem_burst.vert"))
            .withFragmentShader(shader("world_target_ring.frag"))
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(false)
            .build();

    @PipelineRegister
    public static final RenderPipeline WORLD_TARGET_GHOST_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_target_ghost_depth"))
            .withDomain(PipelineDomain.WORLD)
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("world_totem_burst.vert"))
            .withFragmentShader(shader("world_target_ghost.frag"))
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(false)
            .build();

    /**
     * Depth test (GEQUAL); translucent; lines.
     */

    @PipelineRegister
    public static final RenderPipeline WORLD_TARGET_CRYSTAL = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_target_crystal"))
            .withDomain(PipelineDomain.WORLD)
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("world_totem_burst.vert"))
            .withFragmentShader(shader("world_target_crystal.frag"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA)
            .withCull(false)
            .build();

    @PipelineRegister
    public static final RenderPipeline WORLD_TARGET_CRYSTAL_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_target_crystal_depth"))
            .withDomain(PipelineDomain.WORLD)
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("world_totem_burst.vert"))
            .withFragmentShader(shader("world_target_crystal.frag"))
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA)
            .withCull(false)
            .build();

    @PipelineRegister
    public static final RenderPipeline WORLD_COLORED_LINES_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLineSmooth()
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_colored_lines_depth"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.DEBUG_LINES)
            .withVertexShader(shader("pos_color.vert"))
            .withFragmentShader(shader("pos_color.frag"))
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    /**
     * Screen-space wide line expansion for WORLD_COLORED_LINES_DEPTH. Uses triangles; never depends on glLineWidth.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_WIDE_COLORED_LINES_DEPTH = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_wide_colored_lines_depth"))
            .withVertexFormat(CombatantVertexFormats.POS3_COLOR_LINE, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("wide_line.vert"))
            .withFragmentShader(shader("pos_color.frag"))
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();

    /**
     * Depth test (GEQUAL); translucent; lines; lets liquids blend over (no depth write).
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_COLORED_LINES_LIQUID_BLEND = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLineSmooth()
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_colored_lines_liquid_blend"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.DEBUG_LINES)
            .withVertexShader(shader("pos_color.vert"))
            .withFragmentShader(shader("pos_color.frag"))
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    /**
     * Screen-space wide line expansion for WORLD_COLORED_LINES_LIQUID_BLEND. Uses triangles; never depends on glLineWidth.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_WIDE_COLORED_LINES_LIQUID_BLEND = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_wide_colored_lines_liquid_blend"))
            .withVertexFormat(CombatantVertexFormats.POS3_COLOR_LINE, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("wide_line.vert"))
            .withFragmentShader(shader("pos_color.frag"))
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();

    /**
     * Depth test (GEQUAL); translucent; triangles; intended for above-liquids pass (no depth write).
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_COLORED_LIQUID_IGNORE = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_colored_liquid_ignore"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_color.vert"))
            .withFragmentShader(shader("pos_color.frag"))
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    /**
     * Depth test (GEQUAL); translucent; lines; writes depth so liquids render below (above-liquids).
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_COLORED_LINES_LIQUID_IGNORE = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLineSmooth()
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_colored_lines_liquid_ignore"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.DEBUG_LINES)
            .withVertexShader(shader("pos_color.vert"))
            .withFragmentShader(shader("pos_color.frag"))
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    /**
     * Screen-space wide line expansion for WORLD_COLORED_LINES_LIQUID_IGNORE. Uses triangles; never depends on glLineWidth.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_WIDE_COLORED_LINES_LIQUID_IGNORE = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_wide_colored_lines_liquid_ignore"))
            .withVertexFormat(CombatantVertexFormats.POS3_COLOR_LINE, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("wide_line.vert"))
            .withFragmentShader(shader("pos_color.frag"))
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();

    /**
     * Depth test (GEQUAL); additive; textured triangles; intended for pre-water depth.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_TEXTURED_ADDITIVE_LIQUID_IGNORE = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_textured_additive_liquid_ignore"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_tex_color.vert"))
            .withFragmentShader(shader("pos_tex_color.frag"))
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(false)
            .build();
    /**
     * Depth test (GEQUAL); additive; textured triangles with shader-side tint; intended for pre-water depth.
     */
    @PipelineRegister
    public static final RenderPipeline WORLD_TEXTURED_TINT_ADDITIVE_LIQUID_IGNORE = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_textured_tint_additive_liquid_ignore"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos_tex_color.vert"))
            .withFragmentShader(shader("pos_tex_color_tint.frag"))
            .withSampler("u_Texture")
            .withUniform("TextureTint", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(false)
            .build();

    private static Identifier shader(String path) {
        return Identifier.fromNamespaceAndPath("combatant", "shaders/" + path);
    }
}
