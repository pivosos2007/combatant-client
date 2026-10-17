#version 330
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

#ifdef DISSOLVE
uniform sampler2D DissolveMaskSampler;
#endif

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
layout(location = 2) in vec4 vertexPerFaceColorBack;
layout(location = 3) in vec4 vertexPerFaceColorFront;
#else
layout(location = 2) in vec4 vertexColor;
#endif

#ifndef EMISSIVE
layout(location = 4) in vec4 lightMapColor;
#endif

#ifndef NO_OVERLAY
layout(location = 5) in vec4 overlayColor;
#endif

layout(location = 6) in vec2 texCoord0;
layout(location = 7) in vec3 combatantModelPos;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

const ivec3 COMBATANT_DITHER_MARK = ivec3(1, 0, 1);
const ivec3 COMBATANT_A2C_MARK = ivec3(1, 1, 0);

void main() {
    vec4 color = texture(Sampler0, texCoord0);
#ifdef OIT_ADDITIVE
    color.a = min(0.99, color.a);
#endif
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

#ifdef PER_FACE_LIGHTING
    vec4 shadeColor = gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack;
#else
    vec4 shadeColor = vertexColor;
#endif

#ifdef DISSOLVE
    if (shadeColor.a < texture(DissolveMaskSampler, texCoord0).a) {
        discard;
    }
    // The dissolve effect entirely replaces translucency.
    shadeColor.a = 1.0;
#endif

    int shadeAlphaByte = int(floor(clamp(shadeColor.a, 0.0, 1.0) * 255.0 + 0.5));
    int shadeRByte = int(floor(clamp(shadeColor.r, 0.0, 1.0) * 255.0 + 0.5));
    int shadeGByte = int(floor(clamp(shadeColor.g, 0.0, 1.0) * 255.0 + 0.5));
    int shadeBByte = int(floor(clamp(shadeColor.b, 0.0, 1.0) * 255.0 + 0.5));
    ivec3 mark = ivec3(shadeRByte & 1, shadeGByte & 1, shadeBByte & 1);
    bool combatantDitherFade = shadeAlphaByte < 255 && all(equal(mark, COMBATANT_DITHER_MARK));
    bool combatantA2CFade = shadeAlphaByte < 255 && all(equal(mark, COMBATANT_A2C_MARK));

    if (combatantDitherFade || combatantA2CFade) {
        float fadeAlpha = float(shadeAlphaByte) / 255.0;
        shadeColor.r = float(shadeRByte & 254) / 255.0;
        shadeColor.g = float(shadeGByte & 254) / 255.0;
        shadeColor.b = float(shadeBByte & 254) / 255.0;

        // Use fragment alpha for a continuous whole-model fade.
        shadeColor.a = fadeAlpha;
    }

    color *= shadeColor * ColorModulator;

#if !defined(NO_OVERLAY) && !defined(OIT_ALPHA_ONLY)
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
#endif
#if !defined(EMISSIVE) && !defined(OIT_ALPHA_ONLY)
    color *= lightMapColor;
#endif

#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
#else
#ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
#else
    vec4 fogColor = FogColor;
#endif
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd,
            FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
#endif
}
