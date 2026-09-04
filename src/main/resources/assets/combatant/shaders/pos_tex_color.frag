#version 330 core

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

out vec4 color;

uniform sampler2D u_Texture;

#ifdef COMBATANT_ANALYTIC_CLIP
layout (std140) uniform UIBatch {
    vec4 uScreen; // xy = framebuffer size, zw = logical size
};

#moj_import <combatant:ui_geometry.glsl>
#moj_import <combatant:ui_clip.glsl>
#endif

in vec2 v_TexCoord;
in vec4 v_Color;

void main() {
    vec4 value = texture(u_Texture, v_TexCoord) * v_Color;
#ifdef COMBATANT_ANALYTIC_CLIP
    vec2 logicalScale = max(uScreen.zw, vec2(1.0)) / max(uScreen.xy, vec2(1.0));
    value.a *= combatantClipCoverage(
        combatantClipDistance(combatantLogicalFragCoord()), logicalScale
    );
    if (value.a <= 0.001) discard;
#endif
    color = value;
}
