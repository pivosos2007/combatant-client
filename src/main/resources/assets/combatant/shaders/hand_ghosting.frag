#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

in vec2 v_TexCoord;
out vec4 fragColor;

uniform sampler2D u_Src;
uniform sampler2D u_History;

layout (std140) uniform HandGhosting {
    vec4 u_Screen; // xy = full framebuffer size, z = delta seconds, w = time seconds
    vec4 u_Color;
    vec4 u_Params; // decay, strength, blur px, current reject
    vec4 u_Noise0; // quality, octaves, speed, scale
    vec4 u_Noise1; // swirl, contrast, density, history scale
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

    float density = max(u_Noise1.z, 0.0);
    float contrast = max(u_Noise1.y, 0.05);
    ghost = pow(clamp(ghost * (0.78 + density * 0.55), 0.0, 1.0), contrast);
    ghost *= mix(0.78, 1.12, turbulence);

    float alpha = clamp(ghost * max(u_Params.y, 0.0) * u_Color.a, 0.0, 1.0);
    vec3 ghostColor = u_Color.rgb * (0.84 + turbulence * 0.32);
    vec3 outColor = mix(base.rgb, ghostColor, alpha * 0.78);
    outColor += ghostColor * alpha * 0.18;

    fragColor = vec4(clamp(outColor, 0.0, 1.0), base.a);
}
