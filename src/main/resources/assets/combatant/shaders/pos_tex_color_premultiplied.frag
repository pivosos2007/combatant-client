#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout(location = 0) out vec4 color;

uniform sampler2D u_Texture;

#ifdef COMBATANT_ANALYTIC_CLIP
layout (std140) uniform UIBatch {
    vec4 uScreen; // xy = framebuffer size, zw = logical size
    vec4 uLayer;
};

#include <combatant:ui_geometry.glsl>
#include <combatant:ui_clip.glsl>
#endif

layout(location = 0) in vec2 v_TexCoord;
layout(location = 2) in vec4 v_Color;

void main() {
    vec4 value = texture(u_Texture, v_TexCoord) * v_Color;
#ifdef COMBATANT_ANALYTIC_CLIP
    vec2 logicalScale = max(uScreen.zw, vec2(1.0)) / max(uScreen.xy, vec2(1.0));
    float coverage = combatantClipCoverage(
        combatantClipDistance(combatantLogicalFragCoord()), logicalScale
    );
    // This material is submitted with premultiplied-alpha blending, so RGB must follow alpha.
    value *= coverage;
    if (value.a <= 0.001) discard;
#endif
    color = value;
}
