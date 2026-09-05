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

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import combatant.client.mixininterface.IRenderPipeline;
import combatant.client.render.engine.rhi.clip.ShapeClipRenderPassContract;
import combatant.client.util.resources.asset.AssetLoad;
import combatant.client.util.resources.asset.AssetLoadPhase;
import combatant.client.render.engine.rig.shader.RigRenderMode;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.render.engine.rhi.pipeline.RenderPipelineRegistry;
import combatant.client.render.engine.rhi.pipeline.PipelineDomain;
import combatant.client.render.engine.shader.CombatantShaderSources;
import combatant.client.render.engine.shader.ShaderCostRegistry;
import combatant.client.render.engine.vertex.CombatantVertexFormats;
import combatant.client.util.logging.DebugLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pipeline registry for Combatant's custom rendering engine.
 *
 * <p>Keep this class free of module-specific logic: it is the foundation for all custom 3D/2D effects.</p>
 */
public enum CombatantRenderPipelines {
    ;
    // WORLD depth-tested pipelines are overlays against already-populated scene depth.
    // Keep the bias to a constant depth-buffer unit only. A non-zero slope factor is
    // angle dependent and becomes an increasingly large world-space displacement with
    // distance, which makes occluded quads leak through terrain when the camera moves away.
    // Constant = 1 is enough to absorb the last raster/transform ULP without turning the
    // depth test into a distance-dependent polygon offset.
    private static final float WORLD_OVERLAY_DEPTH_BIAS_SLOPE = 0.0f;
    private static final float WORLD_OVERLAY_DEPTH_BIAS_CONSTANT = 1.0f;

