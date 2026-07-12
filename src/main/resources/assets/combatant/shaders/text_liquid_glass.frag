#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

out vec4 color;

uniform sampler2D u_Texture;      // glyph atlas alpha
uniform sampler2D u_SceneTexture; // clean background/source
uniform sampler2D u_BlurTexture;  // prepared liquid-glass blur source

layout (std140) uniform UIBatch {
    vec4 uScreen; // xy = framebuffer, zw = logical
};

in vec2 v_TexCoord;
in vec4 v_Color;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

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

void main() {
    float glyph = texture(u_Texture, v_TexCoord).r;
    if (glyph <= 0.002) discard;

    vec2 fbSize = max(uScreen.xy, vec2(1.0));
    vec2 uv = clamp(gl_FragCoord.xy / fbSize, vec2(0.001), vec2(0.999));

    vec2 n = safeNormal(glyph);
    float edge = 1.0 - smoothstep(0.08, 0.34, abs(glyph - 0.50));
    float coverage = smoothstep(0.015, 0.92, glyph);

    float distortionPx = mix(1.8, 7.5, edge);
    vec2 refractUv = clamp(uv - n * distortionPx / fbSize, vec2(0.001), vec2(0.999));
    vec2 chromaR = clamp(refractUv + n * 1.25 / fbSize, vec2(0.001), vec2(0.999));
    vec2 chromaB = clamp(refractUv - n * 1.25 / fbSize, vec2(0.001), vec2(0.999));

    vec3 tint = clamp(v_Color.rgb, 0.0, 1.0);
    vec3 blur = texture(u_BlurTexture, refractUv).rgb;
    vec3 clean = texture(u_SceneTexture, refractUv).rgb;
    vec3 chroma = vec3(texture(u_SceneTexture, chromaR).r, clean.g, texture(u_SceneTexture, chromaB).b);

    vec3 base = glassMap(mix(blur, clean, 0.16), tint);
    vec3 rimScene = glassMap(mix(blur, chroma, 0.54), tint);
    vec3 finalColor = mix(base, rimScene, edge * 0.42);

    float topLight = clamp(dot(n, normalize(vec2(-0.25, 1.0))) * 0.5 + 0.5, 0.0, 1.0);
    float bright = smoothstep(0.52, 0.88, luma(base));
    float whiteRim = edge * (0.26 + 0.28 * topLight) * (1.0 - bright * 0.44);
    finalColor = mix(finalColor, mix(vec3(1.0), tint, 0.12), whiteRim);

    float innerSheen = smoothstep(0.45, 1.0, glyph) * (0.055 + 0.050 * topLight);
    finalColor = mix(finalColor, vec3(1.0), innerSheen * (1.0 - bright * 0.35));

    float alpha = coverage * v_Color.a * clamp(0.70 + edge * 0.42, 0.0, 1.0);
    if (alpha <= 0.002) discard;
    color = vec4(finalColor, alpha);
}
