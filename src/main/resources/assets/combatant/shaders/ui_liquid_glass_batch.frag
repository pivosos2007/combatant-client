#version 330 core

#moj_import <combatant:ui_geometry.glsl>

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

in vec2 v_TexCoord;
in vec4 v_Local;
in vec4 v_Color;
in vec4 v_Rect;
in vec4 v_Params;
in vec4 v_Params2;
in vec4 v_Params3;
in vec4 v_Params4;
in vec4 v_Params5;
in vec4 v_Params6;
in vec4 v_Params7;

out vec4 fragColor;

uniform sampler2D u_Texture;     // clean scene/background source
uniform sampler2D u_BlurTexture; // existing prepared UI blur source

#ifdef COMBATANT_UI_UNDERLAY
uniform sampler2D u_UiUnderlayTexture;
layout (std140) uniform UIBackdrop {
    vec4 uUiBackdrop; // x = UI underlay mix
};
#endif

layout (std140) uniform UIBatch {
    vec4 uScreen; // xy = framebuffer size, zw = logical size
    vec4 uLayer;
};

#ifdef COMBATANT_ANALYTIC_CLIP
#moj_import <combatant:ui_clip.glsl>
#endif

float roundedBoxSDF(vec2 p, vec2 halfSize, vec4 r, float smoothness) {
    // r order: TL, TR, BR, BL
    //
    // p is centered logical coord:
    // x < 0 = left,  x > 0 = right
    // y < 0 = top,   y > 0 = bottom
    float radius;
    if (p.x < 0.0) {
        radius = (p.y < 0.0) ? r.x : r.w; // TL : BL
    } else {
        radius = (p.y < 0.0) ? r.y : r.z; // TR : BR
    }

    vec2 q = abs(p) - halfSize + radius;
    vec2 qc = max(q, 0.0);

    float k = max(smoothness, 1.0001);
    float len = pow(pow(qc.x, k) + pow(qc.y, k), 1.0 / k);

    return min(max(q.x, q.y), 0.0) + len - radius;
}

float decodeDistort(float packedValue, out float cornerSmoothness, out float blurAlpha, out bool squircle) {
    // Packed payload: rounded boxes use smoothness*10000; squircles use the 200000 marker
    // with exponent hundredths in the following bucket. Small values remain valid for compatibility.
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
    return squircle ? squircleSDF(p, halfSize, exponent) : roundedBoxSDF(p, halfSize, radius, exponent);
}

vec2 safeNormalize(vec2 v, vec2 fallback) {
    float len2 = dot(v, v);
    if (len2 <= 1e-6) return fallback;
    return v * inversesqrt(len2);
}

vec2 warpedLocal(vec4 local) {
    float invW = abs(local.z) > 0.000001 ? local.z : 1.0;
    return local.xy / invW;
}

float fresnelTerm(float signedPower, float edgeGradient) {
    float power = max(abs(signedPower), 0.001);
    float base = (signedPower < 0.0) ? edgeGradient : (1.0 - edgeGradient);
    base = clamp(base, 0.001, 1.0);

    if (power > 20.0) {
        return clamp(exp(power * log(base)), 0.0, 1.0);
    }

    return clamp(pow(base, power), 0.0, 1.0);
}

void decodeFresnelPayload(float packedValue, out float fresnelMix, out float prismStrength, out float prismPhase) {
    fresnelMix = clamp(packedValue, 0.0, 1.0);
    prismStrength = 0.0;
    prismPhase = 0.0;
    if (packedValue < 1.5) return;

    float payload = max(0.0, packedValue - 2.0);
    float phaseBucket = floor(payload / 256.0 + 0.0001);
    float strengthPayload = payload - phaseBucket * 256.0;
    float strengthBucket = floor(strengthPayload / 2.0 + 0.0001);
    fresnelMix = clamp(strengthPayload - strengthBucket * 2.0, 0.0, 1.0);
    prismStrength = clamp(strengthBucket / 100.0, 0.0, 1.0);
    prismPhase = clamp(phaseBucket / 100.0, 0.0, 1.0);
}

