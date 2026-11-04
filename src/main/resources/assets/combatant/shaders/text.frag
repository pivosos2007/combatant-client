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
    vec2 uvDx = dFdx(v_TexCoord);
    vec2 uvDy = dFdy(v_TexCoord);
    vec2 atlasSize = vec2(textureSize(u_Texture, 0));
    float texelsPerPixel = max(length(uvDx * atlasSize), length(uvDy * atlasSize));

    // A single bilinear fetch only integrates roughly one atlas texel. When
    // bitmap glyphs are minified, cover the framebuffer pixel with four taps
    // instead; fade in the resolve continuously to avoid scaling thresholds.
    // Magnified glyphs retain their original single bilinear fetch.
    float coverage = textureGrad(u_Texture, v_TexCoord, uvDx, uvDy).r;
    float resolveWeight = smoothstep(1.0, 1.75, texelsPerPixel);
    if (resolveWeight > 0.001) {
        vec2 subpixelX = 0.25 * uvDx;
        vec2 subpixelY = 0.25 * uvDy;
        float resolved = (textureGrad(u_Texture, v_TexCoord - subpixelX - subpixelY, uvDx, uvDy).r
                        + textureGrad(u_Texture, v_TexCoord + subpixelX - subpixelY, uvDx, uvDy).r
                        + textureGrad(u_Texture, v_TexCoord - subpixelX + subpixelY, uvDx, uvDy).r
                        + textureGrad(u_Texture, v_TexCoord + subpixelX + subpixelY, uvDx, uvDy).r) * 0.25;
        coverage = mix(coverage, resolved, resolveWeight);
    }
    float alpha = coverage * v_Color.a;
#ifdef COMBATANT_ANALYTIC_CLIP
    vec2 logicalScale = max(uScreen.zw, vec2(1.0)) / max(uScreen.xy, vec2(1.0));
    alpha *= combatantClipCoverage(
        combatantClipDistance(combatantLogicalFragCoord()), logicalScale
    );
    if (alpha <= 0.001) discard;
#endif
    color = vec4(v_Color.rgb, alpha);
}
