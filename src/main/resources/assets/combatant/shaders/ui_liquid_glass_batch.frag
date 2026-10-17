#version 330 core
#extension GL_ARB_separate_shader_objects : require

#include <combatant:ui_geometry.glsl>

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 *
 * The LIQUID_REFRACTION optical model adapts the SDF deformation, edge-factor and
 * refraction direction used by liquidGL 3.0.0 (commit 88f681ab7035fd55b04f63edff1841e32c4199e9),
 * Copyright (c) NaughtyDuk, MIT. See THIRD_PARTY_LICENSES/liquidGL-MIT.txt.
 */

layout(location = 0) in vec2 v_TexCoord;
layout(location = 1) in vec4 v_Local;
layout(location = 2) in vec4 v_Color;
layout(location = 3) in vec4 v_Rect;
layout(location = 4) in vec4 v_Params;
layout(location = 5) in vec4 v_Params2;
layout(location = 6) in vec4 v_Params3;
layout(location = 7) in vec4 v_Params4;
layout(location = 8) in vec4 v_Params5;
layout(location = 9) flat in vec4 v_Params6;
layout(location = 10) in vec4 v_Params7;
layout(location = 11) in vec4 v_Params8;
layout(location = 12) in vec4 v_Params9;
layout(location = 13) in vec4 v_Params10;
layout(location = 14) in vec4 v_Params11;

layout(location = 0) out vec4 fragColor;

uniform sampler2D u_Texture;
uniform sampler2D u_BlurTexture;
uniform sampler2D u_StackTexture;

#ifdef COMBATANT_UI_UNDERLAY
uniform sampler2D u_UiUnderlayTexture;
layout (std140) uniform UIBackdrop {
    vec4 uUiBackdrop;
};
#endif

layout (std140) uniform UIBatch {
    vec4 uScreen;
    vec4 uLayer;
};

layout (std140) uniform UIBlend {
    vec4 uBlendParams;
    vec4 uBlendTone0;
    vec4 uBlendTone1;
};

layout (std140) uniform UIGlassFrame {
    vec4 uGlassPointer;
    vec4 uGlassMotion;
    vec4 uGlassClock;
};

layout (std140) uniform UIGlassStack {
    vec4 uStackMeta;
    vec4 uStackBounds[4];
};

float stackRegionMask(vec2 sampleUv, vec2 logicalSize) {
    if (uStackMeta.x < 0.5) return 0.0;
    vec2 logicalPos = vec2(sampleUv.x, 1.0 - sampleUv.y) * logicalSize;
    float coverage = 0.0;
    for (int i = 0; i < 4; ++i) {
        if (float(i) >= uStackMeta.x) break;
        vec4 rect = uStackBounds[i];
        vec2 edge = min(logicalPos - rect.xy, rect.xy + rect.zw - logicalPos);
        float signedInside = min(edge.x, edge.y);
        float aa = max(uStackMeta.y, 0.75);
        coverage = max(coverage, smoothstep(-aa, aa, signedInside));
    }
    return coverage;
}

vec4 sampleLiquidScene(vec2 sampleUv, vec2 logicalSize) {
    vec2 safeUv = clamp(sampleUv, vec2(0.001), vec2(0.999));
    vec4 background = texture(u_BlurTexture, safeUv);
    float region = stackRegionMask(safeUv, logicalSize);
    if (region <= 0.001) return background;
    vec4 lower = texture(u_StackTexture, safeUv) * region;
    return lower + background * (1.0 - lower.a);
}

#include <combatant:ui_backdrop_blend.glsl>

#ifdef COMBATANT_ANALYTIC_CLIP
#include <combatant:ui_clip.glsl>
#endif

float roundedBoxSDF(vec2 p, vec2 halfSize, vec4 r, float smoothness) {
    float radius;
    if (p.x < 0.0) {
        radius = (p.y < 0.0) ? r.x : r.w;
    } else {
        radius = (p.y < 0.0) ? r.y : r.z;
    }

    vec2 q = abs(p) - halfSize + radius;
    vec2 qc = max(q, 0.0);

    float k = max(smoothness, 1.0001);
    float len = pow(pow(qc.x, k) + pow(qc.y, k), 1.0 / k);

    return min(max(q.x, q.y), 0.0) + len - radius;
}

