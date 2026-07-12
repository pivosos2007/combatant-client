#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

in vec2 v_TexCoord;
in vec4 v_Color;
in vec4 v_Params;
in vec4 v_Params2;

out vec4 fragColor;

float circleSdf(vec2 p, float r) {
    return length(p) - r;
}

void main() {
    vec2 local = v_TexCoord * 2.0 - 1.0;

    float radius = max(v_Params.x, 0.001);
    float softness = max(v_Params.y, 0.0001);
    float strokeWidth = max(v_Params.z, 0.0);
    float patternScale = max(v_Params.w, 0.0);

    float fillAlpha = clamp(v_Params2.x, 0.0, 1.0);
    float strokeAlpha = clamp(v_Params2.y, 0.0, 1.0);
    float patternStrength = clamp(v_Params2.z, 0.0, 1.0);

    float d = circleSdf(local, radius);
    float aa = max(fwidth(d), 0.0001);
    float edge = max(softness, aa);

    float fillMask = 1.0 - smoothstep(0.0, edge, d);
    float strokeMask = 0.0;
    if (strokeWidth > 0.0) {
        float ringD = abs(d) - strokeWidth;
        strokeMask = 1.0 - smoothstep(0.0, edge, ringD);
    }

    float checker = 1.0;
    if (patternScale > 0.0 && patternStrength > 0.0) {
        vec2 patternUv = local * patternScale * 3.14159265;
        float wave = cos(patternUv.x) * cos(patternUv.y);
        float waveSoft = max(fwidth(wave), 0.08);
        float parity = smoothstep(-waveSoft, waveSoft, wave);
        checker = mix(1.0 - patternStrength, 1.0, parity);
    }

    float alpha = max(fillMask * fillAlpha * checker, strokeMask * strokeAlpha);
    if (alpha <= 0.001) {
        discard;
    }

    fragColor = vec4(v_Color.rgb, v_Color.a * alpha);
}
