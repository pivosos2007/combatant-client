#version 330

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

#ifdef DISSOLVE
uniform sampler2D DissolveMaskSampler;
#endif

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
in vec4 vertexPerFaceColorBack;
in vec4 vertexPerFaceColorFront;
#else
in vec4 vertexColor;
#endif

#ifndef EMISSIVE
in vec4 lightMapColor;
#endif

#ifndef NO_OVERLAY
in vec4 overlayColor;
#endif

in vec2 texCoord0;
in vec3 combatantModelPos;

out vec4 fragColor;

const ivec3 COMBATANT_DITHER_MARK = ivec3(1, 0, 1);
const ivec3 COMBATANT_A2C_MARK = ivec3(1, 1, 0);

void main() {
    vec4 color = texture(Sampler0, texCoord0);
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

        // Prefer real fragment alpha. The old coverage discard made entities look like
        // punctured meshes instead of a smooth whole-model texture-alpha fade.
        shadeColor.a = fadeAlpha;
    }

    color *= shadeColor * ColorModulator;

#ifndef NO_OVERLAY
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
#endif
#ifndef EMISSIVE
    color *= lightMapColor;
#endif

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