float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float decodeDistort(float packedValue, out float cornerSmoothness, out float blurAlpha, out bool squircle) {
    squircle = packedValue >= 200000.0;
    if (squircle) {
        packedValue -= 200000.0;
        float exponentBucket = floor(packedValue / 128.0 + 1e-4);
        cornerSmoothness = clamp(exponentBucket / 100.0, 2.0, 16.0);
        float rest = packedValue - exponentBucket * 128.0;
        float blurBucket = floor(rest + 1e-4);
        blurAlpha = clamp(blurBucket / 100.0, 0.0, 1.0);
        return clamp(rest - blurBucket, 0.0, 0.35);
    }
    if (packedValue >= 10000.0) {
        cornerSmoothness = floor(packedValue / 10000.0 + 1e-4);
        float rest = packedValue - cornerSmoothness * 10000.0;
        float blurBucket = floor(rest + 1e-4);
        blurAlpha = clamp(blurBucket / 100.0, 0.0, 1.0);
        cornerSmoothness = max(cornerSmoothness, 1.0001);
        return clamp(rest - blurBucket, 0.0, 0.35);
    }

    float distort = packedValue;
    cornerSmoothness = 2.0;
    blurAlpha = 1.0;

    if (packedValue >= 1.5) {
        cornerSmoothness = floor(packedValue + 1e-4);
        distort = packedValue - cornerSmoothness;
    }

    cornerSmoothness = max(cornerSmoothness, 1.0001);
    return clamp(distort, 0.0, 0.35);
}

float squircleSDF(vec2 p, vec2 halfSize, float exponent) {
    vec2 h = max(halfSize, vec2(0.0001));
    float n = clamp(exponent, 2.0, 16.0);
    vec2 q = abs(p) / h;
    vec2 qn = pow(q, vec2(n));
    float implicit = qn.x + qn.y - 1.0;
    vec2 gradient = n * vec2(
        pow(max(q.x, 0.000001), n - 1.0) / h.x,
        pow(max(q.y, 0.000001), n - 1.0) / h.y
    );
    float radial = (pow(max(qn.x + qn.y, 0.000001), 1.0 / n) - 1.0) * min(h.x, h.y);
    return length(gradient) > 0.00001 ? implicit / length(gradient) : radial;
}

vec2 primitivePoint(int index) {
    if (index == 0) return v_Params.xy;
    if (index == 1) return v_Params.zw;
    if (index == 2) return v_Params3.xy;
    if (index == 3) return v_Params3.zw;
    if (index == 4) return v_Params4.xy;
    if (index == 5) return v_Params4.zw;
    if (index == 6) return v_Params5.xy;
    return v_Params5.zw;
}

float smoothMaximum(float a, float b, float radius) {
    if (radius <= 0.0001) return max(a, b);
    float h = clamp(0.5 + 0.5 * (a - b) / radius, 0.0, 1.0);
    return mix(b, a, h) + radius * h * (1.0 - h);
}

float smoothMinimum(float a, float b, float radius) {
    if (radius <= 0.0001) return min(a, b);
    float h = clamp(0.5 + 0.5 * (b - a) / radius, 0.0, 1.0);
    return mix(b, a, h) - radius * h * (1.0 - h);
}

vec4 compoundSource(int index) {
    if (index == 0) return v_Params;
    if (index == 1) return v_Params3;
    if (index == 2) return v_Params4;
    return v_Params5;
}

float compoundCircleSdf(vec2 p, vec3 source) {
    return length(p - source.xy) - max(source.z, 0.0);
}

