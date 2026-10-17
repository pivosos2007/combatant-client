#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout(location = 0) in vec2 v_TexCoord;
layout(location = 0) out vec4 color;

uniform sampler2D u_Source;
uniform sampler2D u_GbufferAuxiliary;
uniform sampler2D u_GbufferDepth;
uniform sampler2D u_CurrentDepth;

layout(std140) uniform DeferredLighting {
    mat4 u_InverseProjection;
    vec4 u_FogColor;
    vec4 u_FogRanges;
    vec4 u_DirectionalDirection;
    vec4 u_DirectionalRadiance;
    vec4 u_DepthAndFlags;
    vec4 u_CloudShadowFlags;
};

float combatant_decode_distance(float encoded) {
    return exp2(encoded * 17.0 - 1.0) - 1.0;
}

float combatant_linear_fog(float distanceValue, float start, float end) {
    if (distanceValue <= start) return 0.0;
    if (distanceValue >= end) return 1.0;
    return (distanceValue - start) / max(end - start, 0.0001);
}

bool combatant_terrain_owns_pixel(vec2 uv) {
    float gbufferDepth = texture(u_GbufferDepth, uv).r;
    float currentDepth = texture(u_CurrentDepth, uv).r;
    if (gbufferDepth <= 0.0 || currentDepth <= 0.0) return false;
    float tolerance = max(1.0e-6, abs(gbufferDepth) * 1.0e-5);
    return abs(gbufferDepth - currentDepth) <= tolerance;
}

vec3 combatant_apply_compatibility_fog(vec3 radiance, vec4 auxiliary) {
    float sphericalDistance = combatant_decode_distance(auxiliary.r);
    float cylindricalDistance = combatant_decode_distance(auxiliary.g);
    float environmentalFog = combatant_linear_fog(
            sphericalDistance, u_FogRanges.x, u_FogRanges.y);
    float renderFog = combatant_linear_fog(
            cylindricalDistance, u_FogRanges.z, u_FogRanges.w);
    float fogAmount = max(1.0 - auxiliary.b, max(environmentalFog, renderFog));
    return mix(radiance, u_FogColor.rgb, fogAmount * u_FogColor.a);
}

void main() {
    vec4 source = texture(u_Source, v_TexCoord);
    if (combatant_terrain_owns_pixel(v_TexCoord)) {
        vec4 auxiliary = texture(u_GbufferAuxiliary, v_TexCoord);
        source.rgb = combatant_apply_compatibility_fog(source.rgb, auxiliary);
    }
    color = source;
}
