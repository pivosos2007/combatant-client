#version 460 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

#include <sodium:globals.glsl>
#include <sodium:fog.glsl>
#include <sodium:chunk_material.glsl>
#include <minecraft:oit.glsl>

layout(location = 0) in vec4 v_Color;
layout(location = 1) in vec2 v_TexCoord;
layout(location = 2) in vec2 v_FragDistance;
layout(location = 3) in float fadeFactor;
layout(location = 4) in float v_ViewDistance;
layout(location = 5) flat in uint v_CombatantSurfaceFlags;

#ifdef COMBATANT_DEFERRED_GBUFFER
layout(location = 6) in vec4 v_CombatantBaseColor;
layout(location = 7) in vec2 v_CombatantLightCoord;
layout(location = 8) in vec3 v_CombatantViewPosition;
layout(location = 9) in float v_CombatantVertexAo;
layout(location = 10) flat in uint v_CombatantMaterialParams;
layout(location = 11) flat in uint v_CombatantMaterialId;
layout(location = 12) flat in uint v_CombatantMaterialMeta;
layout(location = 13) flat in uint v_CombatantMaterialSurface;
layout(location = 14) flat in vec4 v_CombatantTangent;
#endif

uniform sampler2D u_BlockTex;

#ifdef COMBATANT_DEFERRED_GBUFFER
uniform sampler2D u_CombatantAlbedoAtlas;
uniform sampler2D u_CombatantNormalHeightAtlas;
uniform sampler2D u_CombatantSurfaceAtlas;
uniform sampler2D u_CombatantSpecularAtlas;
#endif

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

#if defined(COMBATANT_DEFERRED_GBUFFER) && !defined(OIT)
layout(location = 1) out vec4 combatantGbufferSurface;
layout(location = 2) out vec4 combatantGbufferGeometry;
layout(location = 3) out vec4 combatantGbufferAuxiliary;
layout(location = 4) out vec4 combatantGbufferMaterial;
layout(location = 5) out uint combatantGbufferMaterialId;
#endif

const uint COMBATANT_SURFACE_SOFT_FADE = 1u << 0u;

vec4 sampleNearest(sampler2D source, vec2 uv, vec2 pixelSize, vec2 du, vec2 dv, vec2 texelScreenSize) {
    vec2 uvTexelCoords = uv / pixelSize;
    vec2 texelCenter = round(uvTexelCoords) - 0.5f;
    vec2 texelOffset = uvTexelCoords - texelCenter;

    texelOffset = (texelOffset - 0.5f) * pixelSize / texelScreenSize + 0.5f;
    texelOffset = clamp(texelOffset, 0.0f, 1.0f);

    uv = (texelCenter + texelOffset) * pixelSize;
    return textureGrad(source, uv, du, dv);
}

vec4 sampleNearest(sampler2D source, vec2 uv, vec2 pixelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);
    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    return sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);
}

vec4 sampleRGSS(sampler2D source, vec2 uv, vec2 pixelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);

    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    float maxTexelSize = max(texelScreenSize.x, texelScreenSize.y);
    float minPixelSize = min(pixelSize.x, pixelSize.y);
    float transitionStart = minPixelSize * 1.0;
    float transitionEnd = minPixelSize * 2.0;
    float blendFactor = smoothstep(transitionStart, transitionEnd, maxTexelSize);

    float duLength = length(du);
    float dvLength = length(dv);
    float minDerivative = min(duLength, dvLength);
    float maxDerivative = max(duLength, dvLength);
    float effectiveDerivative = sqrt(minDerivative * maxDerivative);
    float mipLevelExact = max(0.0, log2(effectiveDerivative / minPixelSize));

    const vec2 offsets[4] = vec2[](
        vec2(0.125, 0.375),
        vec2(-0.125, -0.375),
        vec2(0.375, -0.125),
        vec2(-0.375, 0.125)
    );

    vec4 rgssColor = vec4(0.0);
    for (int i = 0; i < 4; ++i) {
        vec2 sampleUV = uv + offsets[i] * pixelSize;
        rgssColor += textureLod(source, sampleUV, mipLevelExact);
    }
    rgssColor *= 0.25;

    vec4 nearestColor = sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);
    return mix(nearestColor, rgssColor, blendFactor);
}

#ifdef COMBATANT_DEFERRED_GBUFFER
vec2 combatant_encode_octahedral(vec3 normal) {
    normal /= abs(normal.x) + abs(normal.y) + abs(normal.z);
    vec2 encoded = normal.xy;
    if (normal.z < 0.0) {
        encoded = (1.0 - abs(encoded.yx)) * vec2(encoded.x >= 0.0 ? 1.0 : -1.0, encoded.y >= 0.0 ? 1.0 : -1.0);
    }
    return encoded * 0.5 + 0.5;
}

float combatant_encode_distance(float distanceValue) {
    return clamp((log2(1.0 + max(distanceValue, 0.0)) + 1.0) / 17.0, 1.0 / 255.0, 1.0);
}

