#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

#include <combatant:scene_color.glsl>

layout(location = 0) out vec4 color;

uniform sampler2D u_Texture;

layout (std140) uniform MsdfText {
    vec4 u_Msdf; // x = pxRange, y = atlasWidth, z = atlasHeight
};

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

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

float glyphCoverage(vec2 uv, float screenPxRange, vec2 uvDx, vec2 uvDy) {
    // Explicit gradients also make sampling well-defined inside the adaptive
    // branch (texture()'s implicit derivatives are unsafe in divergent flow).
    vec3 sampleValue = textureGrad(u_Texture, uv, uvDx, uvDy).rgb;
    float signedDistance = median(sampleValue.r, sampleValue.g, sampleValue.b) - 0.5;
    return clamp(screenPxRange * signedDistance + 0.5, 0.0, 1.0);
}

void main() {
    vec4 material = combatantMaterial(v_Color);
    vec2 atlasSize = max(u_Msdf.yz, vec2(1.0));
    vec2 unitRange = vec2(max(u_Msdf.x, 0.0001)) / atlasSize;
    vec2 uvDx = dFdx(v_TexCoord);
    vec2 uvDy = dFdy(v_TexCoord);
    vec2 uvFootprint = max(abs(uvDx) + abs(uvDy), vec2(1e-7));
    vec2 screenTexSize = vec2(1.0) / uvFootprint;
    float screenPxRange = max(0.5 * dot(unitRange, screenTexSize), 1.0);

    float centerCoverage = glyphCoverage(v_TexCoord, screenPxRange, uvDx, uvDy);
    float alpha = centerCoverage;
    // A small screenPxRange implies too few atlas samples per output pixel.
    // Resolve small MSDF text at four subpixel locations to suppress temporal
    // aliasing during continuous motion. Blend this mode in/out smoothly so
    // resizing text cannot introduce an abrupt filter switch.
    float resolveWeight = 1.0 - smoothstep(1.75, 2.50, screenPxRange);
    if (resolveWeight > 0.001) {
        vec2 subpixelX = 0.25 * uvDx;
        vec2 subpixelY = 0.25 * uvDy;
        float resolved = (glyphCoverage(v_TexCoord - subpixelX - subpixelY, screenPxRange, uvDx, uvDy)
                        + glyphCoverage(v_TexCoord + subpixelX - subpixelY, screenPxRange, uvDx, uvDy)
                        + glyphCoverage(v_TexCoord - subpixelX + subpixelY, screenPxRange, uvDx, uvDy)
                        + glyphCoverage(v_TexCoord + subpixelX + subpixelY, screenPxRange, uvDx, uvDy)) * 0.25;
        alpha = mix(centerCoverage, resolved, resolveWeight);
    }
#ifdef COMBATANT_ANALYTIC_CLIP
    vec2 logicalScale = max(uScreen.zw, vec2(1.0)) / max(uScreen.xy, vec2(1.0));
    alpha *= combatantClipCoverage(
        combatantClipDistance(combatantLogicalFragCoord()), logicalScale
    );
    if (material.a * alpha <= 0.001) discard;
#endif
    color = vec4(material.rgb, material.a * alpha);
}
