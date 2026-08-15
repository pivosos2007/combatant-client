#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

in vec2 v_TexCoord;
in vec4 v_Color;
in vec4 v_Params;  // xy = rect size, z = radius, w = shadow blur
in vec4 v_Params2; // x = mode (0 fill, 1 shadow, 2 stroke), y = inner alpha / stroke width

out vec4 fragColor;

float roundedBoxSDF(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

void main() {
    vec2 rectSize = max(v_Params.xy, vec2(0.0001));
    float radius = clamp(v_Params.z, 0.0, min(rectSize.x, rectSize.y) * 0.5);
    float blur = max(v_Params.w, 0.0);
    float padding = blur * 2.0;
    vec2 canvasSize = rectSize + vec2(padding * 2.0);
    vec2 local = v_TexCoord * canvasSize - vec2(padding);
    float distanceToEdge = roundedBoxSDF(local - rectSize * 0.5, rectSize * 0.5, radius);

    float alpha;
    if (v_Params2.x > 1.5) {
        float aa = max(fwidth(distanceToEdge) * 0.75, 0.0001);
        float thickness = max(v_Params2.y, aa * 1.18);
        float outer = clamp(0.5 - distanceToEdge / aa, 0.0, 1.0);
        float inner = clamp(0.5 - (distanceToEdge + thickness) / aa, 0.0, 1.0);
        alpha = clamp(outer - inner, 0.0, 1.0);
    } else if (v_Params2.x > 0.5) {
        float safeBlur = max(blur, 0.0001);
        float outside = exp(-pow(max(distanceToEdge, 0.0) / safeBlur, 2.0) * 1.35);
        float inner = clamp(v_Params2.y, 0.0, 1.0)
                * (1.0 - smoothstep(-safeBlur * 0.55, 0.0, distanceToEdge));
        alpha = max(inner, outside * (1.0 - inner));
    } else {
        float aa = max(fwidth(distanceToEdge) * 0.75, 0.0001);
        alpha = clamp(0.5 - distanceToEdge / aa, 0.0, 1.0);
    }

    if (alpha <= 0.001) discard;
    fragColor = vec4(v_Color.rgb, v_Color.a * alpha);
}