    // Shaders (assets/combatant/shaders/*)
    public static final Identifier SHADER_POS_COLOR_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_color.vert");
    public static final Identifier SHADER_WIDE_LINE_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/wide_line.vert");
    public static final Identifier SHADER_POS_COLOR_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_color.frag");
    public static final Identifier SHADER_POS_COLOR_FOG_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_color_fog.vert");
    public static final Identifier SHADER_POS_COLOR_FOG_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_color_fog.frag");
    public static final Identifier SHADER_POS_COLOR_RECT_PARAMS_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_color_rect_params.vert");
    public static final Identifier SHADER_POS_COLOR_RECT_PARAMS2_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_color_rect_params2.vert");
    public static final Identifier SHADER_POS_LOCAL_COLOR_RECT_PARAMS_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_local_color_rect_params.vert");
    public static final Identifier SHADER_POS_LOCAL_COLOR_RECT_PARAMS2_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_local_color_rect_params2.vert");
    public static final Identifier SHADER_POS_LOCAL_COLOR_RECT_PARAMS5_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_local_color_rect_params5.vert");
    public static final Identifier SHADER_POS_LOCAL_COLOR_RECT_PARAMS6_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_local_color_rect_params6.vert");
    public static final Identifier SHADER_POS_LOCAL_COLOR_RECT_PARAMS5_GENERIC_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_local_color_rect_params5_generic.vert");
    public static final Identifier SHADER_UI_POS_COLOR_FAST_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_pos_color_fast.vert");
    public static final Identifier SHADER_UI_PATH_FAST_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_path_fast.vert");
    public static final Identifier SHADER_UI_PATH_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_path.frag");
    public static final Identifier SHADER_UI_PATH_ANALYTIC_CLIP_FRAG = CombatantShaderSources.analyticClipVariantId(SHADER_UI_PATH_FRAG);
    public static final Identifier SHADER_UI_WAVE_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_wave.frag");
    public static final Identifier SHADER_UI_WAVE_ANALYTIC_CLIP_FRAG = CombatantShaderSources.analyticClipVariantId(SHADER_UI_WAVE_FRAG);
    public static final Identifier SHADER_UI_POS_TEX_COLOR_FAST_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_pos_tex_color_fast.vert");
    public static final Identifier SHADER_UI_POS_COLOR_RECT_PARAMS_FAST_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_pos_color_rect_params_fast.vert");
    public static final Identifier SHADER_UI_POS_LOCAL_COLOR_RECT_PARAMS_FAST_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_pos_local_color_rect_params_fast.vert");
    public static final Identifier SHADER_UI_GEOMETRY_FAST_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_geometry_fast.vert");
    public static final Identifier SHADER_UI_GEOMETRY_PARAMS2_FAST_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_geometry_params2_fast.vert");
    public static final Identifier SHADER_UI_PRIMITIVE_FAST_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_primitive_fast.vert");
    public static final Identifier SHADER_UI_POS_TEX_COLOR_TRANSFORMED_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_pos_tex_color_transformed.vert");
    public static final Identifier SHADER_POS_TEX_COLOR_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_tex_color.vert");
    public static final Identifier SHADER_POS_TEX_COLOR_PARAMS2_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_tex_color_params2.vert");
    public static final Identifier SHADER_POS_TEX_COLOR_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_tex_color.frag");
    public static final Identifier SHADER_POS_TEX_COLOR_ANALYTIC_CLIP_FRAG = CombatantShaderSources.analyticClipVariantId(SHADER_POS_TEX_COLOR_FRAG);
    public static final Identifier SHADER_POS_TEX_COLOR_PREMULTIPLIED_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_tex_color_premultiplied.frag");
    public static final Identifier SHADER_POS_TEX_COLOR_PREMULTIPLIED_ANALYTIC_CLIP_FRAG = CombatantShaderSources.analyticClipVariantId(SHADER_POS_TEX_COLOR_PREMULTIPLIED_FRAG);
    public static final Identifier SHADER_RIG_TEXTURED_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/rig_textured.vert");
    public static final Identifier SHADER_RIG_TEXTURED_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/rig_textured.frag");
    public static final Identifier SHADER_RIG_ENTITY_CUTOUT_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/rig_entity_cutout.frag");
    public static final Identifier SHADER_RIG_ENTITY_TRANSLUCENT_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/rig_entity_translucent.frag");
    public static final Identifier SHADER_GUI_TEXTURE_LOOKUP_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/gui_texture_lookup.frag");
    public static final Identifier SHADER_POS_TEX_COLOR_TINT_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_tex_color_tint.frag");
    public static final Identifier SHADER_POS_TEX_COLOR_SKY_FOG_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_tex_color_sky_fog.vert");
    public static final Identifier SHADER_POS_TEX_COLOR_SKY_FOG_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_tex_color_sky_fog.frag");
    public static final Identifier SHADER_POS_TEX_COLOR_SKY_FOG_ADDITIVE_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_tex_color_sky_fog_additive.frag");
    public static final Identifier SHADER_POS_COLOR_SKYBOX_SHADER_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_color_skybox_shader.vert");
    public static final Identifier SHADER_REIMAGINED_SKYBOX_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/reimagined_skybox.frag");
    public static final Identifier SHADER_POS_TEX_COLOR_RECT_PARAMS_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_tex_color_rect_params.vert");
    public static final Identifier SHADER_POS_TEX_LOCAL_COLOR_RECT_PARAMS_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_tex_local_color_rect_params.vert");
    public static final Identifier SHADER_POS_TEX_LOCAL_COLOR_RECT_PARAMS2_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_tex_local_color_rect_params2.vert");
    public static final Identifier SHADER_POS_TEX_LOCAL_COLOR_RECT_PARAMS6_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_tex_local_color_rect_params6.vert");
    public static final Identifier SHADER_POS_TEX_COLOR_RECT_PARAMS2_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/pos_tex_color_rect_params2.vert");
    public static final Identifier SHADER_TEXT_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/text.vert");
    public static final Identifier SHADER_TEXT_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/text.frag");
    public static final Identifier SHADER_TEXT_ANALYTIC_CLIP_FRAG = CombatantShaderSources.analyticClipVariantId(SHADER_TEXT_FRAG);
    public static final Identifier SHADER_TEXT_MSDF_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/text_msdf.frag");
    public static final Identifier SHADER_TEXT_MSDF_ANALYTIC_CLIP_FRAG = CombatantShaderSources.analyticClipVariantId(SHADER_TEXT_MSDF_FRAG);
    public static final Identifier SHADER_SVG_MSDF_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/svg_msdf.frag");
    public static final Identifier SHADER_SVG_MSDF_ANALYTIC_CLIP_FRAG = CombatantShaderSources.analyticClipVariantId(SHADER_SVG_MSDF_FRAG);
    public static final Identifier SHADER_ORBIZ_RING_BATCH_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/orbiz_ring_batch.frag");
    public static final Identifier SHADER_WORLD_DECAL_SDF_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/world_decal_sdf.frag");
    public static final Identifier SHADER_WORLD_AIM_DECAL_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/world_aim_decal.frag");
    public static final Identifier SHADER_WORLD_BILLBOARD_SDF_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/world_billboard_sdf.frag");
    public static final Identifier SHADER_WORLD_BUBBLE_SURFACE_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/world_bubble_surface.vert");
    public static final Identifier SHADER_WORLD_BUBBLE_SURFACE_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/world_bubble_surface.frag");
    public static final Identifier SHADER_ROUNDED_RECT_BATCH_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/rounded_rect_batch.frag");
    public static final Identifier SHADER_UI_ROUNDED_FILL_SMOKE_BATCH_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_rounded_fill_smoke_batch.frag");
    public static final Identifier SHADER_UI_MODULE_CATEGORY_SURFACE_BATCH_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_module_category_surface_batch.frag");
    public static final Identifier SHADER_UI_WIDGET_SURFACE_BATCH_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_widget_surface_batch.frag");
    public static final Identifier SHADER_UI_MAIN_MENU_HONEYCOMB_BATCH_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_main_menu_honeycomb_batch.frag");
    public static final Identifier SHADER_ROUNDED_RECT_STROKE_ANGULAR_BATCH_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/rounded_rect_stroke_angular_batch.frag");
    public static final Identifier SHADER_UI_SHAPE_BATCH_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_shape_batch.frag");
    public static final Identifier SHADER_UI_SHAPE_BATCH_ANALYTIC_CLIP_FRAG = CombatantShaderSources.analyticClipVariantId(SHADER_UI_SHAPE_BATCH_FRAG);
    public static final Identifier SHADER_UI_PRIMITIVE_BATCH_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_primitive_batch.frag");
    public static final Identifier SHADER_ROUNDED_RECT_GLOW_BATCH_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/rounded_rect_glow_batch.frag");
    public static final Identifier SHADER_UI_GLOW_BATCH_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_glow_batch.frag");
    public static final Identifier SHADER_UI_TEXTURED_SHAPE_BATCH_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_textured_shape_batch.frag");
    public static final Identifier SHADER_UI_TEXTURED_SHAPE_BATCH_ANALYTIC_CLIP_FRAG = CombatantShaderSources.analyticClipVariantId(SHADER_UI_TEXTURED_SHAPE_BATCH_FRAG);
    public static final Identifier SHADER_UI_BLUR_BATCH_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_blur_batch.frag");
    public static final Identifier SHADER_UI_BLUR_BATCH_ANALYTIC_CLIP_FRAG = CombatantShaderSources.analyticClipVariantId(SHADER_UI_BLUR_BATCH_FRAG);
    public static final Identifier SHADER_UI_BLUR_BATCH_CORNERS_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_blur_batch_corners.frag");
    public static final Identifier SHADER_UI_BLUR_BATCH_CORNERS_ANALYTIC_CLIP_FRAG = CombatantShaderSources.analyticClipVariantId(SHADER_UI_BLUR_BATCH_CORNERS_FRAG);
    public static final Identifier SHADER_UI_LIQUID_GLASS_BATCH_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_liquid_glass_batch.frag");
    public static final Identifier SHADER_UI_LIQUID_GLASS_BATCH_ANALYTIC_CLIP_FRAG = CombatantShaderSources.analyticClipVariantId(SHADER_UI_LIQUID_GLASS_BATCH_FRAG);
    public static final Identifier SHADER_UI_LIQUID_GLASS_BATCH_UI_UNDERLAY_FRAG = CombatantShaderSources.uiUnderlayVariantId(SHADER_UI_LIQUID_GLASS_BATCH_FRAG);
    public static final Identifier SHADER_UI_LIQUID_GLASS_BATCH_UI_UNDERLAY_ANALYTIC_CLIP_FRAG = CombatantShaderSources.uiUnderlayVariantId(SHADER_UI_LIQUID_GLASS_BATCH_ANALYTIC_CLIP_FRAG);
    public static final Identifier SHADER_DAMAGE_TINT_VERT = Identifier.fromNamespaceAndPath("combatant", "shaders/damage_tint.vert");
    public static final Identifier SHADER_DAMAGE_TINT_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/damage_tint.frag");
    public static final Identifier SHADER_KILL_BLUR_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/kill_blur.frag");
    public static final Identifier SHADER_POSTPROCESS_COPY_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/postprocess_copy.frag");
    public static final Identifier SHADER_MAIN_MENU_TEXTURE_BACKGROUND_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/main_menu_texture_background.frag");
    public static final Identifier SHADER_MENU_BACKGROUND_AURORA_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/menu_background_aurora.frag");
    public static final Identifier SHADER_MENU_BACKGROUND_WAVES_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/menu_background_waves.frag");
    public static final Identifier SHADER_VISUAL_PREVIEW_CLOUDS_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/visual_preview_clouds.frag");
    public static final Identifier SHADER_POST_FX_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/post_fx.frag");
    public static final Identifier SHADER_MOTION_BLUR_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/motion_blur.frag");
    public static final Identifier SHADER_DEPTH_OF_FIELD_FOCUS_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/depth_of_field_focus.frag");
    public static final Identifier SHADER_DEPTH_OF_FIELD_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/depth_of_field.frag");
    public static final Identifier SHADER_HEAT_FX_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/heat_fx.frag");
    public static final Identifier SHADER_ESP_GRADIENT_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/shader_esp_gradient.frag");
    public static final Identifier SHADER_ESP_SHADOW_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/shader_esp_shadow.frag");
    public static final Identifier SHADER_ESP_SMOKE_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/shader_esp_smoke.frag");
    public static final Identifier SHADER_PORTAL_RIFT_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/portal_rift.frag");
    public static final Identifier SHADER_SLEEP_OVERLAY_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/sleep_overlay.frag");
    public static final Identifier SHADER_HAND_SMOKE_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/hand_smoke.frag");
    public static final Identifier SHADER_HAND_METALLIC_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/hand_metallic.frag");
    public static final Identifier SHADER_HAND_MASK_OCCUPANCY_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/hand_mask_occupancy.frag");
    public static final Identifier SHADER_HAND_MASK_OCCUPANCY_DILATE_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/hand_mask_occupancy_dilate.frag");
    public static final Identifier SHADER_HAND_GHOSTING_HISTORY_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/hand_ghosting_history.frag");
    public static final Identifier SHADER_HAND_GHOSTING_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/hand_ghosting.frag");
    public static final Identifier SHADER_UI_BLUR_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_blur.frag");
    public static final Identifier SHADER_HAND_GLASS_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/hand_glass.frag");
    public static final Identifier SHADER_SKY_SUN_FRAG = Identifier.fromNamespaceAndPath("combatant", "shaders/sky_sun.frag");
    private static final List<RenderPipeline> PIPELINES = new ArrayList<>();
    public static final RenderPipeline GUI_TEXTURE_LOOKUP = add(new ExtendedRenderPipelineBuilder(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/gui_texture_lookup"))
            .withFragmentShader(SHADER_GUI_TEXTURE_LOOKUP_FRAG)
            .build()
    );
    // Snippets
    private static final RenderPipeline.Snippet MESH_UNIFORMS = new ExtendedRenderPipelineBuilder()
            .withUniform("MeshData", UniformType.UNIFORM_BUFFER)
            .buildSnippet();
    private static final RenderPipeline.Snippet RIG_UNIFORMS = new ExtendedRenderPipelineBuilder()
            .withUniform("RigBones", UniformType.UNIFORM_BUFFER)
            .withUniform("RigDeform", UniformType.UNIFORM_BUFFER)
            .withUniform("RigRibbon", UniformType.UNIFORM_BUFFER)
            .buildSnippet();
    private static final RenderPipeline.Snippet UI_BATCH_UNIFORMS = new ExtendedRenderPipelineBuilder()
            .withUniform("UIBatch", UniformType.UNIFORM_BUFFER)
            .buildSnippet();
    private static final RenderPipeline.Snippet UI_ANALYTIC_CLIP_UNIFORMS = new ExtendedRenderPipelineBuilder()
            .withUniform("UIClip", UniformType.UNIFORM_BUFFER)
            .buildSnippet();
    private static final RenderPipeline.Snippet UI_BACKDROP_UNIFORMS = new ExtendedRenderPipelineBuilder()
            .withUniform("UIBackdrop", UniformType.UNIFORM_BUFFER)
            .buildSnippet();
    /**
     * Backend-neutral rigged entity geometry. Geometry/deformation stays identical between variants;
     * only the entity alpha/cull/depth policy changes. This avoids forcing cutout skin geometry through
     * a translucent pipeline and keeps no-depth-write fades separate from ordinary translucent layers.
     */
    public static final RenderPipeline RIG_ENTITY_CUTOUT = add(rigEntityPipeline(
            "rig_entity_cutout", SHADER_RIG_ENTITY_CUTOUT_FRAG, false, true, false
    ));
    public static final RenderPipeline RIG_ENTITY_CUTOUT_CULL = add(rigEntityPipeline(
            "rig_entity_cutout_cull", SHADER_RIG_ENTITY_CUTOUT_FRAG, true, true, false
    ));
    public static final RenderPipeline RIG_ENTITY_TRANSLUCENT = add(rigEntityPipeline(
            "rig_entity_translucent", SHADER_RIG_ENTITY_TRANSLUCENT_FRAG, false, true, true
    ));
    public static final RenderPipeline RIG_ENTITY_TRANSLUCENT_CULL = add(rigEntityPipeline(
            "rig_entity_translucent_cull", SHADER_RIG_ENTITY_TRANSLUCENT_FRAG, true, true, true
    ));
    public static final RenderPipeline RIG_ENTITY_TRANSLUCENT_NO_DEPTH_WRITE = add(rigEntityPipeline(
            "rig_entity_translucent_no_depth_write", SHADER_RIG_ENTITY_TRANSLUCENT_FRAG, false, false, true
    ));
    public static final RenderPipeline RIG_ENTITY_TRANSLUCENT_NO_DEPTH_WRITE_CULL = add(rigEntityPipeline(
            "rig_entity_translucent_no_depth_write_cull", SHADER_RIG_ENTITY_TRANSLUCENT_FRAG, true, false, true
    ));

    /**
     * Compatibility alias for callers written against the first rig pipeline.
     * New code should choose a {@link RigRenderMode} explicitly.
     */
    @Deprecated
    public static final RenderPipeline RIG_TEXTURED = RIG_ENTITY_TRANSLUCENT_CULL;

    /**
     * No depth test; translucent; triangles.
     */
    public static final RenderPipeline WORLD_COLORED = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_colored"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_COLOR_VERT)
            .withFragmentShader(SHADER_POS_COLOR_FRAG)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );

