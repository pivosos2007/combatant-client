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
import combatant.client.render.engine.vertex.CombatantVertexFormats;
import net.minecraft.resources.Identifier;

public final class EffectRenderPipelines {
    private static final RenderPipeline.Snippet MESH_UNIFORMS = CombatantRenderPipelines.meshUniforms();
    private static final RenderPipeline.Snippet FOG_UNIFORMS = CombatantRenderPipelines.fogUniforms();


    private EffectRenderPipelines() {
    }

    @PipelineRegister
    public static final RenderPipeline WORLD_REIMAGINED_SKYBOX_SHADER = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS, FOG_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_reimagined_skybox_shader"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("reimagined_skybox.frag"))
            .withUniform("SkyboxShader", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT).withCull(false).build();
    /**
     * Fullscreen heat distortion + vignette (pos2).
     */
    @PipelineRegister
    public static final RenderPipeline HEAT_FX = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/heat_fx"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("heat_fx.frag"))
            .withSampler("u_Texture")
            .withUniform("Heat", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    /**
     * Fullscreen shader ESP gradient composite (pos2).
     */
    @PipelineRegister
    public static final RenderPipeline SHADER_ESP_GRADIENT = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/shader_esp_gradient"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("shader_esp_gradient.frag"))
            .withSampler("u_Texture")
            .withUniform("ShaderEspGradient", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    /**
     * Fullscreen shader ESP separable shadow blur (pos2).
     */
    @PipelineRegister
    public static final RenderPipeline SHADER_ESP_SHADOW = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/shader_esp_shadow"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("shader_esp_shadow.frag"))
            .withSampler("u_Texture")
            .withSampler("u_Mask")
            .withUniform("ShaderEspBlur", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    /**
     * Fullscreen shader ESP procedural smoke fill (pos2).
     */
    @PipelineRegister
    public static final RenderPipeline SHADER_ESP_SMOKE = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/shader_esp_smoke"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("shader_esp_smoke.frag"))
            .withSampler("u_Texture")
            .withUniform("ShaderEspSmoke", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    /**
     * No depth test; additive; textured-color triangles with procedural sun shading.
     */
    /**
     * Fullscreen nether portal rift (pos2).
     */
    @PipelineRegister
    public static final RenderPipeline PORTAL_RIFT = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/portal_rift"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("portal_rift.frag"))
            .withSampler("u_Texture")
            .withUniform("Heat", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    /**
     * Fullscreen sleep overlay (pos2).
     */
    @PipelineRegister
    public static final RenderPipeline SLEEP_OVERLAY = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/sleep_overlay"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("sleep_overlay.frag"))
            .withSampler("u_Texture")
            .withUniform("PostProcess", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    /**
     * Fullscreen hand glass (pos2).
     */
    @PipelineRegister
    public static final RenderPipeline HAND_GLASS = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/hand_glass"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("hand_glass.frag"))
            .withSampler("u_Src")
            .withSampler("u_Mask")
            .withUniform("HandGlass", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();

    @PipelineRegister
    public static final RenderPipeline HAND_SMOKE = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/hand_smoke"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("hand_smoke.frag"))
            .withSampler("u_Src")
            .withSampler("u_Mask")
            .withUniform("HandSmoke", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();

    @PipelineRegister
    public static final RenderPipeline HAND_RIFT = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/hand_rift"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("hand_rift.frag"))
            .withSampler("u_Src")
            .withSampler("u_Mask")
            .withUniform("HandRift", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();

    @PipelineRegister
    public static final RenderPipeline HAND_METALLIC = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/hand_metallic"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("hand_metallic.frag"))
            .withSampler("u_Src")
            .withSampler("u_Mask")
            .withSampler("u_Occupancy")
            .withUniform("HandMetallic", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();

    @PipelineRegister
    public static final RenderPipeline HAND_MASK_OCCUPANCY = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/hand_mask_occupancy"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("hand_mask_occupancy.frag"))
            .withSampler("u_Mask")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withoutBlend()
            .withCull(false)
            .build();

    @PipelineRegister
    public static final RenderPipeline HAND_MASK_OCCUPANCY_DILATE = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/hand_mask_occupancy_dilate"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("hand_mask_occupancy_dilate.frag"))
            .withSampler("u_Mask")
            .withUniform("HandMetallic", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withoutBlend()
            .withCull(false)
            .build();

    @PipelineRegister
    public static final RenderPipeline HAND_GHOSTING_HISTORY = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/hand_ghosting_history"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("hand_ghosting_history.frag"))
            .withSampler("u_History")
            .withSampler("u_Mask")
            .withUniform("HandGhosting", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withoutBlend()
            .withCull(false)
            .build();

    @PipelineRegister
    public static final RenderPipeline HAND_GHOSTING = new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/hand_ghosting"))
            .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withVertexShader(shader("damage_tint.vert"))
            .withFragmentShader(shader("hand_ghosting.frag"))
            .withSampler("u_Src")
            .withSampler("u_History")
            .withUniform("HandGhosting", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withoutBlend()
            .withCull(false)
            .build();

    private static Identifier shader(String path) {
        return Identifier.fromNamespaceAndPath("combatant", "shaders/" + path);
    }
}
