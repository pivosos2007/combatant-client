#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

in vec4 v_Local;
in vec4 v_Color;
in vec4 v_Rect;
in vec4 v_CornerModes;
in vec4 v_CornerExtentX;
in vec4 v_CornerExtentY;
in vec4 v_EdgeModes;
in vec4 v_EdgeData;

out vec4 fragColor;

layout (std140) uniform UIBatch {
    vec4 uScreen; // xy = framebuffer size, zw = logical size
    vec4 uLayer;
};

#moj_import <combatant:ui_aa.glsl>
#moj_import <combatant:ui_geometry.glsl>

float random(vec2 st) {
    return fract(sin(dot(st, vec2(12.9898, 78.233))) * 43758.5453123);
}

float noise(vec2 st) {
    vec2 i = floor(st);
    vec2 f = fract(st);
    float a = random(i);
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float fbm(vec2 st, int octaves) {
    float v = 0.0;
    float a = 0.5;
    vec2 shift = vec2(100.0);
    mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));
    for (int i = 0; i < 8; i++) {
        if (i >= octaves) break;
        v += a * noise(st);
        st = rot * st * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

vec2 warpedLocal(vec4 local) {
    float invW = abs(local.z) > 0.000001 ? local.z : 1.0;
    return local.xy / invW;
}

vec3 smokeColor(vec2 frag, vec2 resolution, float time, int octaves, float scale, vec2 flow, vec3 c0, vec3 c1, vec3 c2) {
    vec2 uv = (frag / max(resolution, vec2(1.0))) * vec2(scale, scale * 1.75);
    uv += flow * time;

    vec2 q;
    q.x = fbm(uv, octaves);
    q.y = fbm(uv + vec2(1.0), octaves);

    vec2 r;
    r.x = fbm(uv + q + vec2(1.7, 9.2) + 0.18 * time, octaves);
    r.y = fbm(uv + q + vec2(8.3, 2.8) + 0.14 * time, octaves);

    float f = fbm(uv + r, octaves);
    vec3 col = c0;
    col = mix(col, c1, clamp(length(q), 0.0, 1.0));
    col = mix(col, c2, clamp(abs(r.x), 0.0, 1.0));
    return (f * f * f + 0.62 * f * f + 0.50 * f) * col;
}

void main() {
    vec2 logicalScale = uScreen.zw / uScreen.xy;
    vec2 frag = warpedLocal(v_Local);

    vec2 halfSize = v_Rect.zw * 0.5;
    vec2 center = v_Rect.xy + halfSize;
    float radius = clamp(v_CornerModes.x, 0.0, min(halfSize.x, halfSize.y));
    float softness = max(v_CornerModes.y, 0.0);
    float fillRatio = clamp(v_CornerModes.z, 0.0, 1.0);
    bool fromRight = v_CornerModes.w > 0.5;

    if (fillRatio <= 0.0001) discard;

    float d = roundedBoxSdf(frag - center, halfSize, radius);
    float edge = analyticAa(d, logicalScale, softness);
    float shapeAlpha = coverage(d, edge);

    float clipX = fromRight
        ? v_Rect.x + v_Rect.z * (1.0 - fillRatio)
        : v_Rect.x + v_Rect.z * fillRatio;
    float progressAlpha = fromRight
        ? coverage(clipX - frag.x, edge)
        : coverage(frag.x - clipX, edge);

    float mask = shapeAlpha * progressAlpha;
    if (mask <= 0.001) discard;

    float time = v_EdgeModes.x;
    float smokeScale = max(v_EdgeModes.y, 0.1);
    float smokeMix = clamp(v_EdgeModes.z, 0.0, 1.0);
    int octaves = int(clamp(floor(v_EdgeModes.w + 0.5), 1.0, 8.0));
    vec2 flow = v_EdgeData.xy;
    float intensity = max(v_EdgeData.z, 0.0);

    vec2 local = frag - v_Rect.xy;
    vec3 c0 = v_Color.rgb;
    vec3 c1 = v_CornerExtentX.rgb;
    vec3 c2 = v_CornerExtentY.rgb;
    vec3 base = mix(c0, c1, clamp(local.x / max(v_Rect.z, 1.0), 0.0, 1.0));
    vec3 smoke = smokeColor(local, v_Rect.zw, time, octaves, smokeScale, flow, c0, c1, c2) * intensity;
    vec3 outColor = mix(base, smoke, smokeMix);

    fragColor = vec4(outColor, v_Color.a * mask);
}