    /**
     * Smooth procedural membrane for WorldParticles bubbles. Geometry owns depth; the shader only
     * performs a tiny radial deformation and surface shading, so depth stays tied to the visible shell.
     */
    public static final RenderPipeline WORLD_BUBBLE_SURFACE = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_bubble_surface"))
            .withDomain(PipelineDomain.WORLD)
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_WORLD_BUBBLE_SURFACE_VERT)
            .withFragmentShader(SHADER_WORLD_BUBBLE_SURFACE_FRAG)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );

    /** Same bubble membrane against the pre-translucent world depth attachment. */
    public static final RenderPipeline WORLD_BUBBLE_SURFACE_DEPTH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_bubble_surface_depth"))
            .withDomain(PipelineDomain.WORLD)
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_WORLD_BUBBLE_SURFACE_VERT)
            .withFragmentShader(SHADER_WORLD_BUBBLE_SURFACE_FRAG)
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );

    // ======================
    // World (3D)
    // ======================
    /**
     * No depth test; translucent; lines.
     */
    public static final RenderPipeline WORLD_COLORED_LINES = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLineSmooth()
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_colored_lines"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, com.mojang.blaze3d.PrimitiveTopology.DEBUG_LINES)
            .withVertexShader(SHADER_POS_COLOR_VERT)
            .withFragmentShader(SHADER_POS_COLOR_FRAG)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Screen-space wide line expansion for WORLD_COLORED_LINES. Uses triangles; never depends on glLineWidth.
     */
    public static final RenderPipeline WORLD_WIDE_COLORED_LINES = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_wide_colored_lines"))
            .withVertexFormat(CombatantVertexFormats.POS3_COLOR_LINE, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_WIDE_LINE_VERT)
            .withFragmentShader(SHADER_POS_COLOR_FRAG)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );

    /**
     * Depth test (GEQUAL); translucent; triangles.
     */
    public static final RenderPipeline WORLD_COLORED_DEPTH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_colored_depth"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_COLOR_VERT)
            .withFragmentShader(SHADER_POS_COLOR_FRAG)
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(WORLD_OVERLAY_DEPTH_BIAS_SLOPE, WORLD_OVERLAY_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * No depth test; translucent; textured triangles.
     */
    public static final RenderPipeline WORLD_TEXTURED = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_textured"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_VERT)
            .withFragmentShader(SHADER_POS_TEX_COLOR_FRAG)
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * No depth test; premultiplied-alpha textured triangles. Used for GuiItemAtlas output.
     */
    public static final RenderPipeline WORLD_TEXTURED_PREMULTIPLIED_ALPHA = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_textured_premultiplied_alpha"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_VERT)
            .withFragmentShader(SHADER_POS_TEX_COLOR_FRAG)
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
            .withCull(false)
            .build()
    );
    /**
     * Depth test (GEQUAL); translucent; textured triangles.
     */
    public static final RenderPipeline WORLD_TEXTURED_DEPTH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_textured_depth"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_VERT)
            .withFragmentShader(SHADER_POS_TEX_COLOR_FRAG)
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(WORLD_OVERLAY_DEPTH_BIAS_SLOPE, WORLD_OVERLAY_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Depth test (GEQUAL); additive; colored triangles.
     */
    public static final RenderPipeline WORLD_COLORED_ADDITIVE_DEPTH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_colored_additive_depth"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_COLOR_VERT)
            .withFragmentShader(SHADER_POS_COLOR_FRAG)
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(WORLD_OVERLAY_DEPTH_BIAS_SLOPE, WORLD_OVERLAY_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(false)
            .build()
    );
    /**
     * No depth test; translucent; world decal quads with SDF fill/stroke.
     */
    public static final RenderPipeline WORLD_DECAL_SDF = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_decal_sdf"))
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_PARAMS2_VERT)
            .withFragmentShader(SHADER_WORLD_DECAL_SDF_FRAG)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Dedicated aiming decal. Kept separate from generic impact decals so the aiming marker can evolve
     * independently without changing projectile impact rendering.
     */
    public static final RenderPipeline WORLD_AIM_DECAL = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_aim_decal"))
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_PARAMS2_VERT)
            .withFragmentShader(SHADER_WORLD_AIM_DECAL_FRAG)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );

    /** Aiming decal tested against the pre-translucent scene depth. */
    public static final RenderPipeline WORLD_AIM_DECAL_DEPTH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_aim_decal_depth"))
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_PARAMS2_VERT)
            .withFragmentShader(SHADER_WORLD_AIM_DECAL_FRAG)
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(WORLD_OVERLAY_DEPTH_BIAS_SLOPE, WORLD_OVERLAY_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );

    /**
     * No depth test; camera-facing world UI rounded fills and single-pass soft shadows.
     */
    public static final RenderPipeline WORLD_BILLBOARD_SDF = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_billboard_sdf"))
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_PARAMS2_VERT)
            .withFragmentShader(SHADER_WORLD_BILLBOARD_SDF_FRAG)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Depth test (GEQUAL); translucent; world decal quads with SDF fill/stroke.
     */
    public static final RenderPipeline WORLD_DECAL_SDF_DEPTH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_decal_sdf_depth"))
            .withVertexFormat(CombatantVertexFormats.POS3_TEXTURE_COLOR_PARAMS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_PARAMS2_VERT)
            .withFragmentShader(SHADER_WORLD_DECAL_SDF_FRAG)
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(WORLD_OVERLAY_DEPTH_BIAS_SLOPE, WORLD_OVERLAY_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * No depth test; translucent; world text quads.
     */
    public static final RenderPipeline WORLD_TEXT = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_text"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_TEXT_VERT)
            .withFragmentShader(SHADER_TEXT_FRAG)
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Depth test (GEQUAL); translucent; world text quads.
     */
    public static final RenderPipeline WORLD_TEXT_DEPTH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_text_depth"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_TEXT_VERT)
            .withFragmentShader(SHADER_TEXT_FRAG)
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(WORLD_OVERLAY_DEPTH_BIAS_SLOPE, WORLD_OVERLAY_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * No depth test; translucent; world MSDF text quads.
     */
    public static final RenderPipeline WORLD_TEXT_MSDF = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_text_msdf"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_TEXT_VERT)
            .withFragmentShader(SHADER_TEXT_MSDF_FRAG)
            .withSampler("u_Texture")
            .withUniform("MsdfText", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Depth test (GEQUAL); translucent; world MSDF text quads.
     */
    public static final RenderPipeline WORLD_TEXT_MSDF_DEPTH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_text_msdf_depth"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_TEXT_VERT)
            .withFragmentShader(SHADER_TEXT_MSDF_FRAG)
            .withSampler("u_Texture")
            .withUniform("MsdfText", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(WORLD_OVERLAY_DEPTH_BIAS_SLOPE, WORLD_OVERLAY_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * No depth test; additive; textured triangles.
     */
    public static final RenderPipeline WORLD_TEXTURED_ADDITIVE = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_textured_additive"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_VERT)
            .withFragmentShader(SHADER_POS_TEX_COLOR_FRAG)
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(false)
            .build()
    );
    /**
     * No depth test; additive; textured triangles with shader-side tint.
     */
    public static final RenderPipeline WORLD_TEXTURED_TINT_ADDITIVE = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_textured_tint_additive"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_VERT)
            .withFragmentShader(SHADER_POS_TEX_COLOR_TINT_FRAG)
            .withSampler("u_Texture")
            .withUniform("TextureTint", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(false)
            .build()
    );
    /**
     * Depth test (GEQUAL); additive; textured triangles.
     */
    public static final RenderPipeline WORLD_TEXTURED_ADDITIVE_DEPTH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_textured_additive_depth"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_VERT)
            .withFragmentShader(SHADER_POS_TEX_COLOR_FRAG)
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(WORLD_OVERLAY_DEPTH_BIAS_SLOPE, WORLD_OVERLAY_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(false)
            .build()
    );
    /**
     * Depth test (GEQUAL); translucent; lines.
     */
    public static final RenderPipeline WORLD_COLORED_LINES_DEPTH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLineSmooth()
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_colored_lines_depth"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, com.mojang.blaze3d.PrimitiveTopology.DEBUG_LINES)
            .withVertexShader(SHADER_POS_COLOR_VERT)
            .withFragmentShader(SHADER_POS_COLOR_FRAG)
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(WORLD_OVERLAY_DEPTH_BIAS_SLOPE, WORLD_OVERLAY_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Screen-space wide line expansion for WORLD_COLORED_LINES_DEPTH. Uses triangles; never depends on glLineWidth.
     */
    public static final RenderPipeline WORLD_WIDE_COLORED_LINES_DEPTH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_wide_colored_lines_depth"))
            .withVertexFormat(CombatantVertexFormats.POS3_COLOR_LINE, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_WIDE_LINE_VERT)
            .withFragmentShader(SHADER_POS_COLOR_FRAG)
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(WORLD_OVERLAY_DEPTH_BIAS_SLOPE, WORLD_OVERLAY_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );

    /**
     * Depth test (GEQUAL); translucent; triangles; lets liquids blend over (no depth write).
     */
    public static final RenderPipeline WORLD_COLORED_LIQUID_BLEND = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_colored_liquid_blend"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_COLOR_VERT)
            .withFragmentShader(SHADER_POS_COLOR_FRAG)
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(WORLD_OVERLAY_DEPTH_BIAS_SLOPE, WORLD_OVERLAY_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Depth test (GEQUAL); translucent; lines; lets liquids blend over (no depth write).
     */
    public static final RenderPipeline WORLD_COLORED_LINES_LIQUID_BLEND = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLineSmooth()
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_colored_lines_liquid_blend"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, com.mojang.blaze3d.PrimitiveTopology.DEBUG_LINES)
            .withVertexShader(SHADER_POS_COLOR_VERT)
            .withFragmentShader(SHADER_POS_COLOR_FRAG)
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(WORLD_OVERLAY_DEPTH_BIAS_SLOPE, WORLD_OVERLAY_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Screen-space wide line expansion for WORLD_COLORED_LINES_LIQUID_BLEND. Uses triangles; never depends on glLineWidth.
     */
    public static final RenderPipeline WORLD_WIDE_COLORED_LINES_LIQUID_BLEND = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_wide_colored_lines_liquid_blend"))
            .withVertexFormat(CombatantVertexFormats.POS3_COLOR_LINE, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_WIDE_LINE_VERT)
            .withFragmentShader(SHADER_POS_COLOR_FRAG)
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(WORLD_OVERLAY_DEPTH_BIAS_SLOPE, WORLD_OVERLAY_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );

    /**
     * Depth test (GEQUAL); translucent; textured triangles; lets liquids blend over (no depth write).
     */
    public static final RenderPipeline WORLD_TEXTURED_LIQUID_BLEND = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_textured_liquid_blend"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_VERT)
            .withFragmentShader(SHADER_POS_TEX_COLOR_FRAG)
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(WORLD_OVERLAY_DEPTH_BIAS_SLOPE, WORLD_OVERLAY_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Depth test (GEQUAL); additive; textured triangles; lets liquids blend over (no depth write).
     */
    public static final RenderPipeline WORLD_TEXTURED_ADDITIVE_LIQUID_BLEND = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_textured_additive_liquid_blend"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_VERT)
            .withFragmentShader(SHADER_POS_TEX_COLOR_FRAG)
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(WORLD_OVERLAY_DEPTH_BIAS_SLOPE, WORLD_OVERLAY_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(false)
            .build()
    );
    /**
     * Depth test (GEQUAL); translucent; triangles; intended for above-liquids pass (no depth write).
     */
    public static final RenderPipeline WORLD_COLORED_LIQUID_IGNORE = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_colored_liquid_ignore"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_COLOR_VERT)
            .withFragmentShader(SHADER_POS_COLOR_FRAG)
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(WORLD_OVERLAY_DEPTH_BIAS_SLOPE, WORLD_OVERLAY_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Depth test (GEQUAL); translucent; lines; writes depth so liquids render below (above-liquids).
     */
    public static final RenderPipeline WORLD_COLORED_LINES_LIQUID_IGNORE = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLineSmooth()
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_colored_lines_liquid_ignore"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, com.mojang.blaze3d.PrimitiveTopology.DEBUG_LINES)
            .withVertexShader(SHADER_POS_COLOR_VERT)
            .withFragmentShader(SHADER_POS_COLOR_FRAG)
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(WORLD_OVERLAY_DEPTH_BIAS_SLOPE, WORLD_OVERLAY_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Screen-space wide line expansion for WORLD_COLORED_LINES_LIQUID_IGNORE. Uses triangles; never depends on glLineWidth.
     */
    public static final RenderPipeline WORLD_WIDE_COLORED_LINES_LIQUID_IGNORE = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_wide_colored_lines_liquid_ignore"))
            .withVertexFormat(CombatantVertexFormats.POS3_COLOR_LINE, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_WIDE_LINE_VERT)
            .withFragmentShader(SHADER_POS_COLOR_FRAG)
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(WORLD_OVERLAY_DEPTH_BIAS_SLOPE, WORLD_OVERLAY_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );

    /**
     * Depth test (GEQUAL); additive; textured triangles; intended for pre-water depth.
     */
    public static final RenderPipeline WORLD_TEXTURED_ADDITIVE_LIQUID_IGNORE = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_textured_additive_liquid_ignore"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_VERT)
            .withFragmentShader(SHADER_POS_TEX_COLOR_FRAG)
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(WORLD_OVERLAY_DEPTH_BIAS_SLOPE, WORLD_OVERLAY_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(false)
            .build()
    );
    /**
     * Depth test (GEQUAL); additive; textured triangles with shader-side tint; intended for pre-water depth.
     */
    public static final RenderPipeline WORLD_TEXTURED_TINT_ADDITIVE_LIQUID_IGNORE = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_textured_tint_additive_liquid_ignore"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_VERT)
            .withFragmentShader(SHADER_POS_TEX_COLOR_TINT_FRAG)
            .withSampler("u_Texture")
            .withUniform("TextureTint", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(WORLD_OVERLAY_DEPTH_BIAS_SLOPE, WORLD_OVERLAY_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(false)
            .build()
    );
    /**
     * Depth test (GEQUAL); translucent; textured triangles; intended for pre-water depth.
     */
    public static final RenderPipeline WORLD_TEXTURED_LIQUID_IGNORE = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_textured_liquid_ignore"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_VERT)
            .withFragmentShader(SHADER_POS_TEX_COLOR_FRAG)
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
            .withDepthBias(WORLD_OVERLAY_DEPTH_BIAS_SLOPE, WORLD_OVERLAY_DEPTH_BIAS_CONSTANT)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * UI triangles (pos2 + color).
     */
    public static final RenderPipeline UI_COLORED = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_colored"))
            .withVertexFormat(CombatantVertexFormats.POS2_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_COLOR_VERT)
            .withFragmentShader(SHADER_POS_COLOR_FRAG)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    /**
     * UI lines (pos2 + color).
     */
    public static final RenderPipeline UI_COLORED_LINES = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_colored_lines"))
            .withVertexFormat(CombatantVertexFormats.POS2_COLOR, com.mojang.blaze3d.PrimitiveTopology.DEBUG_LINES)
            .withVertexShader(SHADER_POS_COLOR_VERT)
            .withFragmentShader(SHADER_POS_COLOR_FRAG)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_COLORED_LINES_FAST = add(new ExtendedRenderPipelineBuilder(UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_colored_lines_fast"))
            .withVertexFormat(CombatantVertexFormats.POS2_COLOR, com.mojang.blaze3d.PrimitiveTopology.DEBUG_LINES)
            .withVertexShader(SHADER_UI_POS_COLOR_FAST_VERT)
            .withFragmentShader(SHADER_POS_COLOR_FRAG)
            .withContract(RenderPipelineContract.UI_FAST)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    /**
     * UI textured triangles (pos2 + tex + color).
     */
    public static final RenderPipeline UI_TEXTURED = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_textured"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_VERT)
            .withFragmentShader(SHADER_POS_TEX_COLOR_FRAG)
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_TEXTURED_ANALYTIC_CLIP = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS, UI_ANALYTIC_CLIP_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/analytic_clip/ui_textured"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_VERT)
            .withFragmentShader(SHADER_POS_TEX_COLOR_ANALYTIC_CLIP_FRAG)
            .withSampler("u_Texture")
            .withContract(RenderPipelineContract.UI_EXTENDED)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    /**
     * UI textured triangles for premultiplied-alpha sources such as GuiItemAtlas.
     */
    public static final RenderPipeline UI_TEXTURED_PREMULTIPLIED_ALPHA = add(new ExtendedRenderPipelineBuilder(UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_textured_premultiplied_alpha"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_UI_POS_TEX_COLOR_FAST_VERT)
            .withFragmentShader(SHADER_POS_TEX_COLOR_FRAG)
            .withSampler("u_Texture")
            .withContract(RenderPipelineContract.UI_FAST)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_TEXTURED_PREMULTIPLIED_ALPHA_ANALYTIC_CLIP = add(new ExtendedRenderPipelineBuilder(UI_BATCH_UNIFORMS, UI_ANALYTIC_CLIP_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/analytic_clip/ui_textured_premultiplied_alpha"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_UI_POS_TEX_COLOR_FAST_VERT)
            .withFragmentShader(SHADER_POS_TEX_COLOR_PREMULTIPLIED_ANALYTIC_CLIP_FRAG)
            .withSampler("u_Texture")
            .withContract(RenderPipelineContract.UI_FAST)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_TEXTURED_FAST = add(new ExtendedRenderPipelineBuilder(UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_textured_fast"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_UI_POS_TEX_COLOR_FAST_VERT)
            .withFragmentShader(SHADER_POS_TEX_COLOR_FRAG)
            .withSampler("u_Texture")
            .withContract(RenderPipelineContract.UI_FAST)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_TEXTURED_FAST_ANALYTIC_CLIP = add(new ExtendedRenderPipelineBuilder(UI_BATCH_UNIFORMS, UI_ANALYTIC_CLIP_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/analytic_clip/ui_textured_fast"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_UI_POS_TEX_COLOR_FAST_VERT)
            .withFragmentShader(SHADER_POS_TEX_COLOR_ANALYTIC_CLIP_FRAG)
            .withSampler("u_Texture")
            .withContract(RenderPipelineContract.UI_FAST)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    /**
     * UI MSDF SVG quads (pos2 + tex + color).
     */
    public static final RenderPipeline UI_SVG_MSDF = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_svg_msdf"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_VERT)
            .withFragmentShader(SHADER_SVG_MSDF_FRAG)
            .withSampler("u_Texture")
            .withUniform("MsdfText", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_SVG_MSDF_FAST = add(new ExtendedRenderPipelineBuilder(UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_svg_msdf_fast"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_UI_POS_TEX_COLOR_FAST_VERT)
            .withFragmentShader(SHADER_SVG_MSDF_FRAG)
            .withSampler("u_Texture")
            .withUniform("MsdfText", UniformType.UNIFORM_BUFFER)
            .withContract(RenderPipelineContract.UI_FAST)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_SVG_MSDF_FAST_ANALYTIC_CLIP = add(new ExtendedRenderPipelineBuilder(UI_BATCH_UNIFORMS, UI_ANALYTIC_CLIP_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/analytic_clip/ui_svg_msdf_fast"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_UI_POS_TEX_COLOR_FAST_VERT)
            .withFragmentShader(SHADER_SVG_MSDF_ANALYTIC_CLIP_FRAG)
            .withSampler("u_Texture")
            .withUniform("MsdfText", UniformType.UNIFORM_BUFFER)
            .withContract(RenderPipelineContract.UI_FAST)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    /**
     * UI textured triangles (pos2 + tex + color), additive blend.
     */
    public static final RenderPipeline UI_TEXTURED_ADDITIVE = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_textured_additive"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_VERT)
            .withFragmentShader(SHADER_POS_TEX_COLOR_FRAG)
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_TEXTURED_ADDITIVE_TRANSFORMED = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_textured_additive_transformed"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_UI_POS_TEX_COLOR_TRANSFORMED_VERT)
            .withFragmentShader(SHADER_POS_TEX_COLOR_FRAG)
            .withSampler("u_Texture")
            .withContract(RenderPipelineContract.UI_EXTENDED)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(true)
            .build()
    );
    // ======================
    // UI (2D)
    // ======================
    /**
     * UI text (pos2 + tex + color).
     */
    public static final RenderPipeline UI_TEXT = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_text"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_TEXT_VERT)
            .withFragmentShader(SHADER_TEXT_FRAG)
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_TEXT_ANALYTIC_CLIP = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS, UI_ANALYTIC_CLIP_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/analytic_clip/ui_text"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_TEXT_VERT)
            .withFragmentShader(SHADER_TEXT_ANALYTIC_CLIP_FRAG)
            .withSampler("u_Texture")
            .withContract(RenderPipelineContract.UI_EXTENDED)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    /** Stable screen-space UI text. UNSCALED/CUSTOM and transformed callers keep UI_TEXT. */
    public static final RenderPipeline UI_TEXT_FAST = add(new ExtendedRenderPipelineBuilder(UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_text_fast"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_UI_POS_TEX_COLOR_FAST_VERT)
            .withFragmentShader(SHADER_TEXT_FRAG)
            .withSampler("u_Texture")
            .withContract(RenderPipelineContract.UI_FAST)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_TEXT_FAST_ANALYTIC_CLIP = add(new ExtendedRenderPipelineBuilder(UI_BATCH_UNIFORMS, UI_ANALYTIC_CLIP_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/analytic_clip/ui_text_fast"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_UI_POS_TEX_COLOR_FAST_VERT)
            .withFragmentShader(SHADER_TEXT_ANALYTIC_CLIP_FRAG)
            .withSampler("u_Texture")
            .withContract(RenderPipelineContract.UI_FAST)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    /**
     * UI MSDF text (pos2 + tex + color).
     */
    public static final RenderPipeline UI_TEXT_MSDF = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_text_msdf"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_TEXT_VERT)
            .withFragmentShader(SHADER_TEXT_MSDF_FRAG)
            .withSampler("u_Texture")
            .withUniform("MsdfText", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_TEXT_MSDF_ANALYTIC_CLIP = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS, UI_ANALYTIC_CLIP_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/analytic_clip/ui_text_msdf"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_TEXT_VERT)
            .withFragmentShader(SHADER_TEXT_MSDF_ANALYTIC_CLIP_FRAG)
            .withSampler("u_Texture")
            .withUniform("MsdfText", UniformType.UNIFORM_BUFFER)
            .withContract(RenderPipelineContract.UI_EXTENDED)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_TEXT_MSDF_FAST = add(new ExtendedRenderPipelineBuilder(UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_text_msdf_fast"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_UI_POS_TEX_COLOR_FAST_VERT)
            .withFragmentShader(SHADER_TEXT_MSDF_FRAG)
            .withSampler("u_Texture")
            .withUniform("MsdfText", UniformType.UNIFORM_BUFFER)
            .withContract(RenderPipelineContract.UI_FAST)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_TEXT_MSDF_FAST_ANALYTIC_CLIP = add(new ExtendedRenderPipelineBuilder(UI_BATCH_UNIFORMS, UI_ANALYTIC_CLIP_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/analytic_clip/ui_text_msdf_fast"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_UI_POS_TEX_COLOR_FAST_VERT)
            .withFragmentShader(SHADER_TEXT_MSDF_ANALYTIC_CLIP_FRAG)
            .withSampler("u_Texture")
            .withUniform("MsdfText", UniformType.UNIFORM_BUFFER)
            .withContract(RenderPipelineContract.UI_FAST)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_BLUR = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_blur"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_VERT)
            .withFragmentShader(SHADER_UI_BLUR_FRAG)
            .withSampler("u_Texture")
            .withUniform("UIBlur", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Fullscreen postprocess (pos2).
     */
    public static final RenderPipeline DAMAGE_TINT = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/damage_tint"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_DAMAGE_TINT_FRAG)
            .withSampler("u_Texture")
            .withUniform("DamageTint", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Short full-screen kill impulse blur (pos2).
     */
    public static final RenderPipeline KILL_BLUR = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/kill_blur"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_KILL_BLUR_FRAG)
            .withSampler("u_Texture")
            .withUniform("PostProcess", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Full-screen texture copy fallback used when the backend has no fast blit path.
     */
    public static final RenderPipeline POSTPROCESS_COPY = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/postprocess_copy"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_POSTPROCESS_COPY_FRAG)
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /** Full-screen cover-fit texture used by the rewritten main menu. */
    public static final RenderPipeline MAIN_MENU_TEXTURE_BACKGROUND = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/main_menu_texture_background"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_MAIN_MENU_TEXTURE_BACKGROUND_FRAG)
            .withSampler("u_PreviousTexture")
            .withSampler("u_Texture")
            .withUniform("MenuTextureTransition", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /** Full-screen procedural aurora used by the Combatant/vanilla menu background replacement. */
    public static final RenderPipeline MAIN_MENU_AURORA_BACKGROUND = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/menu_background_aurora"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_MENU_BACKGROUND_AURORA_FRAG)
            .withUniform("MenuBackground", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /** Full-screen procedural waves used by the Combatant/vanilla menu background replacement. */
    public static final RenderPipeline MAIN_MENU_WAVES_BACKGROUND = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/menu_background_waves"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_MENU_BACKGROUND_WAVES_FRAG)
            .withUniform("MenuBackground", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /** Stationary procedural cloud panorama used by isolated visual-preview scenes. */
    public static final RenderPipeline VISUAL_PREVIEW_CLOUDS = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/visual_preview_clouds"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_VISUAL_PREVIEW_CLOUDS_FRAG)
            .withUniform("VisualPreviewBackground", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Fullscreen visual grading + LUT (pos2).
     */
    public static final RenderPipeline POST_FX = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/post_fx"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_POST_FX_FRAG)
            .withSampler("u_Texture")
            .withSampler("u_Lut")
            .withUniform("PostFX", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Fullscreen camera-only perceptual motion blur (pos2).
     */
    public static final RenderPipeline MOTION_BLUR = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/motion_blur"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_MOTION_BLUR_FRAG)
            .withSampler("u_Texture")
            .withSampler("u_PreviousColor")
            .withUniform("MotionBlur", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Resolves the frame-invariant five-sample center focus distance into one RGBA8 texel.
     */
    public static final RenderPipeline DEPTH_OF_FIELD_FOCUS = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/depth_of_field_focus"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_DEPTH_OF_FIELD_FOCUS_FRAG)
            .withSampler("u_MainDepth")
            .withSampler("u_TranslucentDepth")
            .withSampler("u_ItemEntityDepth")
            .withSampler("u_ParticlesDepth")
            .withSampler("u_WeatherDepth")
            .withSampler("u_CloudsDepth")
            .withUniform("DepthOfField", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Fullscreen scene-depth-aware far depth of field (pos2).
     */
    public static final RenderPipeline DEPTH_OF_FIELD = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/depth_of_field"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_DEPTH_OF_FIELD_FRAG)
            .withSampler("u_Texture")
            .withSampler("u_FocusTexture")
            .withSampler("u_MainDepth")
            .withSampler("u_TranslucentDepth")
            .withSampler("u_ItemEntityDepth")
            .withSampler("u_ParticlesDepth")
            .withSampler("u_WeatherDepth")
            .withSampler("u_CloudsDepth")
            .withUniform("DepthOfField", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Fullscreen heat distortion + vignette (pos2).
     */
    public static final RenderPipeline HEAT_FX = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/heat_fx"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_HEAT_FX_FRAG)
            .withSampler("u_Texture")
            .withUniform("Heat", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Fullscreen shader ESP gradient composite (pos2).
     */
    public static final RenderPipeline SHADER_ESP_GRADIENT = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/shader_esp_gradient"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_ESP_GRADIENT_FRAG)
            .withSampler("u_Texture")
            .withUniform("ShaderEspGradient", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Fullscreen shader ESP separable shadow blur (pos2).
     */
    public static final RenderPipeline SHADER_ESP_SHADOW = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/shader_esp_shadow"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_ESP_SHADOW_FRAG)
            .withSampler("u_Texture")
            .withSampler("u_Mask")
            .withUniform("ShaderEspBlur", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Fullscreen shader ESP procedural smoke fill (pos2).
     */
    public static final RenderPipeline SHADER_ESP_SMOKE = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/shader_esp_smoke"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_ESP_SMOKE_FRAG)
            .withSampler("u_Texture")
            .withUniform("ShaderEspSmoke", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * No depth test; additive; textured-color triangles with procedural sun shading.
     */
    public static final RenderPipeline WORLD_SKY_SUN = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_sky_sun"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_VERT)
            .withFragmentShader(SHADER_SKY_SUN_FRAG)
            .withUniform("SkySun", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(false)
            .build()
    );
    /**
     * Fullscreen nether portal rift (pos2).
     */
    public static final RenderPipeline PORTAL_RIFT = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/portal_rift"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_PORTAL_RIFT_FRAG)
            .withSampler("u_Texture")
            .withUniform("Heat", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Fullscreen sleep overlay (pos2).
     */
    public static final RenderPipeline SLEEP_OVERLAY = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/sleep_overlay"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_SLEEP_OVERLAY_FRAG)
            .withSampler("u_Texture")
            .withUniform("PostProcess", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * Fullscreen hand glass (pos2).
     */
    public static final RenderPipeline HAND_GLASS = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/hand_glass"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_HAND_GLASS_FRAG)
            .withSampler("u_Src")
            .withSampler("u_Mask")
            .withUniform("HandGlass", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    public static final RenderPipeline HAND_SMOKE = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/hand_smoke"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_HAND_SMOKE_FRAG)
            .withSampler("u_Src")
            .withSampler("u_Mask")
            .withUniform("HandSmoke", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    public static final RenderPipeline HAND_METALLIC = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/hand_metallic"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_HAND_METALLIC_FRAG)
            .withSampler("u_Src")
            .withSampler("u_Mask")
            .withSampler("u_Occupancy")
            .withUniform("HandMetallic", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    public static final RenderPipeline HAND_MASK_OCCUPANCY = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/hand_mask_occupancy"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_HAND_MASK_OCCUPANCY_FRAG)
            .withSampler("u_Mask")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build()
    );
    public static final RenderPipeline HAND_MASK_OCCUPANCY_DILATE = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/hand_mask_occupancy_dilate"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_HAND_MASK_OCCUPANCY_DILATE_FRAG)
            .withSampler("u_Mask")
            .withUniform("HandMetallic", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build()
    );
    public static final RenderPipeline HAND_GHOSTING_HISTORY = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/hand_ghosting_history"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_HAND_GHOSTING_HISTORY_FRAG)
            .withSampler("u_History")
            .withSampler("u_Mask")
            .withUniform("HandGhosting", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build()
    );
    public static final RenderPipeline HAND_GHOSTING = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/hand_ghosting"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_HAND_GHOSTING_FRAG)
            .withSampler("u_Src")
            .withSampler("u_History")
            .withUniform("HandGhosting", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build()
    );
    private static final RenderPipeline.Snippet FOG_UNIFORMS = new ExtendedRenderPipelineBuilder()
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            .buildSnippet();
    /**
     * No depth test; translucent; textured skybox with vanilla sky/fog blending.
     */
    public static final RenderPipeline WORLD_SKYBOX_TEXTURED_FOG = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, FOG_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_skybox_textured_fog"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_SKY_FOG_VERT)
            .withFragmentShader(SHADER_POS_TEX_COLOR_SKY_FOG_FRAG)
            .withSampler("u_Texture")
            .withUniform("SkyboxShader", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    /**
     * No depth test; additive; textured skybox with vanilla sky/fog blending.
     */
    public static final RenderPipeline WORLD_SKYBOX_TEXTURED_FOG_ADDITIVE = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, FOG_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_skybox_textured_fog_additive"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_COLOR_SKY_FOG_VERT)
            .withFragmentShader(SHADER_POS_TEX_COLOR_SKY_FOG_ADDITIVE_FRAG)
            .withSampler("u_Texture")
            .withUniform("SkyboxShader", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(false)
            .build()
    );
    /**
     * Fullscreen procedural Reimagined skybox with sky/fog blending.
     */
    public static final RenderPipeline WORLD_REIMAGINED_SKYBOX_SHADER = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, FOG_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_reimagined_skybox_shader"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_DAMAGE_TINT_VERT)
            .withFragmentShader(SHADER_REIMAGINED_SKYBOX_FRAG)
            .withUniform("SkyboxShader", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    public static final RenderPipeline UI_ORBIZ_RING_BATCH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_orbiz_ring_batch"))
            .withVertexFormat(CombatantVertexFormats.POS2_LOCAL_COLOR_RECT_PARAMS5, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_LOCAL_COLOR_RECT_PARAMS5_GENERIC_VERT)
            .withFragmentShader(SHADER_ORBIZ_RING_BATCH_FRAG)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_ROUNDED_BATCH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_rounded_batch"))
            .withVertexFormat(CombatantVertexFormats.POS2_LOCAL_COLOR_RECT_PARAMS, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_LOCAL_COLOR_RECT_PARAMS_VERT)
            .withFragmentShader(SHADER_ROUNDED_RECT_BATCH_FRAG)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_ROUNDED_FILL_SMOKE_BATCH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_rounded_fill_smoke_batch"))
            .withVertexFormat(CombatantVertexFormats.POS2_LOCAL_COLOR_RECT_PARAMS5, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_LOCAL_COLOR_RECT_PARAMS5_VERT)
            .withFragmentShader(SHADER_UI_ROUNDED_FILL_SMOKE_BATCH_FRAG)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_MODULE_CATEGORY_SURFACE_BATCH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_module_category_surface_batch"))
            .withVertexFormat(CombatantVertexFormats.POS2_LOCAL_COLOR_RECT_PARAMS5, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_LOCAL_COLOR_RECT_PARAMS5_VERT)
            .withFragmentShader(SHADER_UI_MODULE_CATEGORY_SURFACE_BATCH_FRAG)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_WIDGET_SURFACE_BATCH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_widget_surface_batch"))
            .withVertexFormat(CombatantVertexFormats.POS2_LOCAL_COLOR_RECT_PARAMS6, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_LOCAL_COLOR_RECT_PARAMS6_VERT)
            .withFragmentShader(SHADER_UI_WIDGET_SURFACE_BATCH_FRAG)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_MAIN_MENU_HONEYCOMB_BATCH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_main_menu_honeycomb_batch"))
            .withVertexFormat(CombatantVertexFormats.POS2_LOCAL_COLOR_RECT_PARAMS5, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_LOCAL_COLOR_RECT_PARAMS5_GENERIC_VERT)
            .withFragmentShader(SHADER_UI_MAIN_MENU_HONEYCOMB_BATCH_FRAG)
            .withSampler("u_Texture")
            .withSampler("u_BlurTexture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_ROUNDED_STROKE_ANGULAR_BATCH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_rounded_stroke_angular_batch"))
            .withVertexFormat(CombatantVertexFormats.POS2_LOCAL_COLOR_RECT_PARAMS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_LOCAL_COLOR_RECT_PARAMS2_VERT)
            .withFragmentShader(SHADER_ROUNDED_RECT_STROKE_ANGULAR_BATCH_FRAG)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_PATH_BATCH = add(new ExtendedRenderPipelineBuilder(UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_path_batch"))
            .withVertexFormat(CombatantVertexFormats.POS2_COLOR_PARAMS, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_UI_PATH_FAST_VERT)
            .withFragmentShader(SHADER_UI_PATH_FRAG)
            .withContract(RenderPipelineContract.UI_WARPED)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    public static final RenderPipeline UI_PATH_BATCH_ANALYTIC_CLIP = add(new ExtendedRenderPipelineBuilder(UI_BATCH_UNIFORMS, UI_ANALYTIC_CLIP_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/analytic_clip/ui_path_batch"))
            .withVertexFormat(CombatantVertexFormats.POS2_COLOR_PARAMS, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_UI_PATH_FAST_VERT)
            .withFragmentShader(SHADER_UI_PATH_ANALYTIC_CLIP_FRAG)
            .withContract(RenderPipelineContract.UI_WARPED)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );

    /** Single-quad analytic waveform used by media/progress timelines. */
    public static final RenderPipeline UI_WAVE_BATCH = add(new ExtendedRenderPipelineBuilder(UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_wave_batch"))
            .withVertexFormat(CombatantVertexFormats.POS2_LOCAL_COLOR_RECT_PARAMS3, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_UI_GEOMETRY_FAST_VERT)
            .withFragmentShader(SHADER_UI_WAVE_FRAG)
            .withContract(RenderPipelineContract.UI_WARPED)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    public static final RenderPipeline UI_WAVE_BATCH_ANALYTIC_CLIP = add(new ExtendedRenderPipelineBuilder(UI_BATCH_UNIFORMS, UI_ANALYTIC_CLIP_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/analytic_clip/ui_wave_batch"))
            .withVertexFormat(CombatantVertexFormats.POS2_LOCAL_COLOR_RECT_PARAMS3, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_UI_GEOMETRY_FAST_VERT)
            .withFragmentShader(SHADER_UI_WAVE_ANALYTIC_CLIP_FRAG)
            .withContract(RenderPipelineContract.UI_WARPED)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );

    public static final RenderPipeline UI_SHAPE_BATCH = add(new ExtendedRenderPipelineBuilder(UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_geometry_batch"))
            .withVertexFormat(CombatantVertexFormats.POS2_LOCAL_COLOR_RECT_PARAMS3, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_UI_GEOMETRY_FAST_VERT)
            .withFragmentShader(SHADER_UI_SHAPE_BATCH_FRAG)
            .withContract(RenderPipelineContract.UI_WARPED)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_SHAPE_BATCH_ANALYTIC_CLIP = add(new ExtendedRenderPipelineBuilder(UI_BATCH_UNIFORMS, UI_ANALYTIC_CLIP_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/analytic_clip/ui_geometry_batch"))
            .withVertexFormat(CombatantVertexFormats.POS2_LOCAL_COLOR_RECT_PARAMS3, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_UI_GEOMETRY_FAST_VERT)
            .withFragmentShader(SHADER_UI_SHAPE_BATCH_ANALYTIC_CLIP_FRAG)
            .withContract(RenderPipelineContract.UI_WARPED)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_PRIMITIVE_BATCH = add(new ExtendedRenderPipelineBuilder(UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_primitive_batch"))
            .withVertexFormat(CombatantVertexFormats.POS2_LOCAL_COLOR_RECT_PARAMS5, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_UI_PRIMITIVE_FAST_VERT)
            .withFragmentShader(SHADER_UI_PRIMITIVE_BATCH_FRAG)
            .withContract(RenderPipelineContract.UI_WARPED)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    public static final RenderPipeline UI_ROUNDED_GLOW_BATCH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_rounded_glow_batch"))
            .withVertexFormat(CombatantVertexFormats.POS2_LOCAL_COLOR_RECT_PARAMS, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_LOCAL_COLOR_RECT_PARAMS_VERT)
            .withFragmentShader(SHADER_ROUNDED_RECT_GLOW_BATCH_FRAG)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_GLOW_BATCH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_glow_batch"))
            .withVertexFormat(CombatantVertexFormats.POS2_LOCAL_COLOR_RECT_PARAMS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_LOCAL_COLOR_RECT_PARAMS2_VERT)
            .withFragmentShader(SHADER_UI_GLOW_BATCH_FRAG)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunctions.ALPHA_ADDITIVE)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_TEXTURED_SHAPE_BATCH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_textured_shape_batch"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_LOCAL_COLOR_RECT_PARAMS, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_LOCAL_COLOR_RECT_PARAMS_VERT)
            .withFragmentShader(SHADER_UI_TEXTURED_SHAPE_BATCH_FRAG)
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_TEXTURED_SHAPE_BATCH_ANALYTIC_CLIP = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS, UI_ANALYTIC_CLIP_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/analytic_clip/ui_textured_shape_batch"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_LOCAL_COLOR_RECT_PARAMS, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_LOCAL_COLOR_RECT_PARAMS_VERT)
            .withFragmentShader(SHADER_UI_TEXTURED_SHAPE_BATCH_ANALYTIC_CLIP_FRAG)
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(true)
            .build()
    );
    public static final RenderPipeline UI_BLUR_BATCH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_blur_batch"))
            .withVertexFormat(CombatantVertexFormats.POS2_LOCAL_COLOR_RECT_PARAMS, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_LOCAL_COLOR_RECT_PARAMS_VERT)
            .withFragmentShader(SHADER_UI_BLUR_BATCH_FRAG)
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    public static final RenderPipeline UI_BLUR_BATCH_ANALYTIC_CLIP = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS, UI_ANALYTIC_CLIP_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/analytic_clip/ui_blur_batch"))
            .withVertexFormat(CombatantVertexFormats.POS2_LOCAL_COLOR_RECT_PARAMS, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_LOCAL_COLOR_RECT_PARAMS_VERT)
            .withFragmentShader(SHADER_UI_BLUR_BATCH_ANALYTIC_CLIP_FRAG)
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    public static final RenderPipeline UI_BLUR_BATCH_CORNERS = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_blur_batch_corners"))
            .withVertexFormat(CombatantVertexFormats.POS2_LOCAL_COLOR_RECT_PARAMS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_LOCAL_COLOR_RECT_PARAMS2_VERT)
            .withFragmentShader(SHADER_UI_BLUR_BATCH_CORNERS_FRAG)
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    public static final RenderPipeline UI_BLUR_BATCH_CORNERS_ANALYTIC_CLIP = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS, UI_ANALYTIC_CLIP_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/analytic_clip/ui_blur_batch_corners"))
            .withVertexFormat(CombatantVertexFormats.POS2_LOCAL_COLOR_RECT_PARAMS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_LOCAL_COLOR_RECT_PARAMS2_VERT)
            .withFragmentShader(SHADER_UI_BLUR_BATCH_CORNERS_ANALYTIC_CLIP_FRAG)
            .withSampler("u_Texture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    public static final RenderPipeline UI_LIQUID_GLASS_BATCH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_liquid_glass_batch"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_LOCAL_COLOR_RECT_PARAMS6, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_LOCAL_COLOR_RECT_PARAMS6_VERT)
            .withFragmentShader(SHADER_UI_LIQUID_GLASS_BATCH_FRAG)
            .withSampler("u_Texture")
            .withSampler("u_BlurTexture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    public static final RenderPipeline UI_LIQUID_GLASS_BATCH_ANALYTIC_CLIP = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS, UI_ANALYTIC_CLIP_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/analytic_clip/ui_liquid_glass_batch"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_LOCAL_COLOR_RECT_PARAMS6, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_LOCAL_COLOR_RECT_PARAMS6_VERT)
            .withFragmentShader(SHADER_UI_LIQUID_GLASS_BATCH_ANALYTIC_CLIP_FRAG)
            .withSampler("u_Texture")
            .withSampler("u_BlurTexture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    public static final RenderPipeline UI_LIQUID_GLASS_BATCH_UI_UNDERLAY = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS, UI_BACKDROP_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/ui_liquid_glass_batch_ui_underlay"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_LOCAL_COLOR_RECT_PARAMS6, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_LOCAL_COLOR_RECT_PARAMS6_VERT)
            .withFragmentShader(SHADER_UI_LIQUID_GLASS_BATCH_UI_UNDERLAY_FRAG)
            .withSampler("u_Texture")
            .withSampler("u_BlurTexture")
            .withSampler("u_UiUnderlayTexture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );
    public static final RenderPipeline UI_LIQUID_GLASS_BATCH_UI_UNDERLAY_ANALYTIC_CLIP = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, UI_BATCH_UNIFORMS, UI_ANALYTIC_CLIP_UNIFORMS, UI_BACKDROP_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/analytic_clip/ui_liquid_glass_batch_ui_underlay"))
            .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_LOCAL_COLOR_RECT_PARAMS6, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .withVertexShader(SHADER_POS_TEX_LOCAL_COLOR_RECT_PARAMS6_VERT)
            .withFragmentShader(SHADER_UI_LIQUID_GLASS_BATCH_UI_UNDERLAY_ANALYTIC_CLIP_FRAG)
            .withSampler("u_Texture")
            .withSampler("u_BlurTexture")
            .withSampler("u_UiUnderlayTexture")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build()
    );

    /**
     * Compile all registered pipelines (use after resource reload).
     */
    @AssetLoad(value = AssetLoadPhase.POST_RELOAD, order = 100)
    public static void precompile(ResourceManager resources) {
        final GpuDevice device;
        try {
            device = RenderSystem.getDevice();
        } catch (Throwable t) {
            DebugLog.warn("[Combatant] RenderSystem not ready in precompile(ResourceManager)");
            return;
        }

        DebugLog.renderThread("[Combatant] Precompiling pipelines: " + PIPELINES.size());
        ShaderCostRegistry.clear();

        for (RenderPipeline pipeline : PIPELINES) {
            DebugLog.renderThread("[Combatant] -> pipeline " + pipeline.getLocation());
            DebugLog.renderThread("   VS = " + pipeline.getVertexShader());
            DebugLog.renderThread("   FS = " + pipeline.getFragmentShader());
            DebugLog.renderThread("   VF = " + pipeline.getVertexFormatBinding(0));
            DebugLog.renderThread("   MODE = " + pipeline.getPrimitiveTopology());

            Runnable compile = () -> device.precompilePipeline(pipeline, (identifier, shaderType) -> {
                DebugLog.renderThread("[Combatant]   loading " + shaderType + " " + identifier);
                String source = CombatantShaderSources.load(resources, identifier, shaderType);
                ShaderCostRegistry.analyze(identifier, shaderType, source);
                return source;
            });
            if (isRigPipeline(pipeline)) {
                IrisRuntime.runWithNativeShaderBypass(compile);
            } else {
                compile.run();
            }
        }

        DebugLog.renderThread("[Combatant] Precompile finished; shader cost audit entries="
                + ShaderCostRegistry.all().size());
        for (var estimate : ShaderCostRegistry.top(12)) {
            DebugLog.renderThread("[ShaderCost] " + estimate.shaderId()
                    + " stage=" + estimate.stage()
                    + " score=" + estimate.weightedScore()
                    + " alu=" + estimate.aluOps()
                    + " trans=" + estimate.transcendentalOps()
                    + " tex=" + estimate.textureOps()
                    + " branch=" + estimate.branchOps()
                    + " loops=" + estimate.loopOps());
        }
    }

    /**
     * compile using the current MinecraftClient resource manager.
     */

    public static List<RenderPipeline> all() {
        return Collections.unmodifiableList(PIPELINES);
    }

    /**
     * Applies only the analytic-clip axis to an already selected UI text pipeline. The caller keeps
     * its existing fast or transformed/warped vertex path; clipping does not create another
     * placement mode.
     */
    public static RenderPipeline analyticClipTextPipeline(RenderPipeline pipeline) {
        if (pipeline == UI_TEXT) return UI_TEXT_ANALYTIC_CLIP;
        if (pipeline == UI_TEXT_FAST) return UI_TEXT_FAST_ANALYTIC_CLIP;
        if (pipeline == UI_TEXT_MSDF) return UI_TEXT_MSDF_ANALYTIC_CLIP;
        if (pipeline == UI_TEXT_MSDF_FAST) return UI_TEXT_MSDF_FAST_ANALYTIC_CLIP;
        if (pipeline == UI_TEXT_ANALYTIC_CLIP
                || pipeline == UI_TEXT_FAST_ANALYTIC_CLIP
                || pipeline == UI_TEXT_MSDF_ANALYTIC_CLIP
                || pipeline == UI_TEXT_MSDF_FAST_ANALYTIC_CLIP) {
            return pipeline;
        }
        return null;
    }

    /** Applies the analytic-clip axis without changing the texture placement/blend material. */
    public static RenderPipeline analyticClipTexturedPipeline(RenderPipeline pipeline) {
        if (pipeline == UI_TEXTURED) return UI_TEXTURED_ANALYTIC_CLIP;
        if (pipeline == UI_TEXTURED_FAST) return UI_TEXTURED_FAST_ANALYTIC_CLIP;
        if (pipeline == UI_TEXTURED_PREMULTIPLIED_ALPHA) {
            return UI_TEXTURED_PREMULTIPLIED_ALPHA_ANALYTIC_CLIP;
        }
        if (pipeline == UI_TEXTURED_ANALYTIC_CLIP
                || pipeline == UI_TEXTURED_FAST_ANALYTIC_CLIP
                || pipeline == UI_TEXTURED_PREMULTIPLIED_ALPHA_ANALYTIC_CLIP) {
            return pipeline;
        }
        return null;
    }

    public static RenderPipeline.Snippet meshUniforms() {
        return MESH_UNIFORMS;
    }

    public static RenderPipeline registerAddonPipeline(RenderPipeline pipeline) {
        return add(pipeline);
    }

    public static RenderPipeline rigEntity(RigRenderMode mode) {
        RigRenderMode resolved = mode == null ? RigRenderMode.CUTOUT : mode;
        return switch (resolved) {
            case CUTOUT -> RIG_ENTITY_CUTOUT;
            case CUTOUT_CULL -> RIG_ENTITY_CUTOUT_CULL;
            case TRANSLUCENT -> RIG_ENTITY_TRANSLUCENT;
            case TRANSLUCENT_CULL -> RIG_ENTITY_TRANSLUCENT_CULL;
            case TRANSLUCENT_NO_DEPTH_WRITE -> RIG_ENTITY_TRANSLUCENT_NO_DEPTH_WRITE;
            case TRANSLUCENT_NO_DEPTH_WRITE_CULL -> RIG_ENTITY_TRANSLUCENT_NO_DEPTH_WRITE_CULL;
        };
    }

    public static boolean isRigPipeline(RenderPipeline pipeline) {
        return pipeline != null
                && pipeline.getVertexFormatBinding(0) == CombatantVertexFormats.RIG_POSITION_TEXTURE_NORMAL_COLOR_BONES_DEFORM;
    }

    private static RenderPipeline rigEntityPipeline(String path, Identifier fragmentShader, boolean cull,
                                                    boolean depthWrite, boolean translucent) {
        ExtendedRenderPipelineBuilder builder = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, RIG_UNIFORMS)
                .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/" + path))
                .withDomain(PipelineDomain.WORLD)
                .withVertexFormat(CombatantVertexFormats.RIG_POSITION_TEXTURE_NORMAL_COLOR_BONES_DEFORM,
                        com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
                .withVertexShader(SHADER_RIG_TEXTURED_VERT)
                .withFragmentShader(fragmentShader)
                .withSampler("u_Texture")
                .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
                .withDepthWrite(depthWrite)
                .withCull(cull);
        if (translucent) builder.withBlend(BlendFunction.TRANSLUCENT);
        return builder.build();
    }

    private static RenderPipeline add(RenderPipeline pipeline) {
        declareDefaultShapeClipContract(pipeline);
        PIPELINES.add(pipeline);
        RenderPipelineRegistry.global().registerNative(pipeline);
        return pipeline;
    }

    private static void declareDefaultShapeClipContract(RenderPipeline pipeline) {
        if (!(pipeline instanceof IRenderPipeline combatantPipeline) || pipeline.getLocation() == null) return;
        ShapeClipRenderPassContract current = combatantPipeline.combatant$getShapeClipContract();
        if (current != ShapeClipRenderPassContract.NONE) return;

        String namespace = pipeline.getLocation().getNamespace();
        String path = pipeline.getLocation().getPath();
        if ("combatant".equals(namespace) && (path.startsWith("pipeline/ui_") || path.equals("pipeline/gui_texture_lookup"))) {
            combatantPipeline.combatant$setShapeClipContract(ShapeClipRenderPassContract.WHEN_ACTIVE);
        }
    }

}
