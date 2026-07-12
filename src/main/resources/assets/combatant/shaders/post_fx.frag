#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

out vec4 color;

uniform sampler2D u_Texture;
uniform sampler2D u_Lut;

layout (std140) uniform PostFX {
    vec4 u_Main;     // exposure, gamma, contrast, saturation
    vec4 u_Extra;    // vibrance, temperature, tint, lutStrength
    vec4 u_Vignette; // strength, radius, softness, chroma
    vec4 u_Effects;  // grainAmount, grainSize, sharpenAmount, toneMapMode
    vec4 u_Shadow;   // shadowTint rgb + strength
    vec4 u_Mid;      // midTint rgb + strength
    vec4 u_High;     // highTint rgb + strength
    vec4 u_Screen;   // invW, invH, time, padding
};

in vec2 v_TexCoord;

float rand(vec2 co) {
    return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}

vec3 applyLut(vec3 col) {
    float mode = u_Screen.w;
    vec3 c = clamp(col, 0.0, 1.0);
    if (mode < -999.0) {
        c += texture(u_Lut, vec2(0.5)).rgb;
    }
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));

    if (mode > 1.5) {
        vec3 cool = c * vec3(0.94, 1.00, 1.08) + vec3(-0.010, 0.000, 0.018);
        cool = mix(cool, vec3(luma), 0.035);
        return clamp(cool, 0.0, 1.0);
    }

    if (mode > 0.5) {
        vec3 warm = c * vec3(1.08, 1.015, 0.92) + vec3(0.018, 0.004, -0.014);
        warm = mix(warm, vec3(luma), 0.025);
        return clamp(warm, 0.0, 1.0);
    }

    return c;
}

vec3 applyToneMap(vec3 col, float mode) {
    if (mode < 0.5) return col;
    vec3 x = max(col, vec3(0.0));
    return (x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14);
}

void main() {
    vec3 col = texture(u_Texture, v_TexCoord).rgb;

    float chroma = max(u_Vignette.w, 0.0);
    if (chroma > 0.0001) {
        vec2 dir = v_TexCoord - vec2(0.5);
        vec2 shift = dir * chroma * 0.02;
        float r = texture(u_Texture, v_TexCoord + shift).r;
        float g = col.g;
        float b = texture(u_Texture, v_TexCoord - shift).b;
        col = vec3(r, g, b);
    }

    float sharpen = max(u_Effects.z, 0.0);
    if (sharpen > 0.0001) {
        vec2 texel = vec2(u_Screen.x, u_Screen.y);
        vec3 n = texture(u_Texture, v_TexCoord + vec2(0.0, texel.y)).rgb;
        vec3 s = texture(u_Texture, v_TexCoord - vec2(0.0, texel.y)).rgb;
        vec3 e = texture(u_Texture, v_TexCoord + vec2(texel.x, 0.0)).rgb;
        vec3 w = texture(u_Texture, v_TexCoord - vec2(texel.x, 0.0)).rgb;
        vec3 blur = (n + s + e + w) * 0.25;
        col = mix(col, col + (col - blur) * 1.5, sharpen);
    }

    float exposure = u_Main.x;
    col *= pow(2.0, exposure);

    col = applyToneMap(col, u_Effects.w);

    float gamma = max(u_Main.y, 0.01);
    col = pow(max(col, 0.0), vec3(1.0 / gamma));

    float contrast = max(u_Main.z, 0.0);
    col = (col - 0.5) * contrast + 0.5;

    float saturation = max(u_Main.w, 0.0);
    float luma = dot(col, vec3(0.2126, 0.7152, 0.0722));
    col = mix(vec3(luma), col, saturation);

    float vibrance = u_Extra.x;
    if (abs(vibrance - 1.0) > 0.0001) {
        float maxc = max(col.r, max(col.g, col.b));
        float minc = min(col.r, min(col.g, col.b));
        float sat = maxc - minc;
        float boost = (1.0 - sat) * (vibrance - 1.0);
        col = mix(col, col + (col - vec3(luma)) * boost, 1.0);
    }

    float temp = clamp(u_Extra.y, -1.0, 1.0);
    float tint = clamp(u_Extra.z, -1.0, 1.0);
    col += vec3(temp, temp * 0.2, -temp) * 0.1;
    col += vec3(-tint, tint, -tint) * 0.05;

    float shadowW = smoothstep(0.65, 0.05, luma);
    float highW = smoothstep(0.4, 1.0, luma);
    float midW = clamp(1.0 - shadowW - highW, 0.0, 1.0);

    col = mix(col, col * u_Shadow.rgb, u_Shadow.a * shadowW);
    col = mix(col, col * u_Mid.rgb, u_Mid.a * midW);
    col = mix(col, col * u_High.rgb, u_High.a * highW);

    float lutStrength = clamp(u_Extra.w, 0.0, 1.0);
    if (lutStrength > 0.0001) {
        vec3 lutCol = applyLut(col);
        col = mix(col, lutCol, lutStrength);
    }

    float vigStrength = clamp(u_Vignette.x, 0.0, 1.0);
    if (vigStrength > 0.0001) {
        float radius = clamp(u_Vignette.y, 0.0, 1.0);
        float softness = clamp(u_Vignette.z, 0.001, 1.0);
        float dist = distance(v_TexCoord, vec2(0.5));
        float vig = smoothstep(radius, radius + softness, dist);
        col *= mix(1.0, 1.0 - vigStrength, vig);
    }

    float grainAmount = clamp(u_Effects.x, 0.0, 0.25);
    if (grainAmount > 0.0001) {
        float grainSize = max(u_Effects.y, 0.25);
        vec2 gUv = v_TexCoord * grainSize * 512.0 + vec2(u_Screen.z);
        float n = rand(gUv) - 0.5;
        col += n * grainAmount;
    }

    col = clamp(col, 0.0, 1.0);
    color = vec4(col, 1.0);
}