float compoundRoundedRectSdf(vec2 p, vec4 rect, float radius) {
    vec2 center = rect.xy + rect.zw * 0.5;
    vec2 halfSize = max(rect.zw * 0.5, vec2(0.0001));
    float r = clamp(radius, 0.0, min(halfSize.x, halfSize.y));
    vec2 q = abs(p - center) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float compoundSquircleSdf(vec2 p, vec4 rect, float exponent) {
    vec2 center = rect.xy + rect.zw * 0.5;
    vec2 halfSize = max(rect.zw * 0.5, vec2(0.0001));
    float n = clamp(exponent, 2.0, 16.0);
    vec2 q = abs(p - center) / halfSize;
    vec2 qn = pow(q, vec2(n));
    float implicit = qn.x + qn.y - 1.0;
    vec2 gradient = n * vec2(
        pow(max(q.x, 0.000001), n - 1.0) / halfSize.x,
        pow(max(q.y, 0.000001), n - 1.0) / halfSize.y
    );
    float radial = (pow(max(qn.x + qn.y, 0.000001), 1.0 / n) - 1.0) * min(halfSize.x, halfSize.y);
    return length(gradient) > 0.00001 ? implicit / length(gradient) : radial;
}

float islandBlobSDF(vec2 localPos) {
    int count = int(clamp(floor(v_Params6.x + 0.5), 1.0, 4.0));
    float smoothing = max(v_Params6.y, 0.0);
    float d = compoundCircleSdf(localPos, compoundSource(0).xyz);
    for (int i = 1; i < 4; i++) {
        if (i >= count) break;
        d = smoothMinimum(d, compoundCircleSdf(localPos, compoundSource(i).xyz), smoothing);
    }
    return d;
}

float smoothBoxUnionSDF(vec2 localPos) {
    float smoothing = max(v_Params6.y, 0.0);
    float first = compoundRoundedRectSdf(localPos, v_Params, max(v_Params4.x, 0.0));
    float second = compoundRoundedRectSdf(localPos, v_Params3, max(v_Params4.y, 0.0));
    return smoothMinimum(first, second, smoothing);
}

float smoothSquircleUnionSDF(vec2 localPos) {
    float smoothing = max(v_Params6.y, 0.0);
    float first = compoundSquircleSdf(localPos, v_Params, max(v_Params4.x, 2.0));
    float second = compoundSquircleSdf(localPos, v_Params3, max(v_Params4.y, 2.0));
    return smoothMinimum(first, second, smoothing);
}

float primitiveSDF(vec2 localPos) {
    int count = int(clamp(floor(v_Params6.x + 0.5), 3.0, 8.0));
    float rounding = max(0.0, v_Params6.y);
    float d = -1.0e20;
    for (int i = 0; i < 8; i++) {
        if (i >= count) break;
        int next = i + 1;
        if (next >= count) next = 0;
        vec2 a = primitivePoint(i);
        vec2 b = primitivePoint(next);
        vec2 edge = b - a;
        float edgeLength = max(length(edge), 0.0001);
        float edgeDistance = -(edge.x * (localPos.y - a.y) - edge.y * (localPos.x - a.x)) / edgeLength;
        d = i == 0 ? edgeDistance : smoothMaximum(d, edgeDistance, rounding);
    }
    return d;
}

float glassShapeSDF(vec2 p, vec2 halfSize, vec4 radius, float exponent, bool squircle) {
    int shapeMode = int(floor(v_Params6.w + 0.5));
    vec2 localPos = p + v_Rect.zw * 0.5;
    if (shapeMode == 1) return primitiveSDF(localPos);
    if (shapeMode == 2) return islandBlobSDF(localPos);
    if (shapeMode == 3) return smoothBoxUnionSDF(localPos);
    if (shapeMode == 4) return smoothSquircleUnionSDF(localPos);
    return squircle ? squircleSDF(p, halfSize, exponent) : roundedBoxSDF(p, halfSize, radius, exponent);
}

vec2 safeNormalize(vec2 v, vec2 fallback) {
    float len2 = dot(v, v);
    if (len2 <= 1e-6) return fallback;
    return v * inversesqrt(len2);
}

vec2 cornerNormal(vec2 p, vec2 b, float r, vec2 fallback) {
    vec2 q = abs(p) - b + r;
    vec2 m = max(q, 0.0);
    float l = length(m);
    if (l <= 0.0) return fallback;
    float w = smoothstep(0.0, max(r * 0.5, 1.0), min(m.x, m.y));
    if (w <= 0.0) return fallback;
    vec2 s = vec2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
    return normalize(mix(fallback, s * (m / l), w));
}

vec2 warpedLocal(vec4 local) {
    float invW = abs(local.z) > 0.000001 ? local.z : 1.0;
    return local.xy / invW;
}

vec2 deformGlassPoint(vec2 point, vec2 size, vec2 pointerCenter,
                      float interactionRadius, vec2 displacement) {
    if (interactionRadius <= 0.0 || dot(displacement, displacement) <= 1e-8) return point;
    vec2 interactionCenter = (pointerCenter - 0.5) * size;
    float reach = max(interactionRadius * min(size.x, size.y), 0.0001);
    float influence = 1.0 - smoothstep(0.0, reach, length(point - interactionCenter));
    return point - displacement * size * influence;
}

void main() {
    vec4 screen = uScreen;
    vec2 fbSize = max(screen.xy, vec2(1.0));
    vec2 logicalSize = max(screen.zw, vec2(1.0));
    vec2 logicalScale = logicalSize / fbSize;

    vec2 frag = warpedLocal(v_Local);

    vec2 size = max(v_Rect.zw, vec2(1.0));
    vec2 center = v_Rect.xy + size * 0.5;
    vec2 originalPos = frag - center;
    int glassVariant = int(floor(v_Params8.x + 0.5));
    bool localInteraction = v_Params11.w < 0.0;
    float magnification = clamp(abs(v_Params11.w), 0.65, 1.8);
    uint opticalBits = uint(v_Params6.z);
    float spectralAberration = clamp(v_Params7.x, 0.0, 0.95);
    int chromaticMotion = int(opticalBits & 3u);
    vec2 opticalTiltDeg = vec2(float((opticalBits >> 8u) & 127u) - 64.0,
                               float((opticalBits >> 15u) & 127u) - 64.0) * 0.25;
    bool shapeDeformation = ((opticalBits >> 22u) & 1u) != 0u;
    bool directionalSpecular = ((opticalBits >> 23u) & 1u) != 0u;
    float interactionRadius = clamp(v_Params9.y, 0.0, 2.0);
    vec2 interactionDisplacement = localInteraction ? v_Params10.zw : vec2(0.0);
    vec4 radius = normalizeRadii(v_Params, size);

    float cornerSmoothness;
    float blurAlpha;
    bool squircle;
    float distortStrength = decodeDistort(max(v_TexCoord.y, 0.0), cornerSmoothness, blurAlpha, squircle);
    bool customShape = v_Params6.w > 0.5;
    vec2 halfSize = size * 0.5 - ((squircle || customShape) ? 0.0 : 1.0);

    vec2 deformedPos = localInteraction
            ? deformGlassPoint(originalPos, size, v_Params10.xy,
                               interactionRadius, interactionDisplacement)
            : originalPos;
    vec2 pos = shapeDeformation ? deformedPos : originalPos;

    float selfDistance = glassShapeSDF(pos, halfSize, radius, cornerSmoothness, squircle);
#ifdef COMBATANT_ANALYTIC_CLIP
    vec2 clipPosition = combatantLogicalFragCoord();
    float clipDistance = combatantClipDistance(clipPosition);
    float d = max(selfDistance, clipDistance);
#else
    float d = selfDistance;
#endif

    vec2 dScreenGradient = vec2(
        dFdx(selfDistance) / max(logicalScale.x, 1e-6),
        dFdy(selfDistance) / max(logicalScale.y, 1e-6)
    );
    vec2 fallbackSdfNormal = safeNormalize(pos, vec2(0.0, -1.0));
    vec2 uvNormal = safeNormalize(dScreenGradient,
            vec2(fallbackSdfNormal.x, -fallbackSdfNormal.y));
    vec2 sdfNormal = vec2(uvNormal.x, -uvNormal.y);

    float aa = max(max(logicalScale.x, logicalScale.y) * 1.35, 0.75);
    float shapeAlpha = 1.0 - smoothstep(-aa * 0.5, aa, d);
    if (shapeAlpha <= 0.001) {
        discard;
    }

    float localRadius = pos.x < 0.0
            ? (pos.y < 0.0 ? radius.x : radius.w)
            : (pos.y < 0.0 ? radius.y : radius.z);
    float opticalInsideDistance = (!customShape && !squircle)
            ? -sdRoundBox(pos, size * 0.5, localRadius)
            : max(-selfDistance, 0.0);
    float liquidBevelPx = max(v_Params8.z * min(size.x, size.y), 0.001);
    if (glassVariant == 4) liquidBevelPx *= 1.65;
    float liquidEdge = 1.0 - smoothstep(0.0, liquidBevelPx, opticalInsideDistance);

    vec2 uv = vec2(
        frag.x / logicalSize.x,
        1.0 - frag.y / logicalSize.y
    );

    vec2 centerUv;
    vec2 liquidOffset = vec2(0.0);
    vec2 p = pos / max(size.y, 1.0);
    vec2 refractNormal = (!customShape && !squircle)
            ? cornerNormal(pos, size * 0.5, localRadius,
                    safeNormalize(p + vec2(0.000001), sdfNormal))
            : sdfNormal;
    if (localInteraction && interactionRadius > 0.0
            && dot(interactionDisplacement, interactionDisplacement) > 1e-8) {
        vec2 stepX = vec2(0.5, 0.0);
        vec2 stepY = vec2(0.0, 0.5);
        vec2 dxp = deformGlassPoint(originalPos + stepX, size, v_Params10.xy,
                                    interactionRadius, interactionDisplacement);
        vec2 dxm = deformGlassPoint(originalPos - stepX, size, v_Params10.xy,
                                    interactionRadius, interactionDisplacement);
        vec2 dyp = deformGlassPoint(originalPos + stepY, size, v_Params10.xy,
                                    interactionRadius, interactionDisplacement);
        vec2 dym = deformGlassPoint(originalPos - stepY, size, v_Params10.xy,
                                    interactionRadius, interactionDisplacement);
        float dxpDistance = (!customShape && !squircle)
                ? sdRoundBox(dxp, size * 0.5, localRadius)
                : glassShapeSDF(dxp, halfSize, radius, cornerSmoothness, squircle);
        float dxmDistance = (!customShape && !squircle)
                ? sdRoundBox(dxm, size * 0.5, localRadius)
                : glassShapeSDF(dxm, halfSize, radius, cornerSmoothness, squircle);
        float dypDistance = (!customShape && !squircle)
                ? sdRoundBox(dyp, size * 0.5, localRadius)
                : glassShapeSDF(dyp, halfSize, radius, cornerSmoothness, squircle);
        float dymDistance = (!customShape && !squircle)
                ? sdRoundBox(dym, size * 0.5, localRadius)
                : glassShapeSDF(dym, halfSize, radius, cornerSmoothness, squircle);
        vec2 gradient = vec2(dxpDistance - dxmDistance, dypDistance - dymDistance);
        if (dot(gradient, gradient) > 1e-8) {
            refractNormal = normalize(mix(refractNormal, normalize(gradient),
                    min(length(deformedPos - originalPos), 1.0)));
        }
    }

    float offsetAmount = liquidEdge * v_Params8.y
            + pow(liquidEdge, 10.0) * v_Params8.w;
    float centerBlend = smoothstep(0.15, 0.45, length(p));
    liquidOffset = vec2(refractNormal.x, -refractNormal.y)
            * offsetAmount * centerBlend;

    vec2 lensUv = (frag - v_Rect.xy) / size;
    vec2 localUv = (lensUv - 0.5) / magnification + 0.5;
    vec2 mappedPosition = v_Rect.xy + localUv * size;
    centerUv = vec2(mappedPosition.x / logicalSize.x,
                    1.0 - mappedPosition.y / logicalSize.y) + liquidOffset;

    vec2 tiltOffset = vec2(tan(radians(opticalTiltDeg.y)),
                           -tan(radians(opticalTiltDeg.x))) * 0.05;
    centerUv = clamp(centerUv - tiltOffset, vec2(0.001), vec2(0.999));

    float edgeFocus = smoothstep(0.16, 0.96, liquidEdge);
    vec2 refractAxis = safeNormalize(vec2(refractNormal.x, -refractNormal.y), vec2(1.0, 0.0));
    float minLogicalScale = max(min(logicalScale.x, logicalScale.y), 1e-6);
    float bevelWidthFramebufferPx = liquidBevelPx / minLogicalScale;
    float lensSplitLimitPx = min(size.x / max(logicalScale.x, 1e-6),
                                      size.y / max(logicalScale.y, 1e-6)) * 0.42;
    float splitLimitPx = min(bevelWidthFramebufferPx * 0.5, lensSplitLimitPx);
    vec2 rawChroma = liquidOffset * spectralAberration;
    float rawStaticSplitPx = length(rawChroma * fbSize);
    float resolvedStaticSplitPx = splitLimitPx
            * tanh(rawStaticSplitPx / max(splitLimitPx, 0.001));
    vec2 chroma = refractAxis * resolvedStaticSplitPx / fbSize;
    float chromaCoverage = spectralAberration > 0.001 ? 1.0 : 0.0;
    if (chromaticMotion == 1) {
        float time = uGlassClock.x;
        float playbackImpulse = clamp(max(v_Params11.x, v_Params11.y), 0.0, 1.0);
        float activity = smoothstep(0.02, 0.32, playbackImpulse);
        float primaryWave = sin(time * 1.36 + lensUv.x * 8.0 - lensUv.y * 4.5);
        float secondaryWave = sin(time * 0.83 - lensUv.x * 3.5 + lensUv.y * 6.0 + 1.2);
        vec2 direction = safeNormalize(vec2(
                cos(time * 0.91 + lensUv.y * 5.2 + primaryWave * 0.42),
                sin(time * 1.07 - lensUv.x * 4.6 + secondaryWave * 0.38)),
                refractAxis);
        float waveScale = mix(0.68, 1.32,
                0.5 + 0.5 * sin(time * 1.08 + lensUv.x * 6.2 + lensUv.y * 3.7));
        float splitPx = min(
                bevelWidthFramebufferPx * spectralAberration * waveScale,
                lensSplitLimitPx) * activity;
        chroma = direction * splitPx / fbSize;
        chromaCoverage = mix(0.62, 1.0, edgeFocus) * activity;
    } else if (chromaticMotion == 2) {
        float activity = clamp(max(v_Params11.x, v_Params11.y), 0.0, 1.0);
        float impactLimitPx = min(
                bevelWidthFramebufferPx * mix(0.5, 1.0, activity),
                lensSplitLimitPx);
        float splitPx = impactLimitPx
                * tanh(rawStaticSplitPx / max(impactLimitPx, 0.001));
        chroma = refractAxis * splitPx / fbSize;
        chromaCoverage = smoothstep(0.0, 0.24, liquidEdge)
                * mix(0.72, 1.0, activity);
    }

    vec3 centerScene = sampleLiquidScene(centerUv, logicalSize).rgb;
    vec3 tint = clamp(v_Color.rgb, 0.0, 1.0);

    float lowerGlassCoverage = stackRegionMask(centerUv, logicalSize)
            * texture(u_StackTexture, centerUv).a;
    float stackedContactShadow = lowerGlassCoverage * pow(liquidEdge, 3.0)
            * clamp(uStackMeta.z, 0.0, 0.4);
    float tintStrength = clamp(v_Params2.w, 0.0, 1.0);
    vec3 finalColor = mix(centerScene, centerScene * tint, tintStrength);
    float bodyTransmission = 1.0;
    if (glassVariant == 2 || glassVariant == 3) {
        float body = smoothstep(0.0, max(liquidBevelPx, 1.0), max(-d, 0.0));
        float absorption = glassVariant == 2 ? 0.19 : 0.09;
        bodyTransmission = 1.0 - absorption * body;
    }
    float edgeBand = edgeFocus * edgeFocus;
    float directionalShade = directionalSpecular
            ? 0.020 + 0.025 * (1.0 - dot(refractNormal, normalize(vec2(-0.48, 0.88)))) * 0.5
            : 0.020;
    float surfaceTransmission = bodyTransmission * (1.0 - edgeBand * directionalShade);
    finalColor *= surfaceTransmission;
    float finalAlpha = shapeAlpha * v_Color.a;
    if (finalAlpha <= 0.001) {
        discard;
    }
    if (stackedContactShadow > 0.001) {
        finalColor *= 1.0 - stackedContactShadow;
    }

    vec3 blendBackdrop = texture(u_Texture, uv).rgb;
#ifdef COMBATANT_UI_UNDERLAY
    vec4 blendUi = texture(u_UiUnderlayTexture, uv);
    float blendUiCoverage = clamp(blendUi.a * uUiBackdrop.x, 0.0, 1.0);
    vec3 blendUiColor = blendUi.rgb / max(blendUi.a, 0.0001);
    blendBackdrop = mix(blendBackdrop, blendUiColor, blendUiCoverage);
#endif
    finalColor = combatantResolveBackdropBlend(
            blendBackdrop,
            clamp(finalColor, 0.0, 1.0),
            tint,
            uBlendParams,
            uBlendTone0,
            uBlendTone1
    );

    if (chromaCoverage > 0.001 && dot(chroma, chroma) > 1e-12) {
        vec3 dispersedScene = vec3(
                sampleLiquidScene(centerUv - chroma, logicalSize).r,
                centerScene.g,
                sampleLiquidScene(centerUv + chroma, logicalSize).b);
        vec3 dispersedColor = mix(dispersedScene, dispersedScene * tint, tintStrength)
                * surfaceTransmission;
        if (stackedContactShadow > 0.001) {
            dispersedColor *= 1.0 - stackedContactShadow;
        }
        dispersedColor = combatantResolveBackdropBlend(
                blendBackdrop, clamp(dispersedColor, 0.0, 1.0), tint,
                uBlendParams, uBlendTone0, uBlendTone1);
        finalColor = mix(finalColor, dispersedColor, clamp(chromaCoverage, 0.0, 1.0));
    }
    fragColor = vec4(clamp(finalColor, 0.0, 1.0), finalAlpha);
}