const vec3 LUMA_WEIGHTS = vec3(0.2126, 0.7152, 0.0722);

float luminance(vec3 c) {
    return dot(c, LUMA_WEIGHTS);
}

float prismStripe(float distanceToSweep, float center, float halfWidth, float feather) {
    return 1.0 - smoothstep(halfWidth, halfWidth + feather, abs(distanceToSweep - center));
}

float prismHash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 decodePackedRgb(float packedValue) {
    float packed = clamp(floor(packedValue + 0.5), 0.0, 16777215.0);
    float r = floor(packed / 65536.0);
    packed -= r * 65536.0;
    float g = floor(packed / 256.0);
    float b = packed - g * 256.0;
    return vec3(r, g, b) / 255.0;
}

vec3 lookupGlassColor(vec3 scene, vec3 tint) {
    float sourceLuma = max(luminance(scene), 0.0001);
    float peak = max(scene.r, max(scene.g, scene.b));

    // Limit glare by scaling the original RGB uniformly. Rebuilding the color
    // around a gray luminance axis made glass visibly desaturated.
    float brightWeight = smoothstep(0.52, 0.92, sourceLuma);
    float glareWeight = smoothstep(0.78, 1.00, peak);
    float targetLuma = mix(sourceLuma, 0.48 + 0.20 * sqrt(sourceLuma), brightWeight);
    targetLuma *= 1.0 - glareWeight * 0.055;
    vec3 mapped = scene * (targetLuma / sourceLuma);

    // Blur naturally loses saturation. Restore a small amount without changing
    // the mapped luminance or forcing every material toward neutral gray.
    float mappedLuma = luminance(mapped);
    mapped = vec3(mappedLuma) + (mapped - vec3(mappedLuma)) * (1.05 + brightWeight * 0.08);

    // Tint contributes hue, not additional brightness.
    float tintLuma = luminance(tint);
    vec3 tintChroma = tint - vec3(tintLuma);
    mapped += tintChroma * (0.055 + brightWeight * 0.035);

    return clamp(mapped, 0.0, 1.0);
}