float combatant_unpack_unorm8(uint packedValue, uint shift) {
    return float((packedValue >> shift) & 255u) / 255.0;
}

bool combatant_has_map(uint mask, uint bit) {
    return (mask & (1u << bit)) != 0u;
}
#endif

void main() {
#if defined(COMBATANT_SHADOW_PASS) && !defined(OIT_ALPHA_ONLY)
#ifdef ALPHA_CUTOUT
    vec4 shadowTexel = texture(u_BlockTex, v_TexCoord) * v_Color;
    if (shadowTexel.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    fragColor = vec4(0.0);
    return;
#endif

    vec4 texel = u_UseRGSS ? sampleRGSS(u_BlockTex, v_TexCoord, u_TexelSize) : sampleNearest(u_BlockTex, v_TexCoord, u_TexelSize);

#ifdef COMBATANT_DEFERRED_GBUFFER
    uint mapMask = (v_CombatantMaterialMeta >> 8u) & 255u;
    if (combatant_has_map(mapMask, 7u)) {
        vec4 overrideAlbedo = texture(u_CombatantAlbedoAtlas, v_TexCoord);
        texel.rgb = overrideAlbedo.rgb;
        texel.a *= overrideAlbedo.a;
    }
#endif

    vec4 color = texel * v_Color;

    if ((v_CombatantSurfaceFlags & COMBATANT_SURFACE_SOFT_FADE) != 0u) {
        float obstructionAlpha = smoothstep(0.18, 0.85, v_ViewDistance);
        color.a *= obstructionAlpha;
    }

#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
#else
#ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(u_FogColor.rgb * color.a, u_FogColor.a);
#else
    vec4 fogColor = u_FogColor;
#endif
    fragColor = _linearFog(color, v_FragDistance, fogColor, u_EnvironmentFog, u_RenderFog, fadeFactor);
#endif

#if defined(COMBATANT_DEFERRED_GBUFFER) && !defined(OIT)
    vec3 geometricNormal = normalize(cross(dFdx(v_CombatantViewPosition), dFdy(v_CombatantViewPosition)));
    if (!gl_FrontFacing) {
        geometricNormal = -geometricNormal;
    }

    vec3 viewNormal = geometricNormal;
    if (combatant_has_map(mapMask, 0u)) {
        vec3 tangentNormal = texture(u_CombatantNormalHeightAtlas, v_TexCoord).rgb * 2.0 - 1.0;
        tangentNormal = normalize(tangentNormal);
        vec3 tangent = normalize(v_CombatantTangent.xyz - geometricNormal * dot(geometricNormal, v_CombatantTangent.xyz));
        vec3 bitangent = normalize(cross(geometricNormal, tangent)) * v_CombatantTangent.w;
        viewNormal = normalize(mat3(tangent, bitangent, geometricNormal) * tangentNormal);
    }

    float vertexAo = clamp(v_CombatantVertexAo, 0.0, 1.0);
    float materialAo = 1.0;
    float roughness = combatant_unpack_unorm8(v_CombatantMaterialSurface, 0u);
    float metallic = combatant_unpack_unorm8(v_CombatantMaterialSurface, 8u);
    float dielectricF0 = combatant_unpack_unorm8(v_CombatantMaterialSurface, 16u);
    float emission = combatant_unpack_unorm8(v_CombatantMaterialSurface, 24u);

    if ((mapMask & 0x3Fu) != 0u) {
        vec4 surfaceSample = texture(u_CombatantSurfaceAtlas, v_TexCoord);
        vec4 specularSample = texture(u_CombatantSpecularAtlas, v_TexCoord);
        if (combatant_has_map(mapMask, 1u)) materialAo = surfaceSample.r;
        if (combatant_has_map(mapMask, 2u)) roughness = surfaceSample.g;
        if (combatant_has_map(mapMask, 3u)) metallic = surfaceSample.b;
        if (combatant_has_map(mapMask, 4u)) dielectricF0 = specularSample.r;
        if (combatant_has_map(mapMask, 5u)) emission = surfaceSample.a;
    }

    vec3 albedo = texel.rgb * v_CombatantBaseColor.rgb;
    float ao = max(1.0 / 255.0, clamp(vertexAo * materialAo, 0.0, 1.0));
    float material = float(v_CombatantMaterialParams & 255u) / 255.0;
    combatantGbufferSurface = vec4(albedo, ao);
    combatantGbufferGeometry = vec4(
        combatant_encode_octahedral(viewNormal),
        clamp(v_CombatantLightCoord, 0.0, 1.0)
    );
    combatantGbufferAuxiliary = vec4(
        combatant_encode_distance(v_FragDistance.y),
        combatant_encode_distance(v_FragDistance.x),
        clamp(fadeFactor, 0.0, 1.0),
        material
    );
    combatantGbufferMaterial = vec4(
        clamp(roughness, 0.0, 1.0),
        clamp(metallic, 0.0, 1.0),
        clamp(dielectricF0, 0.0, 1.0),
        max(emission, 0.0)
    );
    combatantGbufferMaterialId = v_CombatantMaterialId;
#endif
}
