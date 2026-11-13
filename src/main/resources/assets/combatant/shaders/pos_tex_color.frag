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
    vec4 texel = texture(u_Texture, v_TexCoord);
    vec4 value = texel * v_Color;
#ifdef COMBATANT_SCENE_LINEAR_COLOR
#ifdef COMBATANT_HDR_PREMULTIPLIED_COLOR
    // Recover authored color before converting the atlas/tint opacity contract.
    vec3 authored = (texel.rgb / max(texel.a, 1e-8)) * (v_Color.rgb / max(v_Color.a, 1e-8));
    vec4 material = combatantMaterial(vec4(authored, v_Color.a));
    value = vec4(material.rgb * (texel.a * material.a), texel.a * material.a);
#elif defined(COMBATANT_HDR_DISPLAY_OPACITY)
    vec4 material = combatantMaterial(vec4(texel.rgb * v_Color.rgb, v_Color.a));
    value = vec4(material.rgb, texel.a * material.a);
#else
    value.rgb = combatantMaterialColor(texel.rgb) * combatantMaterialColor(v_Color.rgb);
#endif
#endif
#ifdef COMBATANT_ANALYTIC_CLIP
    vec2 logicalScale = max(uScreen.zw, vec2(1.0)) / max(uScreen.xy, vec2(1.0));
    value.a *= combatantClipCoverage(
        combatantClipDistance(combatantLogicalFragCoord()), logicalScale
    );
    if (value.a <= 0.001) discard;
#endif
    color = value;
}
