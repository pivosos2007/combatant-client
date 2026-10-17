#version 330 core
#extension GL_ARB_separate_shader_objects : require

#include <combatant:ui_aa.glsl>
#include <combatant:ui_geometry.glsl>

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout(location = 1) in vec4 v_Local;

layout(location = 2) in vec4 v_Color;
layout(location = 3) in vec4 v_Rect;
layout(location = 4) in vec4 v_Params;

layout(location = 0) out vec4 fragColor;

layout (std140) uniform UIBatch {
    vec4 uScreen; // xy = framebuffer size, zw = logical size
    vec4 uLayer;
};

vec2 warpedLocal(vec4 local) {
    float invW = abs(local.z) > 0.000001 ? local.z : 1.0;
    return local.xy / invW;
}

void main() {
    vec2 logicalScale = uScreen.zw / max(uScreen.xy, vec2(1.0));
    vec2 frag = warpedLocal(v_Local);

    vec2 halfSize = v_Rect.zw * 0.5;
    vec2 center = v_Rect.xy + halfSize;
    float radius = clamp(v_Params.x, 0.0, min(halfSize.x, halfSize.y));
    float softness = max(v_Params.y, 0.0);

    float d = roundedBoxSdf(frag - center, halfSize, radius);
    float a = coverage(d, analyticAa(d, logicalScale, softness));

    fragColor = vec4(v_Color.rgb, v_Color.a * a);
}
