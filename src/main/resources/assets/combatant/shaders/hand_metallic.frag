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
uniform sampler2D u_Occupancy;

layout (std140) uniform HandMetallic {
    vec4 u_Base;      // rgb, fill alpha
    vec4 u_Highlight; // rgb, alpha
    vec4 u_Glow;      // rgb, alpha
    vec4 u_Shadow;    // rgb, alpha
    vec4 u_Params0;   // intensity, sharpness, edgeStrength, time
    vec4 u_Params1;   // sweepSpeed, sweepScale, brushedLines, flakes
    vec4 u_Params2;   // glowStrength, shadowStrength, edgeWidth, prism
    vec4 u_Culling;   // x = conservative occupancy mask is available
    vec4 u_PaletteEnd;
    vec4 u_PaletteOptions;
};

layout(location = 0) in vec2 v_TexCoord;

#include <combatant:hand_palette.glsl>
#include <combatant:hand_edge_coverage.glsl>

const int HAND_METAL_OCCUPANCY_CELL_SIZE = 8;

float handMetalHash21(vec2 p) {
    p = fract(p * vec2(234.34, 435.45));
    p += dot(p, p + 31.31);
    return fract(p.x * p.y);
}

float handMetalNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = handMetalHash21(i);
    float b = handMetalHash21(i + vec2(1.0, 0.0));
    float c = handMetalHash21(i + vec2(0.0, 1.0));
    float d = handMetalHash21(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float handMetalFbm(vec2 p) {
    float v = 0.0;
    float a = 0.55;
    mat2 r = mat2(0.74, 0.67, -0.67, 0.74);
    for (int i = 0; i < 3; ++i) {
        v += a * handMetalNoise(p);
        p = r * p * 2.11 + vec2(19.7, 43.2);
        a *= 0.50;
    }
    return v;
}

void main() {
    vec4 base = texture(u_Src, v_TexCoord);
    float mask = handMaskCoverage(v_TexCoord);
    float glowStrength = max(u_Params2.x, 0.0);
    float shadowStrength = max(u_Params2.y, 0.0);
    float edgeWidth = max(u_Params2.z, 0.0);
    bool outerEnabled = edgeWidth > 0.001 &&
            (u_Glow.a * glowStrength > 0.0001 || u_Shadow.a * shadowStrength > 0.0001);

    if (mask <= 0.001 && !outerEnabled) {
        color = vec4(base.rgb, 1.0);
        return;
    }

    ivec2 sz = max(textureSize(u_Mask, 0), ivec2(1));
    // The 1/8-resolution occupancy field conservatively covers the halo radius.
    // It is an optimization only; it must not influence the material's edge strength.
    if (mask <= 0.01 && outerEnabled && u_Culling.x > 0.5) {
        ivec2 maskPixel = clamp(ivec2(floor(v_TexCoord * vec2(sz))), ivec2(0), sz - ivec2(1));
        ivec2 occupancyPixel = maskPixel / HAND_METAL_OCCUPANCY_CELL_SIZE;
        if (texelFetch(u_Occupancy, occupancyPixel, 0).r <= 0.001) {
            color = vec4(base.rgb, 1.0);
            return;
        }
    }

    vec2 resolution = vec2(sz);
    vec2 oneTexel = 1.0 / resolution;
    vec3 outCol = base.rgb;

    // The exterior halo overlaps the partially covered inner edge. Splitting at
    // mask > 0.01 created a one-pixel discontinuity once the mask was filtered.
    if (outerEnabled && mask < 0.75) {
        float halo = handMaskOuterHalo(v_TexCoord, oneTexel, edgeWidth, 4);
        halo *= 1.0 - smoothstep(0.02, 0.75, mask);
        float shadowA = halo * u_Shadow.a * shadowStrength * 0.48;
        float glowA = halo * u_Glow.a * glowStrength * 0.40;
        outCol = mix(outCol, u_Shadow.rgb, shadowA);
        outCol += u_Glow.rgb * glowA;
    }

    if (mask > 0.001) {
        float intensity = max(u_Params0.x, 0.0);
        float sharpness = max(u_Params0.y, 0.15);
        float edgeStrength = max(u_Params0.z, 0.0);
        float time = u_Params0.w;
        float sweepSpeed = u_Params1.x;
        float sweepScale = max(u_Params1.y, 0.1);
        float brushedLines = max(u_Params1.z, 0.0);
        float flakes = max(u_Params1.w, 0.0);
        float prism = max(u_Params2.w, 0.0);
        float edge = handMaskInnerContour(v_TexCoord, oneTexel, mask);

        vec2 aspect = vec2(resolution.x / max(resolution.y, 1.0), 1.0);
        vec2 p = v_TexCoord * aspect;
        float diagonal = dot(p, normalize(vec2(0.82, -0.57)));
        float sweep = sin(diagonal * sweepScale + time * sweepSpeed);
        sweep = pow(clamp(sweep * 0.5 + 0.5, 0.0, 1.0), sharpness);

        float fine = handMetalFbm(p * resolution.y * 0.030 + vec2(time * 0.04, -time * 0.025));
        float brushed = 0.5 + 0.5 * sin((p.y * resolution.y * 0.20 + p.x * 18.0 + fine * 7.0) * (0.34 + brushedLines * 0.32));
        brushed = mix(1.0, mix(0.78, 1.14, brushed), clamp(brushedLines, 0.0, 1.0));

        float sparkleNoise = handMetalFbm(p * resolution.y * 0.11 + vec2(time * 0.06, time * 0.035));
        float sparkle = smoothstep(0.76, 0.98, sparkleNoise) * flakes;
        sparkle *= 0.55 + 0.45 * sin(time * 2.2 + sparkleNoise * 17.0);

        float rim = pow(edge, sharpness * 0.55) * edgeStrength;
        float highlight = clamp(sweep * 0.70 + rim + sparkle * 0.85, 0.0, 1.0);

        vec3 baseColor = handPaletteColor(v_TexCoord, time, u_Base, u_PaletteEnd, u_PaletteOptions).rgb;
        vec3 metal = baseColor * brushed;
        metal += u_Highlight.rgb * intensity * highlight * u_Highlight.a;
        metal += u_Highlight.rgb * sparkle * 0.26 * intensity;

        if (prism > 0.001) {
            vec3 prismTint = vec3(
                sin(diagonal * 11.0 + time * 0.7) * 0.5 + 0.5,
                sin(diagonal * 11.0 + 2.1 + time * 0.62) * 0.5 + 0.5,
                sin(diagonal * 11.0 + 4.2 + time * 0.58) * 0.5 + 0.5
            );
            metal = mix(metal, metal * (0.68 + prismTint * 0.62), clamp(prism * highlight, 0.0, 0.65));
        }

        outCol = mix(outCol, metal, clamp(u_Base.a * mask, 0.0, 1.0));
        outCol = mix(outCol, u_Shadow.rgb, edge * u_Shadow.a * shadowStrength * 0.34);
        outCol += u_Glow.rgb * edge * u_Glow.a * glowStrength * 0.25;
    }

    color = vec4(clamp(outCol, 0.0, 1.0), 1.0);
}
