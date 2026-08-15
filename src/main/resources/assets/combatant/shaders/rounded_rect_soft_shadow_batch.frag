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
in vec4 v_Params;

out vec4 fragColor;

layout (std140) uniform UIBatch {
    vec4 uScreen; // xy = framebuffer size, zw = logical size
};

float roundedBoxSDF(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float squircleSDF(vec2 p, vec2 halfSize, float exponent) {
    vec2 h = max(halfSize, vec2(0.0001));
    float n = clamp(exponent, 2.0, 16.0);
    vec2 q = abs(p) / h;
    vec2 qn = pow(q, vec2(n));
    float implicit = qn.x + qn.y - 1.0;
    vec2 gradient = n * vec2(
        pow(max(q.x, 0.000001), n - 1.0) / h.x,
        pow(max(q.y, 0.000001), n - 1.0) / h.y
    );
    float radial = (pow(max(qn.x + qn.y, 0.000001), 1.0 / n) - 1.0) * min(h.x, h.y);
    return length(gradient) > 0.00001 ? implicit / length(gradient) : radial;
}


vec2 warpedLocal(vec4 local) {
    float invW = abs(local.z) > 0.000001 ? local.z : 1.0;
    return local.xy / invW;
}

void main() {
    vec2 logicalScale = uScreen.zw / uScreen.xy;
    vec2 frag = warpedLocal(v_Local);

    vec2 halfSize = v_Rect.zw * 0.5;
    vec2 center = v_Rect.xy + halfSize;
    bool squircle = v_Params.x < 0.0;
    float radius = min(max(v_Params.x, 0.0), min(halfSize.x, halfSize.y));
    float blur = max(v_Params.y, 0.0001);
    float innerAlpha = clamp(v_Params.z, 0.0, 1.0);

    float d = squircle
            ? squircleSDF(frag - center, halfSize, -v_Params.x)
            : roundedBoxSDF(frag - center, halfSize, radius);

    float outside = exp(-pow(max(d, 0.0) / blur, 2.0) * 1.35);
    float inside = innerAlpha * (1.0 - smoothstep(-blur * 0.55, 0.0, d));
    float alpha = max(inside, outside * (1.0 - inside));

    fragColor = vec4(v_Color.rgb, v_Color.a * alpha);
}
