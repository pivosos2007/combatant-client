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

out vec4 fragColor;

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
    float thickness = max(0.0, v_Params.z);

    float d = roundedBoxSdf(frag - center, halfSize, radius);
    float stroke = min(thickness, min(halfSize.x, halfSize.y));

    float a = innerStrokeCoverage(d, stroke, logicalScale, softness);

    fragColor = vec4(v_Color.rgb, v_Color.a * a);
}
