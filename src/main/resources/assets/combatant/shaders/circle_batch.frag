#version 330 core

#moj_import <combatant:ui_aa.glsl>
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

out vec4 fragColor;

layout (std140) uniform UIBatch {
    vec4 uScreen; // xy = framebuffer size, zw = logical size
    vec4 uLayer;
};

float circleSDF(vec2 p, float r) {
    return length(p) - r;
}

vec2 warpedLocal(vec4 local) {
    float invW = abs(local.z) > 0.000001 ? local.z : 1.0;
    return local.xy / invW;
}

void main() {
    vec2 logicalScale = uScreen.zw / max(uScreen.xy, vec2(1.0));
    vec2 frag = warpedLocal(v_Local);

    vec2 halfSize = v_Rect.zw * 0.5;
    vec2 center = v_Rect.xy + halfSize;
    float radius = v_Params.x;
    float softness = max(v_Params.y, 0.0);
    float thickness = v_Params.z;
    float d = circleSDF(frag - center, radius);
    float a = thickness > 0.0
            ? centeredStrokeCoverage(d, thickness, logicalScale, softness)
            : coverage(d, analyticAa(d, logicalScale, softness));

    fragColor = vec4(v_Color.rgb, v_Color.a * a);
}
