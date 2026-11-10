#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout(location = 0) in vec2 v_TexCoord;

#include <combatant:hand_ghost_detail.glsl>

#include <combatant:hand_palette.glsl>
layout(location = 0) out vec4 fragColor;

uniform sampler2D u_Src;
uniform sampler2D u_History;

layout (std140) uniform HandGhosting {
    vec4 u_Screen; // xy = full framebuffer size, z = delta seconds, w = time seconds
    vec4 u_Color;
    vec4 u_Params; // decay, strength, blur px, current reject
    vec4 u_Noise0; // quality, octaves, speed, scale
    vec4 u_Noise1; // swirl, contrast, density, history scale
    vec4 u_PaletteEnd;
    vec4 u_PaletteOptions;
    vec4 u_GhostDetails;
};

vec3 historyAt(vec2 uv) {
    return texture(u_History, clamp(uv, vec2(0.0), vec2(1.0))).rgb;
}

void accumulateHistory(inout vec2 trailNoise, inout float weight, vec2 uv, float sampleWeight) {
    vec3 h = historyAt(uv);
    trailNoise += vec2(h.r, h.b) * sampleWeight;
    weight += sampleWeight;
}

float currentMaskDilated(vec2 uv, vec2 texel, int quality) {
    float current = historyAt(uv).g;
    if (quality <= 1) return current;

    current = max(current, historyAt(uv + vec2( texel.x, 0.0)).g);
    current = max(current, historyAt(uv + vec2(-texel.x, 0.0)).g);
    current = max(current, historyAt(uv + vec2(0.0,  texel.y)).g);
    current = max(current, historyAt(uv + vec2(0.0, -texel.y)).g);
    return current;
}

void main() {
    vec4 base = texture(u_Src, v_TexCoord);
    int quality = clamp(int(u_Noise0.x + 0.5), 1, 4);

    ivec2 historySizeI = max(textureSize(u_History, 0), ivec2(1));
    vec2 historyTexel = 1.0 / vec2(historySizeI);
    float historyScale = max(u_Noise1.w, 0.05);
    float radius = max(u_Params.z, 0.0) * historyScale;
    vec2 stepUv = historyTexel * radius;

    vec2 sum = vec2(0.0);
    float weight = 0.0;
    accumulateHistory(sum, weight, v_TexCoord, 4.0);

    if (quality >= 2 && radius > 0.01) {
        accumulateHistory(sum, weight, v_TexCoord + vec2( stepUv.x, 0.0), 1.0);
        accumulateHistory(sum, weight, v_TexCoord + vec2(-stepUv.x, 0.0), 1.0);
        accumulateHistory(sum, weight, v_TexCoord + vec2(0.0,  stepUv.y), 1.0);
        accumulateHistory(sum, weight, v_TexCoord + vec2(0.0, -stepUv.y), 1.0);
    }
    if (quality >= 3 && radius > 0.01) {
        vec2 diagonal = stepUv * 0.72;
        accumulateHistory(sum, weight, v_TexCoord + vec2( diagonal.x,  diagonal.y), 0.8);
        accumulateHistory(sum, weight, v_TexCoord + vec2(-diagonal.x,  diagonal.y), 0.8);
        accumulateHistory(sum, weight, v_TexCoord + vec2( diagonal.x, -diagonal.y), 0.8);
        accumulateHistory(sum, weight, v_TexCoord + vec2(-diagonal.x, -diagonal.y), 0.8);
    }
    if (quality >= 4 && radius > 0.01) {
        vec2 outer = stepUv * 1.75;
        accumulateHistory(sum, weight, v_TexCoord + vec2( outer.x, 0.0), 0.45);
        accumulateHistory(sum, weight, v_TexCoord + vec2(-outer.x, 0.0), 0.45);
        accumulateHistory(sum, weight, v_TexCoord + vec2(0.0,  outer.y), 0.45);
        accumulateHistory(sum, weight, v_TexCoord + vec2(0.0, -outer.y), 0.45);
    }

    vec2 history = sum / max(weight, 1.0);
    float temporal = history.x;
    float turbulence = history.y;

    // Current hand rejection is not blurred together with temporal history. Using a small max
    // dilation prevents the blur kernel from painting a permanent halo around a stationary hand.
    float current = currentMaskDilated(v_TexCoord, historyTexel, quality);
    float ghost = max(temporal - current * clamp(u_Params.w, 0.0, 1.25), 0.0);
    // Most pixels contain no trail. Avoid procedural palette/ring work outside its footprint.
    if (ghost <= 0.0001) {
        fragColor = base;
        return;
    }

    float density = max(u_Noise1.z, 0.0);
    float contrast = max(u_Noise1.y, 0.05);
    ghost = pow(clamp(ghost * (0.62 + density * 0.27), 0.0, 1.0), contrast);
    ghost *= mix(0.82, 1.12, turbulence);
    ghost *= handGhostDetail(v_TexCoord, vec2(max(u_Screen.x, 1.0) / max(u_Screen.y, 1.0), 1.0),
                             u_Screen.w, turbulence, u_Noise0.w, u_Noise0.z, u_GhostDetails);

    vec4 palette = handPaletteColor(v_TexCoord, u_Screen.w, u_Color, u_PaletteEnd, u_PaletteOptions);
    // Beer-Lambert-like translucency: no additive glow or fully opaque colored silhouette.
    float opticalDepth = ghost * max(u_Params.y, 0.0) * palette.a * 1.20;
    float alpha = (1.0 - exp(-opticalDepth)) * 0.82;
    vec3 ghostColor = palette.rgb * mix(0.66, 0.94, turbulence);
    fragColor = vec4(mix(base.rgb, ghostColor, alpha), base.a);
}
