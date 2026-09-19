#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

#moj_import <sodium:globals.glsl>
#moj_import <sodium:fog.glsl>
#moj_import <sodium:chunk_vertex.glsl>

out vec4 v_Color;
out vec2 v_TexCoord;
out float v_ViewDistance;
flat out uint v_CombatantSurfaceFlags;

#ifdef COMBATANT_DEFERRED_GBUFFER
out vec4 v_CombatantBaseColor;
out vec2 v_CombatantLightCoord;
out vec3 v_CombatantViewPosition;
out float v_CombatantVertexAo;
flat out uint v_CombatantMaterialParams;
flat out uint v_CombatantMaterialId;
flat out uint v_CombatantMaterialMeta;
flat out uint v_CombatantMaterialSurface;
flat out vec4 v_CombatantTangent;
#endif

#ifdef USE_FOG
out vec2 v_FragDistance;
out float fadeFactor;
#endif

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

uniform sampler2D u_LightTex;

layout(location = 4) in uint a_CombatantSurfaceFlags;
#ifdef COMBATANT_DEFERRED_GBUFFER
layout(location = 5) in uint a_CombatantMaterialData;
layout(location = 6) in uint a_CombatantMaterialMeta;
layout(location = 7) in vec4 a_CombatantTangent;
layout(location = 8) in uint a_CombatantMaterialSurface;
layout(location = 9) in vec4 a_CombatantBaseColor;
#endif

const uint COMBATANT_SURFACE_WAVY_VEGETATION = 1u << 1u;
const uint COMBATANT_SURFACE_WAVY_VEGETATION_FREE = 1u << 2u;
const uint COMBATANT_WAVE_LOCAL_Y_SHIFT = 8u;
const uint COMBATANT_WAVE_LOCAL_Y_MASK = 15u;
const uint COMBATANT_WAVE_ROOTED_HORIZONTAL_SHIFT = 12u;
const uint COMBATANT_WAVE_ROOTED_VERTICAL_SHIFT = 16u;
const uint COMBATANT_WAVE_FREE_HORIZONTAL_SHIFT = 20u;
const uint COMBATANT_WAVE_FREE_VERTICAL_SHIFT = 24u;
const uint COMBATANT_WAVE_SPEED_SHIFT = 28u;
const uint COMBATANT_WAVE_SETTING_MASK = 15u;

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

float combatant_decode_wave_setting(uint flags, uint shift) {
    return float((flags >> shift) & COMBATANT_WAVE_SETTING_MASK) * (3.0 / 15.0);
}

vec3 combatant_apply_wavy_vegetation(vec3 position, uint flags) {
    if ((flags & COMBATANT_SURFACE_WAVY_VEGETATION) == 0u) {
        return position;
    }

    float speed = combatant_decode_wave_setting(flags, COMBATANT_WAVE_SPEED_SHIFT);
    if (speed <= 0.0001) {
        return position;
    }

    float frameTimeCounter = float(u_CurrentTime) * 0.001;
    float pi2wt = 150.796447372 * speed * frameTimeCounter;
    float magnitude = abs(sin(dot(vec4(frameTimeCounter * speed, position), vec4(1.0, 0.005, 0.005, 0.005))) * 0.5 + 0.72) * 0.013;

    if ((flags & COMBATANT_SURFACE_WAVY_VEGETATION_FREE) != 0u) {
        float horizontal = combatant_decode_wave_setting(flags, COMBATANT_WAVE_FREE_HORIZONTAL_SHIFT);
        float vertical = combatant_decode_wave_setting(flags, COMBATANT_WAVE_FREE_VERTICAL_SHIFT);
        vec3 move = sin(pi2wt * vec3(0.0063, 0.0224, 0.0015) * 1.5 - position) * magnitude;
        return position + vec3(move.x * horizontal, move.y * vertical, move.z * horizontal) * 5.0;
    }

    float horizontal = combatant_decode_wave_setting(flags, COMBATANT_WAVE_ROOTED_HORIZONTAL_SHIFT);
    float vertical = combatant_decode_wave_setting(flags, COMBATANT_WAVE_ROOTED_VERTICAL_SHIFT);
    vec2 move2 = (sin(pi2wt * vec2(0.0063, 0.0015) * 4.0 - position.xz + position.y * 0.05) + 0.1) * magnitude;
    float move2y = -length(move2);
    vec3 offset = vec3(move2.x * horizontal, move2y * vertical, move2.y * horizontal) * 5.0;
    float base = float((flags >> COMBATANT_WAVE_LOCAL_Y_SHIFT) & COMBATANT_WAVE_LOCAL_Y_MASK) / float(COMBATANT_WAVE_LOCAL_Y_MASK);
    return position + offset * base;
}

uvec3 _get_relative_chunk_coord(uint pos) {
    return uvec3(pos) >> uvec3(5u, 0u, 2u) & uvec3(7u, 3u, 7u);
}

vec3 _get_draw_translation(uint pos) {
    return _get_relative_chunk_coord(pos) * vec3(16.0);
}

void main() {
    _vert_init();

    vec3 translation = u_RegionOffset + _get_draw_translation(_draw_id);
    vec3 position = _vert_position + translation;
    position = combatant_apply_wavy_vegetation(position, a_CombatantSurfaceFlags);
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

#if defined(COMBATANT_SHADOW_PASS) || defined(COMBATANT_DEFERRED_GBUFFER)
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
