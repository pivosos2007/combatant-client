#version 330 core

#moj_import <combatant:ui_aa.glsl>
#moj_import <combatant:ui_geometry.glsl>
#moj_import <combatant:ui_stroke.glsl>

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
in vec4 v_Params2;

out vec4 fragColor;

layout (std140) uniform UIBatch {
    vec4 uScreen; // xy = framebuffer size, zw = logical size
};

const float PI = 3.141592653589793;
const float TAU = 6.283185307179586;

vec2 warpedLocal(vec4 local) {
    float invW = abs(local.z) > 0.000001 ? local.z : 1.0;
    return local.xy / invW;
}

float wrap01(float v) {
    return fract(v);
}

float circularDistance(float a, float b) {
    float d = abs(fract(a - b + 0.5) - 0.5);
    return d;
}

void main() {
    vec2 logicalScale = uScreen.zw / max(uScreen.xy, vec2(1.0));
    vec2 frag = warpedLocal(v_Local);

    vec2 halfSize = v_Rect.zw * 0.5;
    vec2 center = v_Rect.xy + halfSize;
    vec2 p = frag - center;

    float radius = clamp(v_Params.x, 0.0, min(halfSize.x, halfSize.y));
    float softness = max(v_Params.y, 0.0);
    float thickness = max(0.0, v_Params.z);
    float offset = wrap01(v_Params.w);

    float d = roundedBoxSdf(p, halfSize, radius);
    float stroke = min(thickness, min(halfSize.x, halfSize.y));

    float a = innerStrokeCoverage(d, stroke, logicalScale, softness);

    float phase = wrap01((atan(p.y, p.x) + PI) / TAU);
    float head = 1.0 - smoothstep(0.0, 0.145, circularDistance(phase, offset));
    float tail = 1.0 - smoothstep(0.02, 0.46, circularDistance(phase, wrap01(offset - 0.20)));

    vec3 start = v_Color.rgb;
    vec3 end = v_Params2.rgb;
    vec3 dim = mix(start * 0.58, vec3(0.0), 0.10);
    vec3 body = mix(dim, start, 0.45 + 0.42 * tail);
    vec3 hot = mix(end, vec3(1.0), 0.30);
    vec3 color = mix(body, hot, head);

    float alpha = mix(v_Color.a * 0.58, max(v_Color.a, v_Params2.a), max(head, tail * 0.52));
    fragColor = vec4(color, alpha * a);
}
