#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */

layout(location = 0) in vec2 v_TexCoord;
layout(location = 1) flat in vec4 v_Bounds;
layout(location = 2) in vec4 v_Color;
// xy: effect offset/radius in atlas UV; z: outline opacity (>0) or shadow opacity (<0);
// w: MSDF pxRange, -1 for high-resolution grayscale mask, 0 for bitmap.
layout(location = 3) flat in vec4 v_Outline;
layout(location = 0) out vec4 color;
uniform sampler2D u_Texture;

#ifdef COMBATANT_ANALYTIC_CLIP
layout(std140) uniform UIBatch { vec4 uScreen; vec4 uLayer; };
#include <combatant:ui_geometry.glsl>
#include <combatant:ui_clip.glsl>
#endif

#include <combatant:text_effect_coverage.glsl>

void main() {
    color = textEffectColor(v_TexCoord, v_Bounds, v_Color, v_Outline,
                            dFdx(v_TexCoord), dFdy(v_TexCoord));
#ifdef COMBATANT_ANALYTIC_CLIP
    vec2 logicalScale = max(uScreen.zw, vec2(1.0)) / max(uScreen.xy, vec2(1.0));
    color.a *= combatantClipCoverage(combatantClipDistance(combatantLogicalFragCoord()), logicalScale);
#endif
    if (color.a <= 0.001) discard;
}
