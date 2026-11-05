#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout(location = 0) in vec2 v_TexCoord;
layout(location = 1) flat in vec4 v_Bounds;
layout(location = 2) in vec4 v_Color;
// xy: outline radius in atlas UV; z: opacity; w: MSDF range, -1 for 4x masks, 0 for bitmap.
layout(location = 3) flat in vec4 v_Outline;
layout(location = 0) out vec4 color;
uniform sampler2D u_Texture;

#ifdef COMBATANT_ANALYTIC_CLIP
layout(std140) uniform UIBatch { vec4 uScreen; vec4 uLayer; };
#include <combatant:ui_geometry.glsl>
#include <combatant:ui_clip.glsl>
#endif

vec3 glyphSample(vec2 uv, vec2 texel) {
    if (any(lessThan(uv, v_Bounds.xy)) || any(greaterThan(uv, v_Bounds.zw))) return vec3(0.0);
    return texture(u_Texture, clamp(uv, v_Bounds.xy + texel * 0.5, v_Bounds.zw - texel * 0.5)).rgb;
}

float coverage(vec2 uv, vec2 texel, float range) {
    if (v_Outline.w < 0.0) {
        return (glyphSample(uv + vec2(-texel.x, -texel.y), texel).r
              + glyphSample(uv + vec2( texel.x, -texel.y), texel).r
              + glyphSample(uv + vec2(-texel.x,  texel.y), texel).r
              + glyphSample(uv + vec2( texel.x,  texel.y), texel).r) * 0.25;
    }
    vec3 value = glyphSample(uv, texel);
    if (v_Outline.w == 0.0) return value.r;
    float sd = max(min(value.r, value.g), min(max(value.r, value.g), value.b));
    return clamp(range * (sd - 0.5) + 0.5, 0.0, 1.0);
}

void main() {
    vec2 texel = 1.0 / vec2(textureSize(u_Texture, 0));
    float range = max(0.5 * dot(v_Outline.w * texel, 1.0 / max(fwidth(v_TexCoord), vec2(1e-8))), 1.0);
    float foreground = coverage(v_TexCoord, texel, range) * v_Color.a;
    float opacity = v_Outline.z * v_Color.a;
    vec2 dx = vec2(v_Outline.x, 0.0);
    vec2 dy = vec2(0.0, v_Outline.y);
    float shadow = 1.0 - (1.0 - coverage(v_TexCoord - dx, texel, range) * opacity)
                         * (1.0 - coverage(v_TexCoord + dx, texel, range) * opacity)
                         * (1.0 - coverage(v_TexCoord - dy, texel, range) * opacity)
                         * (1.0 - coverage(v_TexCoord + dy, texel, range) * opacity);
    float alpha = foreground + shadow * (1.0 - foreground);
    vec3 rgb = v_Color.rgb * foreground / max(alpha, 1e-8);
#ifdef COMBATANT_ANALYTIC_CLIP
    vec2 logicalScale = max(uScreen.zw, vec2(1.0)) / max(uScreen.xy, vec2(1.0));
    alpha *= combatantClipCoverage(combatantClipDistance(combatantLogicalFragCoord()), logicalScale);
#endif
    if (alpha <= 0.001) discard;
    color = vec4(rgb, alpha);
}
