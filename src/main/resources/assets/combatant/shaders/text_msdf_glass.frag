#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout(location = 0) out vec4 color;

uniform sampler2D u_Texture;
uniform sampler2D u_BlurTexture;

layout (std140) uniform MsdfText {
    vec4 u_Msdf;
};

layout (std140) uniform UIBatch {
    vec4 uScreen;
    vec4 uLayer;
};

layout (std140) uniform UIGlassFrame {
    vec4 uGlassPointer;
    vec4 uGlassMotion;
    vec4 uGlassClock;
};

layout(location = 0) in vec2 v_TexCoord;
layout(location = 2) in vec4 v_Color;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

vec2 safeNormalize(vec2 v, vec2 fallback) {
    float len2 = dot(v, v);
    return len2 > 1e-7 ? v * inversesqrt(len2) : fallback;
}

float glyphCoverage(vec2 atlasUv, float screenPxRange) {
    vec3 atlas = texture(u_Texture, atlasUv).rgb;
    float signedPx = screenPxRange * (median(atlas.r, atlas.g, atlas.b) - 0.5);
    return smoothstep(-0.68, 0.58, signedPx);
}

void main() {
    vec3 atlas = texture(u_Texture, v_TexCoord).rgb;
    float sd = median(atlas.r, atlas.g, atlas.b);

    vec2 atlasSize = max(u_Msdf.yz, vec2(1.0));
    vec2 unitRange = vec2(max(u_Msdf.x, 0.5)) / atlasSize;
    vec2 screenTexSize = vec2(1.0) / max(fwidth(v_TexCoord), vec2(1e-6));
    float screenPxRange = max(0.5 * dot(unitRange, screenTexSize), 1.0);
    float signedPx = screenPxRange * (sd - 0.5);
    float glyphAlpha = smoothstep(-0.68, 0.58, signedPx);

    vec2 normal = safeNormalize(vec2(dFdx(signedPx), dFdy(signedPx)), vec2(0.0, 1.0));
    float edge = 1.0 - smoothstep(0.0, 3.6, abs(signedPx));

    vec2 fbSize = max(uScreen.xy, vec2(1.0));
    vec2 uv = clamp(gl_FragCoord.xy / fbSize, vec2(0.001), vec2(0.999));
    float time = uGlassClock.x;
    vec2 tangent = vec2(-normal.y, normal.x);
    float wave = sin(time * 1.24 + gl_FragCoord.x * 0.026 - gl_FragCoord.y * 0.017);
    float crossWave = sin(time * 0.77 - gl_FragCoord.x * 0.012 - gl_FragCoord.y * 0.023);
    vec2 refractionDirection = safeNormalize(normal + tangent * (wave * 0.52 + crossWave * 0.20), normal);
    vec2 chromaDirection = safeNormalize(normal + tangent * (wave * 0.28), normal);
    float spreadPx = 1.55 + 0.30 * sin(time * 0.85 + gl_FragCoord.x * 0.016);
    vec2 atlasChromaStep = dFdx(v_TexCoord) * (chromaDirection.x * spreadPx)
            + dFdy(v_TexCoord) * (chromaDirection.y * spreadPx);
    vec3 glyphCoverageRgb = vec3(
            glyphCoverage(v_TexCoord - atlasChromaStep, screenPxRange),
            glyphAlpha,
            glyphCoverage(v_TexCoord + atlasChromaStep, screenPxRange));
    glyphCoverageRgb = vec3(1.0) - pow(max(vec3(1.0) - glyphCoverageRgb, vec3(0.0)), vec3(1.16));
    float compositeAlpha = max(glyphCoverageRgb.r, max(glyphCoverageRgb.g, glyphCoverageRgb.b));
    if (compositeAlpha * v_Color.a <= 0.001) discard;

    float offsetAmount = edge * 2.15 + pow(edge, 10.0) * 3.65;
    vec2 offset = refractionDirection * offsetAmount / fbSize;
    vec2 refractedUv = clamp(uv + offset,
            vec2(0.001), vec2(0.999));

    vec3 refracted = texture(u_BlurTexture, refractedUv).rgb;
    float aberration = 1.55 + 0.90 * (0.5 + 0.5 * sin(time * 1.08 + gl_FragCoord.x * 0.019));
    vec2 chroma = offset * aberration;
    vec3 glass = vec3(
            texture(u_BlurTexture, clamp(refractedUv - chroma, vec2(0.001), vec2(0.999))).r,
            refracted.g,
            texture(u_BlurTexture, clamp(refractedUv + chroma, vec2(0.001), vec2(0.999))).b
    );

    vec3 tint = clamp(v_Color.rgb, 0.0, 1.0);
    glass = mix(glass, glass * tint, 0.12);
    vec2 lightDirection = safeNormalize(vec2(sin(time * 0.20), cos(time * 0.30)), vec2(0.6, -0.8));
    vec2 fillDirection = safeNormalize(vec2(cos(time * 0.17 + 2.1), sin(time * 0.23 - 0.6)), vec2(-0.8, 0.6));
    float keyLight = pow(max(dot(normal, lightDirection), 0.0), 3.0);
    float fillLight = pow(max(dot(normal, fillDirection), 0.0), 4.0);
    float reflection = edge * (0.16 + keyLight * 0.25 + fillLight * 0.13);
    glass = mix(glass, tint, clamp(reflection, 0.0, 0.48));

    vec3 undistortedBackdrop = texture(u_BlurTexture, uv).rgb;
    vec3 channelCoverage = glyphCoverageRgb / max(compositeAlpha, 0.0001);
    vec3 resolvedGlass = mix(undistortedBackdrop, glass, channelCoverage);
    color = vec4(clamp(resolvedGlass, 0.0, 1.0), compositeAlpha * v_Color.a);
}
