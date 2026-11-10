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

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import combatant.client.render.engine.vertex.CombatantVertexFormats;
import net.minecraft.resources.Identifier;

public final class PostProcessRenderPipelines {
    private static final RenderPipeline.Snippet MESH_UNIFORMS = CombatantRenderPipelines.meshUniforms();
    private static final String UI_BLUR_BLOCK = CombatantRenderPipelines.uiBlurBlock();


    private PostProcessRenderPipelines() {
    }

    @PipelineRegister
    public static final RenderPipeline UI_BLUR = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_blur"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_COLOR, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("pos2_tex_color.vert"))
            .withFragmentShader(shader("ui_blur.frag"))
            .withSampler("u_Texture")
            .withUniform(UI_BLUR_BLOCK, UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    @PipelineRegister
    public static final RenderPipeline DAMAGE_TINT = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/damage_tint"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("damage_tint.frag"))
            .withSampler("u_Texture")
            .withUniform("DamageTint", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    /**
     * Short full-screen kill impulse blur (pos2).
     */
    @PipelineRegister
    public static final RenderPipeline KILL_BLUR = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/kill_blur"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("kill_blur.frag"))
            .withSampler("u_Texture")
            .withUniform("PostProcess", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    /**
     * Full-screen texture copy fallback used when the backend has no fast blit path.
     */
    @PipelineRegister
    public static final RenderPipeline POSTPROCESS_COPY = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/postprocess_copy"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("postprocess_copy.frag"))
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withoutBlend()
            .withCull(false)
            .build();

    @PipelineRegister
    public static final RenderPipeline POSTPROCESS_COPY_HDR = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/postprocess_copy_hdr"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("postprocess_copy.frag"))
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withoutBlend()
            .withColorTarget(0, GpuFormat.RGBA16_FLOAT)
            .withCull(false)
            .build();
    /** Local screen refraction driven by the depth-tested world mask target. */
    @PipelineRegister
    public static final RenderPipeline POSTPROCESS_WORLD_MASK_DISTORTION = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/postprocess_world_mask_distortion"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("world_mask_distortion.frag"))
            .withSampler("u_Texture")
            .withSampler("u_Mask")
            .withUniform("WorldMaskedPost", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();

    @PipelineRegister
    public static final RenderPipeline POSTPROCESS_JUMP_SHOCKWAVE = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/postprocess_jump_shockwave"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("jump_shockwave.frag"))
            .withSampler("u_Texture")
            .withSampler("u_Depth")
            .withUniform("JumpShockwave", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withoutBlend()
            .withCull(false)
            .build();

    /** Format-matched HDR variant for the Iris colortex0 pre-translucent integration. */
    @PipelineRegister
    public static final RenderPipeline POSTPROCESS_JUMP_SHOCKWAVE_HDR = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/postprocess_jump_shockwave_hdr"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("jump_shockwave.frag"))
            .withSampler("u_Texture")
            .withSampler("u_Depth")
            .withUniform("JumpShockwave", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withoutBlend()
            .withColorTarget(0, GpuFormat.RGBA16_FLOAT)
            .withCull(false)
            .build();

    @PipelineRegister
    public static final RenderPipeline POSTPROCESS_BLAST_SHOCKWAVE = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/postprocess_blast_shockwave"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("blast_shockwave.frag"))
            .withSampler("u_Texture")
            .withSampler("u_Depth")
            .withUniform("JumpShockwave", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withoutBlend()
            .withCull(false)
            .build();

    /** Format-matched HDR variant for the Iris colortex0 pre-translucent integration. */
    @PipelineRegister
    public static final RenderPipeline POSTPROCESS_BLAST_SHOCKWAVE_HDR = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/postprocess_blast_shockwave_hdr"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("blast_shockwave.frag"))
            .withSampler("u_Texture")
            .withSampler("u_Depth")
            .withUniform("JumpShockwave", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withoutBlend()
            .withColorTarget(0, GpuFormat.RGBA16_FLOAT)
            .withCull(false)
            .build();

    @PipelineRegister
    public static final RenderPipeline POSTPROCESS_ORBIT_GRAVITY = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/postprocess_orbit_gravity"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("orbit_gravity.frag"))
            .withSampler("u_Texture")
            .withSampler("u_Depth")
            .withUniform("JumpShockwave", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withoutBlend()
            .withCull(false)
            .build();
    /** Full-screen cover-fit texture used by the rewritten main menu. */
    /** Stationary procedural cloud panorama used by isolated visual-preview scenes. */
    @PipelineRegister
    public static final RenderPipeline VISUAL_PREVIEW_CLOUDS = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/visual_preview_clouds"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("visual_preview_clouds.frag"))
            .withUniform("VisualPreviewBackground", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    @PipelineRegister
    public static final RenderPipeline POST_FX = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/post_fx"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("post_fx.frag"))
            .withSampler("u_Texture")
            .withSampler("u_Lut")
            .withUniform("PostFX", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();

    @PipelineRegister
    public static final RenderPipeline DEPTH_OF_FIELD_FOCUS = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/depth_of_field_focus"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("depth_of_field_focus.frag"))
            .withSampler("u_MainDepth").withSampler("u_TranslucentDepth").withSampler("u_ItemEntityDepth")
            .withSampler("u_ParticlesDepth").withSampler("u_WeatherDepth").withSampler("u_CloudsDepth")
            .withUniform("DepthOfField", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT).withCull(false).build();

    @PipelineRegister
    public static final RenderPipeline DEPTH_OF_FIELD = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/depth_of_field"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("depth_of_field.frag"))
            .withSampler("u_Texture").withSampler("u_FocusTexture").withSampler("u_MainDepth")
            .withSampler("u_TranslucentDepth").withSampler("u_ItemEntityDepth").withSampler("u_ParticlesDepth")
            .withSampler("u_WeatherDepth").withSampler("u_CloudsDepth")
            .withUniform("DepthOfField", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT).withCull(false).build();

    private static Identifier shader(String path) {
        return Identifier.fromNamespaceAndPath("combatant", "shaders/" + path);
    }
}
