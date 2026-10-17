#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

#include <sodium:globals.glsl>
#include <sodium:fog.glsl>
#include <sodium:chunk_vertex.glsl>

layout(location = 0) out vec4 v_Color;
layout(location = 1) out vec2 v_TexCoord;
layout(location = 4) out float v_ViewDistance;
layout(location = 5) flat out uint v_CombatantSurfaceFlags;

#ifdef COMBATANT_DEFERRED_GBUFFER
layout(location = 6) out vec4 v_CombatantBaseColor;
layout(location = 7) out vec2 v_CombatantLightCoord;
layout(location = 8) out vec3 v_CombatantViewPosition;
layout(location = 9) out float v_CombatantVertexAo;
layout(location = 10) flat out uint v_CombatantMaterialParams;
layout(location = 11) flat out uint v_CombatantMaterialId;
layout(location = 12) flat out uint v_CombatantMaterialMeta;
layout(location = 13) flat out uint v_CombatantMaterialSurface;
layout(location = 14) flat out vec4 v_CombatantTangent;
#endif

layout(location = 2) out vec2 v_FragDistance;
layout(location = 3) out float fadeFactor;

uniform isamplerBuffer u_SectionTimeInfo;

#ifdef VULKAN
layout(push_constant) uniform PC {
    vec3 u_RegionOffset;
    int u_CurrentTime;
    uint u_RegionID;
};
#else
uniform vec3 u_RegionOffset;
uniform int u_CurrentTime;
uniform uint u_RegionID;
#endif

#ifndef OIT_ALPHA_ONLY
uniform sampler2D u_LightTex;
#endif

layout(location = 4) in uint a_CombatantSurfaceFlags;
#ifdef COMBATANT_DEFERRED_GBUFFER
layout(location = 5) in uint a_CombatantMaterialData;
layout(location = 6) in uint a_CombatantMaterialMeta;
layout(location = 7) in vec4 a_CombatantTangent;
layout(location = 8) in uint a_CombatantMaterialSurface;
layout(location = 9) in vec4 a_CombatantBaseColor;
#endif


#ifdef COMBATANT_DIRECTIONAL_SHADOW_DISTORTION
// Non-linear single-map warping concentrates texel density around the camera and decreases it
// continuously toward the finite shadow boundary without over-expanding diagonal axes.
const float COMBATANT_SHADOW_DISTORTION = 0.85;

float combatant_shadow_quartic_length(vec2 value) {
    vec2 squared = value * value;
    vec2 fourth = squared * squared;
    return sqrt(sqrt(fourth.x + fourth.y));
}

float combatant_shadow_distortion_factor(vec2 shadowNdc) {
    return combatant_shadow_quartic_length(shadowNdc) * COMBATANT_SHADOW_DISTORTION
            + (1.0 - COMBATANT_SHADOW_DISTORTION);
}
#endif


uvec3 _get_relative_chunk_coord(uint pos) {
    return uvec3(pos) >> uvec3(5u, 0u, 2u) & uvec3(7u, 3u, 7u);
}

vec3 _get_draw_translation(uint pos) {
    return _get_relative_chunk_coord(pos) * vec3(16.0);
}

void main() {
    _vert_init();

    v_FragDistance = vec2(0.0);
    fadeFactor = 1.0;

    vec3 translation = u_RegionOffset + _get_draw_translation(_draw_id);
    vec3 position = _vert_position + translation;
    vec4 viewPosition = u_ModelViewMatrix * vec4(position, 1.0);

#ifdef USE_FOG
    v_FragDistance = getFragDistance(position);

    int chunkId = int(_draw_id);
    int chunkFade = texelFetch(u_SectionTimeInfo, int((u_RegionID * 256u) + uint(chunkId))).r;
    float fade = clamp(float(u_CurrentTime - chunkFade) * u_FadePeriodInv, 0.0, 1.0);
    fadeFactor = (chunkFade < 0) ? 1.0 : fade;
#endif

    gl_Position = u_ProjectionMatrix * viewPosition;
#ifdef COMBATANT_DIRECTIONAL_SHADOW_DISTORTION
    if (abs(gl_Position.w) > 1.0e-7) {
        vec2 shadowNdc = gl_Position.xy / gl_Position.w;
        float distortionFactor = max(combatant_shadow_distortion_factor(shadowNdc), 1.0e-4);
        gl_Position.xy /= distortionFactor;
    }
#endif

#if defined(COMBATANT_SHADOW_PASS) || defined(COMBATANT_DEFERRED_GBUFFER) || defined(OIT_ALPHA_ONLY)
    // Deferred G-buffer stores material/base tint only. Vanilla lightmap is a lighting result and
    // must not be baked into albedo before Combatant's renderer-owned lighting stage.
    v_Color = _vert_color;
#else
    v_Color = _vert_color * texture(u_LightTex, _vert_tex_light_coord);
#endif
    v_TexCoord = (_vert_tex_diffuse_coord_bias * u_TexCoordShrink) + _vert_tex_diffuse_coord;
    v_ViewDistance = length(viewPosition.xyz);
    v_CombatantSurfaceFlags = a_CombatantSurfaceFlags;

#ifdef COMBATANT_DEFERRED_GBUFFER
    v_CombatantBaseColor = a_CombatantBaseColor;
    v_CombatantLightCoord = _vert_tex_light_coord;
    v_CombatantViewPosition = viewPosition.xyz;
    // AO is per-vertex data. Keep it smooth; packing it inside the flat material meta makes each
    // triangle inherit only its provoking vertex and creates the visible diagonal faceting.
    v_CombatantVertexAo = float(a_CombatantMaterialMeta & 255u) / 255.0;
    v_CombatantMaterialParams = _material_params;
    v_CombatantMaterialId = a_CombatantMaterialData;
    v_CombatantMaterialMeta = a_CombatantMaterialMeta;
    v_CombatantMaterialSurface = a_CombatantMaterialSurface;
    v_CombatantTangent = vec4(normalize(mat3(u_ModelViewMatrix) * a_CombatantTangent.xyz), a_CombatantTangent.w);
#endif
}
