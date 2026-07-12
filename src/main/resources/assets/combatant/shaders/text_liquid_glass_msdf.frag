#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

out vec4 color;

uniform sampler2D u_Texture;      // MSDF glyph atlas
uniform sampler2D u_SceneTexture; // clean background/source
uniform sampler2D u_BlurTexture;  // prepared liquid-glass blur source

layout (std140) uniform MsdfText {
    vec4 u_Msdf; // x = pxRange, y = atlasWidth, z = atlasHeight
};

layout (std140) uniform UIBatch {
    vec4 uScreen; // xy = framebuffer, zw = logical
};

in vec2 v_TexCoord;
in vec4 v_Color;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

float luma(vec3 c) {
    return dot(c, LUMA);
}

vec2 safeNormal(float a) {
    vec2 g = vec2(dFdx(a), dFdy(a));
    float len2 = dot(g, g);
    if (len2 <= 1e-8) return vec2(0.0, -1.0);
    return g * inversesqrt(len2);
}

vec3 glassMap(vec3 scene, vec3 tint) {
    float y = max(luma(scene), 0.0001);
    float bright = smoothstep(0.48, 0.88, y);
    float target = mix(y, 0.50 + 0.20 * sqrt(y), bright);
    vec3 mapped = scene * (target / y);
    float mappedY = luma(mapped);
    mapped = vec3(mappedY) + (mapped - vec3(mappedY)) * 1.10;
    mapped += (tint - vec3(luma(tint))) * 0.075;
    return clamp(mapped, 0.0, 1.0);
}

float msdfCoverage(out float sd) {
    vec3 sample = texture(u_Texture, v_TexCoord).rgb;
    sd = median(sample.r, sample.g, sample.b);

    float pxRange = u_Msdf.x;
    vec2 atlasSize = u_Msdf.yz;
    vec2 unitRange = vec2(pxRange) / atlasSize;
    vec2 screenTexSize = vec2(1.0) / fwidth(v_TexCoord);
    float screenPxRange = max(0.5 * dot(unitRange, screenTexSize), 1.0);
    return clamp(screenPxRange * (sd - 0.5) + 0.5, 0.0, 1.0);
}

void main() {
    float sd;
    float glyph = msdfCoverage(sd);
    if (glyph <= 0.002) discard;

    vec2 fbSize = max(uScreen.xy, vec2(1.0));
    vec2 uv = clamp(gl_FragCoord.xy / fbSize, vec2(0.001), vec2(0.999));

    vec2 n = safeNormal(sd);
    float edge = 1.0 - smoothstep(0.030, 0.150, abs(sd - 0.50));
    float coverage = glyph;

    float distortionPx = mix(2.4, 8.8, edge);
    vec2 refractUv = clamp(uv - n * distortionPx / fbSize, vec2(0.001), vec2(0.999));
    vec2 chromaR = clamp(refractUv + n * 1.45 / fbSize, vec2(0.001), vec2(0.999));
    vec2 chromaB = clamp(refractUv - n * 1.45 / fbSize, vec2(0.001), vec2(0.999));

    vec3 tint = clamp(v_Color.rgb, 0.0, 1.0);
    vec3 blur = texture(u_BlurTexture, refractUv).rgb;
    vec3 clean = texture(u_SceneTexture, refractUv).rgb;
    vec3 chroma = vec3(texture(u_SceneTexture, chromaR).r, clean.g, texture(u_SceneTexture, chromaB).b);

    vec3 base = glassMap(mix(blur, clean, 0.18), tint);
    vec3 rimScene = glassMap(mix(blur, chroma, 0.60), tint);
    vec3 finalColor = mix(base, rimScene, edge * 0.48);

    float topLight = clamp(dot(n, normalize(vec2(-0.25, 1.0))) * 0.5 + 0.5, 0.0, 1.0);
    float bright = smoothstep(0.52, 0.88, luma(base));
    float whiteRim = edge * (0.32 + 0.32 * topLight) * (1.0 - bright * 0.44);
    finalColor = mix(finalColor, mix(vec3(1.0), tint, 0.10), whiteRim);

    float innerSheen = smoothstep(0.62, 1.0, glyph) * (0.055 + 0.050 * topLight);
    finalColor = mix(finalColor, vec3(1.0), innerSheen * (1.0 - bright * 0.35));

    float alpha = coverage * v_Color.a * clamp(0.72 + edge * 0.45, 0.0, 1.0);
    if (alpha <= 0.002) discard;
    color = vec4(finalColor, alpha);
}