void main() {
    vec4 screen = uScreen;
    vec2 fbSize = max(screen.xy, vec2(1.0));
    vec2 logicalSize = max(screen.zw, vec2(1.0));
    vec2 logicalScale = logicalSize / fbSize;

    // Perspective-correct global logical coordinates also keep scene sampling stable
    // when this material is rendered through a bounded local MSAA target.
    vec2 frag = warpedLocal(v_Local);

    vec2 size = max(v_Rect.zw, vec2(1.0));
    vec2 center = v_Rect.xy + size * 0.5;
    vec2 pos = frag - center;
    vec4 radius = normalizeRadii(v_Params, size);

    float cornerSmoothness;
    float blurAlpha;
    bool squircle;
    float distortStrength = decodeDistort(max(v_TexCoord.y, 0.0), cornerSmoothness, blurAlpha, squircle);
    bool customShape = v_Params6.w > 0.5;
    vec2 halfSize = size * 0.5 - ((squircle || customShape) ? 0.0 : 1.0);

    float selfDistance = glassShapeSDF(pos, halfSize, radius, cornerSmoothness, squircle);
#ifdef COMBATANT_ANALYTIC_CLIP
    vec2 clipPosition = combatantLogicalFragCoord();
    float clipDistance = combatantClipDistance(clipPosition);
    bool clipOwnsVisibleEdge = clipDistance > selfDistance;
    float d = max(selfDistance, clipDistance);
#else
    float d = selfDistance;
#endif
    float aa = max(max(logicalScale.x, logicalScale.y) * 1.35, 0.75);
    float shapeAlpha = 1.0 - smoothstep(-aa * 0.5, aa, d);

    if (shapeAlpha <= 0.001) {
        discard;
    }

    float thickness = max(v_Params2.x, 1.0);
    float distToEdge = abs(d);
    float edgeGradient = 1.0 - clamp(distToEdge / thickness, 0.0, 1.0);
    float fresnel = fresnelTerm(v_Params2.y, edgeGradient);
    float wideRim = smoothstep(0.04, 0.92, edgeGradient);

    // SDF normal in logical top-left coordinates. Converted to framebuffer UV below.
    float nStep = max(1.0, max(logicalScale.x, logicalScale.y));
    float dx = glassShapeSDF(pos + vec2(nStep, 0.0), halfSize, radius, cornerSmoothness, squircle)
             - glassShapeSDF(pos - vec2(nStep, 0.0), halfSize, radius, cornerSmoothness, squircle);
    float dy = glassShapeSDF(pos + vec2(0.0, nStep), halfSize, radius, cornerSmoothness, squircle)
             - glassShapeSDF(pos - vec2(0.0, nStep), halfSize, radius, cornerSmoothness, squircle);
#ifdef COMBATANT_ANALYTIC_CLIP
    if (clipOwnsVisibleEdge) {
        dx = combatantClipDistance(clipPosition + vec2(nStep, 0.0))
           - combatantClipDistance(clipPosition - vec2(nStep, 0.0));
        dy = combatantClipDistance(clipPosition + vec2(0.0, nStep))
           - combatantClipDistance(clipPosition - vec2(0.0, nStep));
    }
#endif
    vec2 sdfNormal = safeNormalize(vec2(dx, dy), safeNormalize(pos, vec2(0.0, -1.0)));
    vec2 uvNormal = vec2(sdfNormal.x, -sdfNormal.y);

    vec2 uv = vec2(
        frag.x / logicalSize.x,
        1.0 - frag.y / logicalSize.y
    );

    float fresnelMix;
    float prismStrength;
    float prismPhase;
    decodeFresnelPayload(v_TexCoord.x, fresnelMix, prismStrength, prismPhase);
    vec2 surfaceUv = pos / size + 0.5;

    // Stable per-surface randomization. Position is quantized coarsely so animated
    // geometry does not sparkle, while neighbouring Modules dropdowns still receive
    // different material characters.
    vec2 surfaceKey = vec2(
        floor((v_Rect.x + size.x * 0.31) / 24.0),
        floor((size.x * 0.37 + size.y * 0.63) / 16.0)
    );
    float strengthSeed = prismHash(surfaceKey + vec2(7.1, 19.7));
    float waveSeed = prismHash(surfaceKey + vec2(31.3, 5.9));
    float colorSeed = prismHash(surfaceKey + vec2(13.7, 47.1));
    float fillSeed = prismHash(surfaceKey + vec2(61.9, 23.3));
    prismStrength = clamp(prismStrength * mix(0.62, 1.18, strengthSeed), 0.0, 1.0);

    float sweepCoord = surfaceUv.x * 0.72 + surfaceUv.y * 0.28;
    float sweepDistance = sweepCoord - prismPhase;

    // Two travelling waves bend the sweep into a liquid caustic. The low-frequency
    // wave defines the silhouette; the smaller ripple prevents a mechanically clean
    // diagonal without turning the surface into noisy rainbow stripes.
    float alongSweep = surfaceUv.x * -0.28 + surfaceUv.y * 0.72;
    float wavePhaseA = alongSweep * mix(13.5, 18.5, waveSeed)
            + prismPhase * 5.0 + waveSeed * 6.2831853;
    float wavePhaseB = alongSweep * mix(31.0, 42.0, fillSeed)
            - prismPhase * 8.0 + fillSeed * 6.2831853;
    float waveAmplitudeA = mix(0.010, 0.019, waveSeed);
    float waveAmplitudeB = mix(0.0030, 0.0065, fillSeed);
    float waveFrequencyA = mix(13.5, 18.5, waveSeed);
    float waveFrequencyB = mix(31.0, 42.0, fillSeed);
    float waveOffsetA = sin(wavePhaseA) * waveAmplitudeA;
    float waveOffsetB = sin(wavePhaseB) * waveAmplitudeB;
    float liquidSweepDistance = sweepDistance + waveOffsetA + waveOffsetB;
    float waveSlope = cos(wavePhaseA) * (waveAmplitudeA * waveFrequencyA)
            + cos(wavePhaseB) * (waveAmplitudeB * waveFrequencyB);

    // The body is intentionally broad. Two offset envelopes overlap into an uneven
    // pool of refracted color instead of concentrating all energy into one wire.
    float widthVariance = mix(0.78, 1.28, fillSeed);
    float prismEnvelopeDistance = liquidSweepDistance / (0.175 * widthVariance);
    float primaryEnvelope = exp(-prismEnvelopeDistance * prismEnvelopeDistance);
    float secondaryOffset = mix(0.060, 0.108, waveSeed);
    float secondaryDistance = (liquidSweepDistance - secondaryOffset - sin(wavePhaseA * 0.63) * 0.018)
            / (0.125 * mix(0.82, 1.22, colorSeed));
    float secondaryEnvelope = exp(-secondaryDistance * secondaryDistance);
    float fillWaveA = 0.5 + 0.5 * sin(alongSweep * mix(6.5, 11.0, fillSeed)
            + prismPhase * 3.0 + sin(wavePhaseB) * 0.48 + waveSeed * 5.0);
    float fillWaveB = 0.5 + 0.5 * sin(alongSweep * mix(12.0, 19.0, colorSeed)
            - prismPhase * 4.0 + liquidSweepDistance * 12.0 + fillSeed * 4.0);
    float unevenFloor = mix(0.20, 0.42, strengthSeed);
    float unevenFill = unevenFloor + (1.0 - unevenFloor)
            * mix(fillWaveA, fillWaveB, 0.28 + 0.38 * fillSeed * fillWaveA);
    float prismEnvelope = max(primaryEnvelope, secondaryEnvelope * 0.72)
            * prismStrength * unevenFill;

    float waveVariation = 0.66 + 0.34 * sin(alongSweep * 18.0 + sin(wavePhaseA) * 1.10);
    float prismCrest = prismStripe(liquidSweepDistance, 0.000, 0.008, 0.021)
            * prismStrength * waveVariation;
    float prismEcho = prismStripe(liquidSweepDistance, 0.058, 0.010, 0.026)
            * prismStrength * (0.72 - waveVariation * 0.20);
    float prismBand = max(prismEnvelope * 0.72, max(prismCrest * 0.58, prismEcho * 0.48));

    // Keep the body of the glass locked to screen UV. Previously the center was
    // displaced along a rect-relative radial vector, so changing a rect's bounds
    // visibly recomposed otherwise stationary scenery.
    float centerDistortPx = distortStrength * min(fbSize.x, fbSize.y) * 0.42 * (1.0 + prismBand * 1.10);
    float edgeRefraction = smoothstep(0.42, 0.98, edgeGradient);
    edgeRefraction *= edgeRefraction;
    vec2 centerUv = clamp(uv + uvNormal * (centerDistortPx / fbSize) * edgeRefraction, vec2(0.001), vec2(0.999));

    // Optional frosted modifier. The hash is anchored in logical surface space, so the
    // grain follows the glass surface instead of sparkling with framebuffer resizes.
    float frostedJitterPx = clamp(v_Params7.x, 0.0, 4.0);
    if (frostedJitterPx > 0.001) {
        vec2 frostCell = floor((frag - v_Rect.xy) * 0.75);
        float frostX = prismHash(frostCell + surfaceKey * 3.17 + vec2(17.0, 41.0)) - 0.5;
        float frostY = prismHash(frostCell.yx + surfaceKey * 5.03 + vec2(59.0, 11.0)) - 0.5;
        vec2 frostOffset = vec2(frostX, frostY) * (2.0 * frostedJitterPx) / fbSize;
        centerUv = clamp(centerUv + frostOffset, vec2(0.001), vec2(0.999));
    }

    // Rim: sample clean scene outside the SDF boundary. This is the only part that
    // should read as mirror/detail; center remains the existing blur material.
    float mirrorPx = thickness * (1.90 + 2.50 * fresnel) + centerDistortPx * 0.55;
    vec2 mirrorUv = clamp(uv + uvNormal * (mirrorPx / fbSize), vec2(0.001), vec2(0.999));
    float chromaOffsetPx = 1.65 + prismBand * 7.50;
    vec2 chromaUvR = clamp(mirrorUv + uvNormal * (chromaOffsetPx / fbSize), vec2(0.001), vec2(0.999));
    vec2 chromaUvB = clamp(mirrorUv - uvNormal * (chromaOffsetPx / fbSize), vec2(0.001), vec2(0.999));

    vec4 blurColor = texture(u_BlurTexture, centerUv);
    vec4 cleanColor = texture(u_Texture, centerUv);
#ifdef COMBATANT_UI_UNDERLAY
    // The accumulation target stores ordinary translucent UI with premultiplied RGB.
    // Recover its straight color, then place it over both scene sources before the glass
    // material is evaluated. PASS_THROUGH binds the sharp target; BLUR binds its cheap chain.
    vec4 uiUnderlay = texture(u_UiUnderlayTexture, centerUv);
    float uiCoverage = clamp(uiUnderlay.a * uUiBackdrop.x, 0.0, 1.0);
    vec3 uiUnderlayColor = uiUnderlay.rgb / max(uiUnderlay.a, 0.0001);
#endif
    vec4 mirrorColor = texture(u_Texture, mirrorUv);
    vec4 blurMirrorColor = texture(u_BlurTexture, mirrorUv);

    vec3 chromaMirror = vec3(
        texture(u_Texture, chromaUvR).r,
        mirrorColor.g,
        texture(u_Texture, chromaUvB).b
    );

    // Sample the scene through three displaced channels inside the travelling
    // fracture. This makes dispersion respond to actual content instead of reading
    // as a flat rainbow overlay.
    vec2 liquidNormalLocal = normalize(vec2(0.72, 0.28) + vec2(-0.28, 0.72) * waveSlope);
    vec2 prismAxis = vec2(liquidNormalLocal.x, -liquidNormalLocal.y);
    vec2 prismSceneOffset = prismAxis * ((1.75 + 6.25 * prismBand) / fbSize);
    vec3 prismScene = vec3(
        texture(u_Texture, clamp(centerUv + prismSceneOffset, vec2(0.001), vec2(0.999))).r,
        texture(u_Texture, centerUv).g,
        texture(u_Texture, clamp(centerUv - prismSceneOffset, vec2(0.001), vec2(0.999))).b
    );

    vec3 tint = clamp(v_Color.rgb, 0.0, 1.0);
    // Blur is the material base. Clean scene clarity is deliberately tiny for
    // blur-first presets, otherwise the center turns back into raw refraction.
    // Keep the body blur-first. Stronger clean-scene leakage made large panels look
    // like weak refraction and visually erased the prepared blur layer.
    float clarityMix = clamp(0.004 + fresnelMix * 0.045, 0.0, 0.055);
    vec3 centerScene = mix(blurColor.rgb, cleanColor.rgb, clarityMix);
    float sourceSceneLuma = luminance(centerScene);
    float brightScene = smoothstep(0.48, 0.82, sourceSceneLuma);
    vec3 centerColor = lookupGlassColor(centerScene, tint);

    float topLight = clamp(dot(sdfNormal, normalize(vec2(-0.35, -1.0))) * 0.5 + 0.5, 0.0, 1.0);
    float bottomShade = clamp(dot(sdfNormal, normalize(vec2(0.20, 1.0))) * 0.5 + 0.5, 0.0, 1.0);

    // Low fresnelMix profiles must refract the already blurred source, not the clean
    // scene. High fresnelMix profiles still get the sharper chromatic mirror detail.
    vec2 rimTangent = vec2(-uvNormal.y, uvNormal.x);
    float rimBlurPx = 2.20 + 3.80 * fresnel + thickness * 0.11;
    vec2 rimNormalStep = uvNormal * (rimBlurPx / fbSize);
    vec2 rimTangentStep = rimTangent * (rimBlurPx / fbSize);
    vec3 blurRimWide = blurMirrorColor.rgb * 0.36;
    blurRimWide += texture(u_BlurTexture, clamp(mirrorUv + rimNormalStep, vec2(0.001), vec2(0.999))).rgb * 0.16;
    blurRimWide += texture(u_BlurTexture, clamp(mirrorUv - rimNormalStep, vec2(0.001), vec2(0.999))).rgb * 0.16;
    blurRimWide += texture(u_BlurTexture, clamp(mirrorUv + rimTangentStep, vec2(0.001), vec2(0.999))).rgb * 0.16;
    blurRimWide += texture(u_BlurTexture, clamp(mirrorUv - rimTangentStep, vec2(0.001), vec2(0.999))).rgb * 0.16;

    float cleanRimT = smoothstep(0.28, 0.72, fresnelMix);
    vec3 blurredRim = lookupGlassColor(blurRimWide, tint);
    vec3 cleanRim = lookupGlassColor(chromaMirror, tint);
    vec3 rimSceneColor = mix(blurredRim, cleanRim, cleanRimT);
    rimSceneColor *= 1.0 - bottomShade * fresnel * 0.14;

    // Refraction/scene replacement obeys fresnelMix, but the white glass edge does
    // not. This keeps blur-first health glass glassy instead of flat matte.
    float rimSceneMix = clamp((fresnel * 0.46 + wideRim * 0.08) * fresnelMix, 0.0, 0.62);
    vec3 finalColor = mix(centerColor, rimSceneColor, rimSceneMix);

    float hairline = 1.0 - smoothstep(0.0, aa * 1.55, abs(d));
    vec3 rimHighlight = mix(vec3(0.88, 0.95, 1.0), tint, 0.24 + brightScene * 0.10);
    float whiteRim = clamp(fresnel * (0.16 + 0.23 * topLight) + hairline * (0.09 + 0.18 * topLight), 0.0, 0.34);
    whiteRim *= 1.0 - brightScene * 0.42;
    finalColor = mix(finalColor, rimHighlight, whiteRim);

    // Thin specular wire on the exact SDF edge. Keep it mostly independent from
    // fresnelMix so low-refraction profiles still keep a visible glass rim.
    float specMix = hairline * (0.10 + 0.20 * topLight) * (0.60 + 0.40 * fresnelMix);
    specMix *= 1.0 - brightScene * 0.52;
    finalColor = mix(finalColor, rimHighlight, specMix);

    vec3 refractedPrism = lookupGlassColor(prismScene, tint);
    finalColor = mix(finalColor, refractedPrism, prismEnvelope * (0.20 + 0.12 * wideRim));

    // Color belongs to the moving caustic crests, not to three parallel RGB bands.
    // Slowly changing interference swaps cyan/violet and warm/pink fragments along
    // the same wavy front, while the echo stays quieter and cooler.
    float colorInterference = 0.5 + 0.5 * sin(alongSweep * mix(9.0, 15.0, colorSeed)
            + prismPhase * 4.5 + sin(wavePhaseB) * 0.42 + colorSeed * 6.2831853);
    vec3 coolCaustic = mix(vec3(0.30, 0.84, 1.00), vec3(0.68, 0.38, 1.00), colorInterference);
    vec3 warmCaustic = mix(vec3(1.00, 0.58, 0.28), vec3(1.00, 0.38, 0.72), colorInterference);
    float surfaceWarmBias = mix(0.08, 0.62, colorSeed);
    vec3 crestColor = mix(coolCaustic, warmCaustic,
            clamp(surfaceWarmBias + sin(wavePhaseA) * 0.16, 0.0, 1.0));
    vec3 fillColor = mix(coolCaustic, warmCaustic,
            clamp(surfaceWarmBias * 0.72 + fillWaveB * 0.34, 0.0, 1.0));
    float fillIntensity = mix(0.76, 1.20, strengthSeed);
    float fillMix = clamp(prismEnvelope * fillIntensity * (0.15 + 0.08 * wideRim), 0.0, 0.25);
    finalColor = mix(finalColor, fillColor, fillMix);
    float crestMix = clamp(prismCrest * (0.10 + 0.06 * wideRim), 0.0, 0.16);
    finalColor = mix(finalColor, crestColor, crestMix);
    finalColor = mix(finalColor, coolCaustic, prismEcho * (0.05 + 0.035 * wideRim));
    finalColor = mix(finalColor, vec3(0.98, 1.0, 1.0), prismCrest * (0.025 + 0.035 * wideRim));

    // Explicit inner-glow modifier. It is evaluated from the final visible SDF edge, so
    // rounded boxes, squircles, convex primitives and analytic clips share one behavior.
    float innerGlowStrength = clamp(v_Params7.y, 0.0, 1.0);
    float innerGlowSizePx = max(v_Params7.z, 0.0);
    if (innerGlowStrength > 0.001 && innerGlowSizePx > 0.001) {
        float insideDistance = max(-d, 0.0);
        float innerGlowMask = (1.0 - smoothstep(0.0, innerGlowSizePx, insideDistance)) * step(d, 0.0);
        vec3 innerGlowColor = decodePackedRgb(v_Params7.w);
        float glowMix = clamp(innerGlowMask * innerGlowStrength * (0.34 + 0.18 * topLight), 0.0, 0.52);
        finalColor = mix(finalColor, innerGlowColor, glowMix);
    }

    float fresnelAlpha = clamp(v_Params2.z, 0.0, 1.0);
    float baseAlpha = clamp(v_Params2.w, 0.0, 1.0);
    baseAlpha = clamp(baseAlpha + brightScene * (0.035 + 0.045 * (1.0 - fresnelMix)), 0.0, 1.0);
    float edgeAlpha = clamp(fresnel * (0.25 + 0.75 * fresnelMix) + hairline * 0.40, 0.0, 1.0);

    // Separate layers inside one liquid-glass draw:
    //   blurAlpha controls the prepared blur layer opacity;
    //   v_Color.a controls only the glass/material/rim opacity.
    // This keeps HUD glass from weakening the blur when the material veil is transparent.
    float materialAlpha = mix(baseAlpha, fresnelAlpha, edgeAlpha) * shapeAlpha * v_Color.a;
    float blurLayerAlpha = blurAlpha * shapeAlpha;
    float finalAlpha = materialAlpha + blurLayerAlpha * (1.0 - materialAlpha);

    if (finalAlpha <= 0.001) {
        discard;
    }

    vec3 blurLayerColor = blurColor.rgb;
#ifdef COMBATANT_UI_UNDERLAY
    // UI is a backdrop contribution, not an optical input to the glass material. Keeping this
    // blend after rim/refraction evaluation prevents HUD colors/luminance from driving the rim.
    blurLayerColor = mix(blurLayerColor, uiUnderlayColor, uiCoverage);
#endif
    finalColor = (blurLayerColor * blurLayerAlpha * (1.0 - materialAlpha) + finalColor * materialAlpha) / max(finalAlpha, 1e-5);

    fragColor = vec4(finalColor, finalAlpha);
}
