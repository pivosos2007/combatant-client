#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout(location = 0) out vec4 color;

uniform sampler2D u_Src;
uniform sampler2D u_Mask;

layout (std140) uniform HandSmoke {
    vec4 u_Fill;    // rgb, fill alpha
    vec4 u_Glow;    // rgb, alpha
    vec4 u_Shadow;  // rgb, alpha
    vec4 u_Params0; // edgeWidth, quality, octaves, time
    vec4 u_Params1; // resX, resY, scale, contrast
    vec4 u_Params2; // swirl, glowStrength, shadowStrength, density
    vec4 u_PaletteEnd;
    vec4 u_PaletteOptions;
    vec4 u_PaletteTime;
};

layout(location = 0) in vec2 v_TexCoord;

#include <combatant:hand_palette.glsl>
#include <combatant:hand_edge_coverage.glsl>

float handSmokeHash21(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

float handSmokeValueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = handSmokeHash21(i);
    float b = handSmokeHash21(i + vec2(1.0, 0.0));
    float c = handSmokeHash21(i + vec2(0.0, 1.0));
    float d = handSmokeHash21(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float handSmokeFbm(vec2 p, int octaves) {
    float v = 0.0;
    float a = 0.55;
    mat2 r = mat2(0.80, 0.60, -0.60, 0.80);
    int o = clamp(octaves, 1, 4);
    for (int i = 0; i < 4; ++i) {
        if (i >= o) break;
        v += a * handSmokeValueNoise(p);
        p = r * p * 2.03 + vec2(37.1, 17.7);
        a *= 0.52;
    }
    return v;
}

vec3 handSmokeField(vec2 fragCoord, vec2 resolution, float time, int octaves) {
    float scale = max(u_Params1.z, 0.05);
    float contrast = max(u_Params1.w, 0.05);
    float swirl = u_Params2.x;
    float density = max(u_Params2.w, 0.0);

    vec2 uv = fragCoord / max(resolution, vec2(1.0));
    vec2 aspect = vec2(resolution.x / max(resolution.y, 1.0), 1.0);
    vec2 p = (uv - 0.5) * aspect * scale;

    float slow = time * 0.13;
    vec2 curl = vec2(
        handSmokeFbm(p * 1.35 + vec2(0.0, slow), octaves),
        handSmokeFbm(p * 1.35 + vec2(5.2, -slow * 0.85), octaves)
    ) - 0.5;
    p += curl * (0.45 + swirl * 0.35);
    p.y -= time * 0.035;

    float body = handSmokeFbm(p * 2.0 + vec2(time * 0.025, -time * 0.055), octaves);
    float wisps = handSmokeFbm(p * 5.2 + vec2(-time * 0.06, time * 0.025), max(octaves - 1, 1));
    float veins = 0.5 + 0.5 * sin((p.x + curl.y * 0.8) * 7.5 + body * 3.2);
    veins = smoothstep(0.28, 0.88, veins);

    float m = mix(body, body * wisps, 0.34);
    m = mix(m, m * (0.74 + veins * 0.38), 0.42);
    m = pow(clamp(m * (0.9 + density * 0.35), 0.0, 1.0), 1.0 / contrast);

    vec3 baseColor = handPaletteColor(v_TexCoord, u_PaletteTime.x, u_Fill, u_PaletteEnd, u_PaletteOptions).rgb;
    vec3 smoke = mix(baseColor * 0.48, baseColor, m);
    smoke = mix(smoke, u_Glow.rgb, clamp((m - 0.62) * 1.55, 0.0, 0.38));
    return smoke * (0.50 + m * 0.72);
}

void main() {
    vec4 base = texture(u_Src, v_TexCoord);
    float mask = handMaskCoverage(v_TexCoord);
    float edgeWidth = max(u_Params0.x, 0.0);
    int quality = int(u_Params0.y + 0.5);
    int octaves = clamp(int(u_Params0.z + 0.5), 1, 4);
    float time = u_Params0.w;
    float glowStrength = max(u_Params2.y, 0.0);
    float shadowStrength = max(u_Params2.z, 0.0);
    bool outerEnabled = edgeWidth > 0.001 &&
            (u_Glow.a * glowStrength > 0.0001 || u_Shadow.a * shadowStrength > 0.0001);

    if (mask <= 0.001 && !outerEnabled) {
        color = vec4(base.rgb, 1.0);
        return;
    }

    vec2 resolution = max(u_Params1.xy, vec2(1.0));
    vec2 oneTexel = 1.0 / resolution;
    vec3 outCol = base.rgb;
    vec3 smoke = vec3(0.0);
    bool needsSmoke = mask > 0.001;

    // Share a continuous halo across partially covered edge pixels instead of
    // switching between two unrelated edge formulas at alpha == 0.01.
    if (outerEnabled && mask < 0.75) {
        float halo = handMaskOuterHalo(v_TexCoord, oneTexel, edgeWidth, quality);
        halo *= 1.0 - smoothstep(0.02, 0.75, mask);
        float shadowA = halo * u_Shadow.a * shadowStrength * 0.54;
        float glowA = halo * u_Glow.a * glowStrength * 0.40;
        outCol = mix(outCol, u_Shadow.rgb, shadowA);
        if (glowA > 0.0001) {
            smoke = handSmokeField(gl_FragCoord.xy, resolution, time, octaves);
            outCol = mix(outCol, smoke, glowA * 0.28);
            outCol += u_Glow.rgb * glowA;
            needsSmoke = false;
        }
    }

    if (mask > 0.001) {
        if (needsSmoke) {
            smoke = handSmokeField(gl_FragCoord.xy, resolution, time, octaves);
        }
        float innerEdge = handMaskInnerContour(v_TexCoord, oneTexel, mask);
        float alpha = clamp(u_Fill.a * mask, 0.0, 1.0);
        outCol = mix(outCol, smoke, alpha);
        outCol = mix(outCol, u_Shadow.rgb, innerEdge * u_Shadow.a * shadowStrength * 0.34);
        outCol += u_Glow.rgb * innerEdge * u_Glow.a * glowStrength * 0.24;
    }

    color = vec4(clamp(outCol, 0.0, 1.0), 1.0);
}
