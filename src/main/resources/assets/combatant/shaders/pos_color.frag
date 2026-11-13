#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on, adapted from, or implemented
 * with reference to Meteor Client
 * (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Licensed under the GNU General Public License v3.0.
 * See THIRD_PARTY_NOTICES.md for details.
 */

#include <combatant:scene_color.glsl>

layout(location = 0) out vec4 color;

layout(location = 2) in vec4 v_Color;

#ifdef COMBATANT_ANALYTIC_CLIP
layout (std140) uniform UIBatch {
    vec4 uScreen;
    vec4 uLayer;
};
#include <combatant:ui_clip.glsl>
#endif

void main() {
    vec4 material = combatantMaterial(v_Color);
    color = material;
#ifdef COMBATANT_ANALYTIC_CLIP
    vec2 logicalScale = uScreen.zw / max(uScreen.xy, vec2(1.0));
    color.a *= combatantClipCoverage(combatantClipDistance(combatantLogicalFragCoord()), logicalScale);
    if (color.a <= 0.001) discard;
#endif
}
