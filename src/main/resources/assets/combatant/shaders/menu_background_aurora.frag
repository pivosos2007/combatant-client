#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

out vec4 color;

layout (std140) uniform MenuBackground {
    vec4 u_Params;     // x = width, y = height, z = time, w = pad
    vec4 u_Accent;     // rgb
    vec4 u_Background; // rgb
};

in vec2 v_TexCoord;

#define TAU 6.2831853071

float hash12(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.2330))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

void main() {
    vec2 res = u_Params.xy;
    float time = u_Params.z;
    vec2 fragCoord = v_TexCoord * res;
    vec2 uv = fragCoord / res;

    float o = noise(uv * 2.0 + vec2(0.0, time * 0.025));
    float d = (noise(uv * 3.5 - vec2(0.0, time * 0.02 + o * 0.02)) * 2.0 - 1.0);

    float v = uv.y + d * 0.06;
    v = 1.0 - abs(v * 2.0 - 1.0);
    v = smoothstep(0.0, 1.0, v);
    v = pow(v, 2.2 + sin((time * 0.2 + d * 0.25) * TAU) * 0.35);

    vec3 bg = u_Background.rgb;
    vec3 accent = u_Accent.rgb;
    vec3 accent2 = mix(accent, vec3(accent.b, accent.r, accent.g), 0.45);
    vec3 outCol = mix(bg, accent2, 0.12);

    float x = (1.0 - uv.x * 0.75);
    float y = 1.0 - abs(uv.y * 2.0 - 1.0);
    vec3 band = mix(bg, accent, 0.35 + 0.35 * v);
    vec3 glow = mix(accent2, accent, 0.5 + 0.5 * v);
    outCol = mix(outCol, band, v * 0.6);
    outCol += glow * vec3(x * 0.45, y, x) * v;

    vec2 seed = fragCoord;
    vec2 r;
    r.x = fract(sin((seed.x * 12.9898) + (seed.y * 78.2330)) * 43758.5453);
    r.y = fract(sin((seed.x * 53.7842) + (seed.y * 47.5134)) * 43758.5453);
    float s = mix(r.x, (sin((time * 2.5 + 60.0) * r.y) * 0.5 + 0.5) * ((r.y * r.y) * (r.y * r.y)), 0.04);
    outCol += accent * pow(s, 70.0) * (1.0 - v) * 0.7;

    // Prevent deep black gaps
    outCol = max(outCol, bg * 0.65 + vec3(0.01, 0.015, 0.02));

    color = vec4(clamp(outCol, 0.0, 1.0), 1.0);
}
