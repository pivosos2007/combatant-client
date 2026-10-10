#version 330 core

#moj_import <combatant:ui_geometry.glsl>

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
in vec4 v_Params8;
in vec4 v_Params9;
in vec4 v_Params10; // normalized local interaction xy, eased deformation zw
in vec4 v_Params11; // activity, press, opening, use-local flag

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

layout (std140) uniform UIBlend {
    vec4 uBlendParams; // x = mode, y = strength, z = pivot, w = softness
    vec4 uBlendTone0;
    vec4 uBlendTone1;
};

layout (std140) uniform UIGlassFrame {
    vec4 uGlassPointer; // xy = raw pointer, zw = eased pointer, in logical coordinates
    vec4 uGlassMotion;  // xy = logical px/second, z = motion envelope, w = press impulse
};

#moj_import <combatant:ui_backdrop_blend.glsl>

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

vec2 warpedLocal(vec4 local) {
    float invW = abs(local.z) > 0.000001 ? local.z : 1.0;
    return local.xy / invW;
}

vec2 deformGlassPoint(vec2 point, vec2 size, vec2 center, float interactionStrength,
                      float interactionRadius, float interactionViscosity, float activity,
                      vec2 pointerCenter, vec2 displacement) {
    if (interactionRadius <= 0.0 || interactionStrength <= 0.0 || activity <= 0.0001) {
        return point;
    }
    float viscosity = clamp(interactionViscosity, 0.0, 1.0);
    vec2 interactionCenter = pointerCenter - center;
    float reach = max(interactionRadius * min(size.x, size.y), 0.0001);
    float influence = 1.0 - smoothstep(reach * 0.12, reach, length(point - interactionCenter));
    // liquidGL's displacement is already temporally eased by the per-node CPU state.
    // Keep the analytic SDF as the only shape mask; never shift an entire rectangular quad.
    return point - displacement * influence * activity * mix(1.0, 0.78, viscosity);
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
    float packedRgb = clamp(floor(packedValue + 0.5), 0.0, 16777215.0);
    float r = floor(packedRgb / 65536.0);
    packedRgb -= r * 65536.0;
    float g = floor(packedRgb / 256.0);
    float b = packedRgb - g * 256.0;
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
    vec2 originalPos = frag - center;
    int glassVariant = int(floor(v_Params8.x + 0.5));
    bool liquidOptics = glassVariant != 1;
    bool localInteraction = v_Params11.w < 0.0;
    float magnification = clamp(abs(v_Params11.w), 0.65, 1.8);
    // Packed flags are exact 24-bit integers in a float attribute. Adding 0.5
    // rounds odd values to the next EVEN float at this magnitude (ULP=1),
    // corrupting the low 8-bit chromatic value, including 255 -> 0.
    uint opticalBits = uint(v_Params6.z);
    float spectralAberration = float(opticalBits & 255u) * (0.95 / 255.0);
    vec2 opticalTiltDeg = vec2(float((opticalBits >> 8u) & 127u) - 64.0,
                               float((opticalBits >> 15u) & 127u) - 64.0) * 0.25;
    bool shapeDeformation = ((opticalBits >> 22u) & 1u) != 0u;
    bool specularEnabled = ((opticalBits >> 23u) & 1u) != 0u;
    vec2 localPointer = v_Rect.xy + v_Params10.xy * size;
    vec2 pointerPos = localInteraction ? localPointer
            : mix(uGlassPointer.xy, uGlassPointer.zw, clamp(v_Params9.z, 0.0, 1.0));
    float interactionStrength = clamp(v_Params9.x, 0.0, 5.0);
    float interactionRadius = clamp(v_Params9.y, 0.0, 2.0);
    vec2 nodeFlow = localInteraction ? v_Params10.zw * size : vec2(0.0);
    vec4 radius = normalizeRadii(v_Params, size);

    float cornerSmoothness;
    float blurAlpha;
    bool squircle;
    float distortStrength = decodeDistort(max(v_TexCoord.y, 0.0), cornerSmoothness, blurAlpha, squircle);
    bool customShape = v_Params6.w > 0.5;
    vec2 halfSize = size * 0.5 - ((squircle || customShape) ? 0.0 : 1.0);

    // Only an explicitly opted-in UI owner can supply a fluid impulse.
    // Exact SDF gating prevents bounding-box corners from triggering polygon lens motion.
    float hoverInside = 0.0;
    float interactionActivity = 0.0;
    if (liquidOptics && interactionStrength > 0.0 && interactionRadius > 0.0) {
        float pointerSdf = glassShapeSDF(pointerPos - center, halfSize,
                                        radius, cornerSmoothness, squircle);
        float pointerBand = max(2.0, min(size.x, size.y) * 0.055);
        hoverInside = 1.0 - smoothstep(-0.5, pointerBand, pointerSdf);
        float trailing = 1.0 - smoothstep(pointerBand, pointerBand * 3.0, pointerSdf);
        if (localInteraction) {
            // The node retains the impulse after exit; only this shape reacts.
            interactionActivity = max(v_Params11.x, max(v_Params11.y, v_Params11.z * 0.58));
            interactionActivity *= trailing;
        }
        interactionActivity = clamp(interactionActivity, 0.0, 1.0);
    }
    vec2 pos = liquidOptics && localInteraction && shapeDeformation
            ? deformGlassPoint(originalPos, size, center, interactionStrength,
                               interactionRadius, v_Params9.z, interactionActivity,
                               pointerPos, nodeFlow)
            : originalPos;

    float selfDistance = glassShapeSDF(pos, halfSize, radius, cornerSmoothness, squircle);
#ifdef COMBATANT_ANALYTIC_CLIP
    vec2 clipPosition = combatantLogicalFragCoord();
    float clipDistance = combatantClipDistance(clipPosition);
    float d = max(selfDistance, clipDistance);
#else
    float d = selfDistance;
#endif

    // d already contains the final visible SDF (including analytic clip when present).
    // Screen-space derivatives therefore replace four additional SDF evaluations and four
    // additional clip-distance evaluations while keeping the normal tied to the visible edge.
    vec2 dScreenGradient = vec2(
        dFdx(d) / max(logicalScale.x, 1e-6),
        dFdy(d) / max(logicalScale.y, 1e-6)
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

    float thickness = max(v_Params2.x, 1.0);
    float distToEdge = abs(d);
    float edgeGradient = 1.0 - clamp(distToEdge / thickness, 0.0, 1.0);
    bool edgeActive = edgeGradient > 0.025;
    float fresnel = edgeActive ? fresnelTerm(v_Params2.y, edgeGradient) : 0.0;
    float wideRim = edgeActive ? smoothstep(0.04, 0.92, edgeGradient) : 0.0;
    float liquidBevelPx = max(v_Params8.z * min(size.x, size.y), 0.001);
    if (glassVariant == 4) liquidBevelPx *= 1.65; // SOFT_LENS
    float liquidEdge = liquidOptics
            ? 1.0 - smoothstep(0.0, liquidBevelPx, max(-selfDistance, 0.0))
            : 0.0;

    vec2 uv = vec2(
        frag.x / logicalSize.x,
        1.0 - frag.y / logicalSize.y
    );

    float fresnelMix;
    float prismStrength;
    float prismPhase;
    decodeFresnelPayload(v_TexCoord.x, fresnelMix, prismStrength, prismPhase);

#ifdef COMBATANT_LIGHT_GLASS
    bool prismActive = false;
    prismStrength = 0.0;
#else
    // Full glass keeps the travelling prism/caustic animation across the whole surface.
    // Rim/chromatic work is still edge-only below; ordinary non-prismatic glass is routed
    // to COMBATANT_LIGHT_GLASS and compiles this path out entirely.
    bool prismActive = prismStrength > 0.01;
#endif

    float frostedJitterPx = clamp(v_Params7.x, 0.0, 4.0);
    vec2 surfaceUv = pos / size + 0.5;
    vec2 surfaceKey = vec2(0.0);
    if (frostedJitterPx > 0.001 || prismActive) {
        // Stable per-surface randomization. Position is quantized coarsely so animated
        // geometry does not sparkle, while neighbouring surfaces still differ.
        surfaceKey = vec2(
            floor((v_Rect.x + size.x * 0.31) / 24.0),
            floor((size.x * 0.37 + size.y * 0.63) / 16.0)
        );
    }

    float prismBand = 0.0;
#ifndef COMBATANT_LIGHT_GLASS
    float prismEnvelope = 0.0;
    float prismCrest = 0.0;
    float prismEcho = 0.0;
    float alongSweep = 0.0;
    float waveSlope = 0.0;
    float wavePhaseA = 0.0;
    float wavePhaseB = 0.0;
    float fillWaveB = 0.0;
    float strengthSeed = 0.5;
    float colorSeed = 0.5;

    // Full-glass caustics are intentionally evaluated across the surface: the travelling
    // sweep is itself the visible mask. Non-prismatic materials never enter this shader
    // path, so ordinary glass still avoids all of this math.
    if (prismActive) {
        strengthSeed = prismHash(surfaceKey + vec2(7.1, 19.7));
        float waveSeed = prismHash(surfaceKey + vec2(31.3, 5.9));
        colorSeed = prismHash(surfaceKey + vec2(13.7, 47.1));
        float fillSeed = prismHash(surfaceKey + vec2(61.9, 23.3));
        prismStrength = clamp(prismStrength * mix(0.62, 1.18, strengthSeed), 0.0, 1.0);

        float sweepCoord = surfaceUv.x * 0.72 + surfaceUv.y * 0.28;
        float sweepDistance = sweepCoord - prismPhase;
        alongSweep = surfaceUv.x * -0.28 + surfaceUv.y * 0.72;
        wavePhaseA = alongSweep * mix(13.5, 18.5, waveSeed)
                + prismPhase * 5.0 + waveSeed * 6.2831853;
        wavePhaseB = alongSweep * mix(31.0, 42.0, fillSeed)
                - prismPhase * 8.0 + fillSeed * 6.2831853;
        float waveAmplitudeA = mix(0.010, 0.019, waveSeed);
        float waveAmplitudeB = mix(0.0030, 0.0065, fillSeed);
        float waveFrequencyA = mix(13.5, 18.5, waveSeed);
        float waveFrequencyB = mix(31.0, 42.0, fillSeed);
        float waveOffsetA = sin(wavePhaseA) * waveAmplitudeA;
        float waveOffsetB = sin(wavePhaseB) * waveAmplitudeB;
        float liquidSweepDistance = sweepDistance + waveOffsetA + waveOffsetB;
        waveSlope = cos(wavePhaseA) * (waveAmplitudeA * waveFrequencyA)
                + cos(wavePhaseB) * (waveAmplitudeB * waveFrequencyB);

        float widthVariance = mix(0.78, 1.28, fillSeed);
        float prismEnvelopeDistance = liquidSweepDistance / (0.175 * widthVariance);
        float primaryEnvelope = exp(-prismEnvelopeDistance * prismEnvelopeDistance);
        float secondaryOffset = mix(0.060, 0.108, waveSeed);
        float secondaryDistance = (liquidSweepDistance - secondaryOffset - sin(wavePhaseA * 0.63) * 0.018)
                / (0.125 * mix(0.82, 1.22, colorSeed));
        float secondaryEnvelope = exp(-secondaryDistance * secondaryDistance);
        float fillWaveA = 0.5 + 0.5 * sin(alongSweep * mix(6.5, 11.0, fillSeed)
                + prismPhase * 3.0 + sin(wavePhaseB) * 0.48 + waveSeed * 5.0);
        fillWaveB = 0.5 + 0.5 * sin(alongSweep * mix(12.0, 19.0, colorSeed)
                - prismPhase * 4.0 + liquidSweepDistance * 12.0 + fillSeed * 4.0);
        float unevenFloor = mix(0.20, 0.42, strengthSeed);
        float unevenFill = unevenFloor + (1.0 - unevenFloor)
                * mix(fillWaveA, fillWaveB, 0.28 + 0.38 * fillSeed * fillWaveA);
        prismEnvelope = max(primaryEnvelope, secondaryEnvelope * 0.72)
                * prismStrength * unevenFill;

        float waveVariation = 0.66 + 0.34 * sin(alongSweep * 18.0 + sin(wavePhaseA) * 1.10);
        prismCrest = prismStripe(liquidSweepDistance, 0.000, 0.008, 0.021)
                * prismStrength * waveVariation;
        prismEcho = prismStripe(liquidSweepDistance, 0.058, 0.010, 0.026)
                * prismStrength * (0.72 - waveVariation * 0.20);
        prismBand = max(prismEnvelope * 0.72, max(prismCrest * 0.58, prismEcho * 0.48));
    }
#endif

    // Center path: screen-locked refraction + prepared blur + clean scene. Mirror/chromatic
    // rim work remains edge-only; full-glass caustics are composited separately below.
    float centerDistortPx = distortStrength * min(fbSize.x, fbSize.y) * 0.42 * (1.0 + prismBand * 1.10);
    float edgeRefraction = edgeActive ? smoothstep(0.42, 0.98, edgeGradient) : 0.0;
    edgeRefraction *= edgeRefraction;
    vec2 centerUv;
    float localPulse = 0.0;
    if (liquidOptics) {
        float reach = max(interactionRadius * min(size.x, size.y), 2.0);
        float pointerFalloff = 1.0 - smoothstep(reach * 0.10, reach, length(frag - pointerPos));
        // The local optical crest follows the pointer and velocity, not a screen-wide
        // animated sine. Keep a very small static bevel when there is no interaction.
        localPulse = clamp(pointerFalloff * interactionActivity, 0.0, 1.0);
        // Edge/bevel of liquidGL, transformed into bounded pixel displacement in
        // Combatant's captured framebuffer. Prevent refraction jumping beyond the
        // prepared blur border on very large panels.
        float edgeBend = liquidEdge * v_Params8.y + pow(liquidEdge, 10.0) * v_Params8.w;
        float centerBlend = smoothstep(0.12, 0.43, length(pos / max(size.y, 1.0)));
        float bevelPixels = clamp(edgeBend * min(size.x, size.y), -22.0, 22.0);
        vec2 bevelOffset = uvNormal * (bevelPixels / fbSize) * centerBlend;
        vec2 motionLogical = localInteraction ? nodeFlow : vec2(0.0);
        // _updateInteraction already eases and attenuates nodeFlow in Java.
        // Multiplying by the independent activity envelope again used to square
        // the decay and made normal mouse movement almost invisible.
        vec2 flowOffset = vec2(motionLogical.x, -motionLogical.y)
                * pointerFalloff / logicalSize;
        // A click without pointer travel has no velocity. Give PRESS its own
        // local optical impulse, not a deformation of the element's SDF shape.
        float pressImpulse = localInteraction ? clamp(v_Params11.y, 0.0, 1.0) : 0.0;
        vec2 clickDirection = safeNormalize(frag - pointerPos,
                vec2(uvNormal.x, -uvNormal.y));
        vec2 clickBend = vec2(clickDirection.x, -clickDirection.y)
                * ((1.7 + 2.8 * liquidEdge) * pressImpulse * pointerFalloff / fbSize);
        centerUv = clamp(uv + bevelOffset + flowOffset + clickBend,
                vec2(0.001), vec2(0.999));
    } else {
        centerUv = clamp(uv + uvNormal * (centerDistortPx / fbSize) * edgeRefraction,
                vec2(0.001), vec2(0.999));
    }

    // liquidGL optical tilt is an independent refraction offset; never moves the
    // actual UI geometry, its text, or an SDF clip.
    if (liquidOptics && (abs(opticalTiltDeg.x) + abs(opticalTiltDeg.y)) > 0.01) {
        vec2 tiltOffset = vec2(tan(radians(opticalTiltDeg.y)),
                               -tan(radians(opticalTiltDeg.x))) * 0.05;
        centerUv = clamp(centerUv - tiltOffset, vec2(0.001), vec2(0.999));
    }

    // liquidGL magnification remaps lens-local coordinates before background sampling.
    // Keep display geometry and alpha unchanged: only the optical scene is magnified.
    if (liquidOptics && abs(magnification - 1.0) > 0.0005) {
        vec2 delta = (frag - center) * (1.0 / magnification - 1.0);
        centerUv = clamp(centerUv + vec2(delta.x, -delta.y) / logicalSize,
                vec2(0.001), vec2(0.999));
    }

    if (frostedJitterPx > 0.001) {
        vec2 frostCell = floor((frag - v_Rect.xy) * 0.75);
        float frostX = prismHash(frostCell + surfaceKey * 3.17 + vec2(17.0, 41.0)) - 0.5;
        float frostY = prismHash(frostCell.yx + surfaceKey * 5.03 + vec2(59.0, 11.0)) - 0.5;
        vec2 frostOffset = vec2(frostX, frostY) * (2.0 * frostedJitterPx) / fbSize;
        centerUv = clamp(centerUv + frostOffset, vec2(0.001), vec2(0.999));
    }

    vec4 blurColor = texture(u_BlurTexture, centerUv);
    vec4 cleanColor = texture(u_Texture, centerUv);
#ifdef COMBATANT_UI_UNDERLAY
    vec4 uiUnderlay = texture(u_UiUnderlayTexture, centerUv);
    float uiCoverage = clamp(uiUnderlay.a * uUiBackdrop.x, 0.0, 1.0);
    vec3 uiUnderlayColor = uiUnderlay.rgb / max(uiUnderlay.a, 0.0001);
#endif

    vec3 tint = clamp(v_Color.rgb, 0.0, 1.0);
    // The old optical profile displaced the scene but displayed almost entirely
    // the prepared Kawase blur (1.8% clear in the body, ~13% even on the bevel).
    // A refraction control cannot look responsive if displaced high-frequency
    // structure is filtered away before compositing. Preserve the frosted body,
    // but allow a bounded strip at the bevel to transmit displaced clean scene.
    // The depth term supplies a slight idle facet; the refraction term controls
    // the actual strength of that facet independently of hover/press.
    float refractivePower = liquidOptics
            ? clamp(abs(v_Params8.y) * 7.0 + abs(v_Params8.w) * 1.1, 0.0, 1.0)
            : 0.0;
    float opticalFacet = liquidOptics
            ? pow(clamp(liquidEdge, 0.0, 1.0), 0.83)
            : 0.0;
    float facetTransmission = opticalFacet * mix(0.065, 0.60, refractivePower);
    // Local reveal is coupled to refraction but does not alter coverage/alpha.
    // Idle glass stays frosted; only a small segment of the bevel and a nearby
    // patch of the body expose sharper captured-scene details while moving/pressed.
    float reactiveReveal = liquidOptics
            ? clamp(localPulse * (0.22 + 0.78 * pow(liquidEdge, 0.72))
                    + localPulse * localPulse * 0.16 * (1.0 - liquidEdge), 0.0, 1.0)
            : 0.0;
    float clarityMix = liquidOptics
            ? clamp(0.018 + facetTransmission * (glassVariant == 3 ? 0.55 : 1.0)
                    + v_Params9.w * reactiveReveal * (glassVariant == 3 ? 0.45 : 1.0), 0.0, 0.82)
            : clamp(0.004 + fresnelMix * 0.045, 0.0, 0.055);
    // Independently tunable liquidGL spectral dispersion. It is constrained to the
    // bevel, not applied to text/the entire UI, and uses the same ordered source.
    vec3 blurredScene = blurColor.rgb;
    vec3 sharpScene = cleanColor.rgb;
    if (liquidOptics && spectralAberration > 0.001) {
        // Dispersion follows optical curvature and is sampled from the *sharp*
        // displaced source. The previous <=4px split was buried by the blur mix.
        // Keep it confined to the bevel, away from HUD glyphs and the panel body.
        float dispersionPx = spectralAberration * (4.5 + 5.5 * refractivePower)
                * opticalFacet;
        vec2 splitUv = uvNormal * dispersionPx / fbSize;
        blurredScene = vec3(
                texture(u_BlurTexture, clamp(centerUv - splitUv, vec2(0.001), vec2(0.999))).r,
                blurColor.g,
                texture(u_BlurTexture, clamp(centerUv + splitUv, vec2(0.001), vec2(0.999))).b);
        sharpScene = vec3(
                texture(u_Texture, clamp(centerUv - splitUv, vec2(0.001), vec2(0.999))).r,
                cleanColor.g,
                texture(u_Texture, clamp(centerUv + splitUv, vec2(0.001), vec2(0.999))).b);
    }
    vec3 centerScene = mix(blurredScene, sharpScene, clarityMix);
    float sourceSceneLuma = luminance(centerScene);
    float brightScene = smoothstep(0.48, 0.82, sourceSceneLuma);
    vec3 centerColor = lookupGlassColor(centerScene, tint);
    vec3 finalColor = centerColor;
    if (liquidOptics) {
        // Transmission absorption: scene remains readable, surface has optical depth.
        float body = smoothstep(0.0, max(liquidBevelPx, 1.0), max(-d, 0.0));
        float lowFrequency = 0.5 + 0.5 * sin(surfaceUv.x * 3.13 + surfaceUv.y * 4.77);
        float absorption = glassVariant == 2 ? 0.19 : (glassVariant == 3 ? 0.09 : 0.065);
        float bodyAbsorption = (absorption + 0.014 * lowFrequency) * body;
        if (glassVariant == 3) {
            // Etched glass has stable, band-limited correlated optical roughness.
            float etched = sin(surfaceUv.x * size.x * 0.31)
                    * sin(surfaceUv.y * size.y * 0.37);
            float bandwidth = length(fwidth(surfaceUv * size));
            float filtered = 1.0 - smoothstep(2.0, 4.0, bandwidth);
            bodyAbsorption += filtered * etched * 0.016 * body;
        }
        finalColor *= 1.0 - bodyAbsorption;
    }

    float innerGlowStrength = clamp(v_Params7.y, 0.0, 1.0);
    float innerGlowSizePx = max(v_Params7.z, 0.0);
    bool needsNormalLighting = edgeActive || (innerGlowStrength > 0.001 && innerGlowSizePx > 0.001);
    float topLight = needsNormalLighting
            ? clamp(dot(sdfNormal, normalize(vec2(-0.35, -1.0))) * 0.5 + 0.5, 0.0, 1.0)
            : 0.5;

    float hairline = 1.0 - smoothstep(0.0, aa * 1.55, abs(d));

    if (edgeActive && !liquidOptics) {
        float bottomShade = clamp(dot(sdfNormal, normalize(vec2(0.20, 1.0))) * 0.5 + 0.5, 0.0, 1.0);
        float mirrorPx = thickness * (1.90 + 2.50 * fresnel) + centerDistortPx * 0.55;
        vec2 mirrorUv = clamp(uv + uvNormal * (mirrorPx / fbSize), vec2(0.001), vec2(0.999));
        vec4 mirrorColor = texture(u_Texture, mirrorUv);
        vec4 blurMirrorColor = texture(u_BlurTexture, mirrorUv);

        vec3 rimSceneColor;
#ifdef COMBATANT_LIGHT_GLASS
        // Lightweight glass keeps one sharp + one blurred rim sample and compiles out all
        // chromatic/prism code. This is the normal path for prismStrength == 0 materials.
        float cleanRimT = smoothstep(0.28, 0.72, fresnelMix);
        rimSceneColor = mix(lookupGlassColor(blurMirrorColor.rgb, tint),
                lookupGlassColor(mirrorColor.rgb, tint), cleanRimT);
#else
        float chromaOffsetPx = 1.65 + prismBand * 7.50;
        vec2 chromaUvR = clamp(mirrorUv + uvNormal * (chromaOffsetPx / fbSize), vec2(0.001), vec2(0.999));
        vec2 chromaUvB = clamp(mirrorUv - uvNormal * (chromaOffsetPx / fbSize), vec2(0.001), vec2(0.999));
        vec3 chromaMirror = vec3(
            texture(u_Texture, chromaUvR).r,
            mirrorColor.g,
            texture(u_Texture, chromaUvB).b
        );

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
        rimSceneColor = mix(lookupGlassColor(blurRimWide, tint),
                lookupGlassColor(chromaMirror, tint), cleanRimT);
#endif
        rimSceneColor *= 1.0 - bottomShade * fresnel * 0.14;

        float rimSceneMix = clamp((fresnel * 0.46 + wideRim * 0.08) * fresnelMix, 0.0, 0.62);
        finalColor = mix(finalColor, rimSceneColor, rimSceneMix);

        vec3 rimHighlight = mix(vec3(0.88, 0.95, 1.0), tint, 0.24 + brightScene * 0.10);
        float whiteRim = clamp(fresnel * (0.16 + 0.23 * topLight)
                + hairline * (0.09 + 0.18 * topLight), 0.0, 0.34);
        whiteRim *= 1.0 - brightScene * 0.42;
        finalColor = mix(finalColor, rimHighlight, whiteRim);

        float specMix = hairline * (0.10 + 0.20 * topLight) * (0.60 + 0.40 * fresnelMix);
        specMix *= 1.0 - brightScene * 0.52;
        finalColor = mix(finalColor, rimHighlight, specMix);

    } else if (liquidOptics && liquidEdge > 0.001) {
        // Glass is visible at rest through a continuous optical facet, not a white
        // Fresnel stroke. Directional illumination changes with the curved SDF normal.
        vec2 lightAxis = normalize(vec2(-0.47, -0.88));
        float faceLighting = clamp(dot(sdfNormal, lightAxis) * 0.5 + 0.5, 0.0, 1.0);
        float glancing = pow(liquidEdge, 2.6);
        float specular = glancing * pow(faceLighting, 4.0)
                * (0.09 + 0.07 * interactionActivity) * (1.0 - brightScene * 0.64);
        float internalShade = glancing * (1.0 - faceLighting) * 0.15;
        finalColor *= 1.0 - internalShade;
        vec3 reflectedHue = mix(tint, vec3(0.78, 0.90, 1.0), 0.57);
        finalColor = mix(finalColor, reflectedHue, specular * (specularEnabled ? 1.0 : 0.0));
        // Narrow physical glint is a detail, not the silhouette's primary cue.
        float rimGlint = pow(liquidEdge, 9.0) * pow(faceLighting, 7.0)
                * (0.025 + 0.06 * localPulse) * (1.0 - brightScene * 0.73);
        finalColor = mix(finalColor, vec3(0.89, 0.96, 1.0),
                rimGlint * (specularEnabled ? 1.0 : 0.0));
    }

#ifndef COMBATANT_LIGHT_GLASS
    // The travelling caustic belongs to the glass body, not the SDF rim. Keeping this
    // outside edgeActive preserves the original full-surface sweep/phase animation.
    if (prismActive) {
        vec2 liquidNormalLocal = normalize(vec2(0.72, 0.28) + vec2(-0.28, 0.72) * waveSlope);
        vec2 prismAxis = vec2(liquidNormalLocal.x, -liquidNormalLocal.y);
        vec2 prismSceneOffset = prismAxis * ((1.75 + 6.25 * prismBand) / fbSize);
        vec3 prismScene = vec3(
            texture(u_Texture, clamp(centerUv + prismSceneOffset, vec2(0.001), vec2(0.999))).r,
            cleanColor.g,
            texture(u_Texture, clamp(centerUv - prismSceneOffset, vec2(0.001), vec2(0.999))).b
        );
        vec3 refractedPrism = lookupGlassColor(prismScene, tint);
        finalColor = mix(finalColor, refractedPrism, prismEnvelope * (0.20 + 0.12 * wideRim));

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
        finalColor = mix(finalColor, vec3(0.98, 1.0, 1.0),
                prismCrest * (0.025 + 0.035 * wideRim));
    }
#endif

    // Explicit inner-glow modifier remains independent from prism/rim quality.
    if (innerGlowStrength > 0.001 && innerGlowSizePx > 0.001) {
        float insideDistance = max(-d, 0.0);
        float innerGlowMask = (1.0 - smoothstep(0.0, innerGlowSizePx, insideDistance)) * step(d, 0.0);
        vec3 innerGlowColor = decodePackedRgb(v_Params7.w);
        float glowMix = clamp(innerGlowMask * innerGlowStrength * (0.34 + 0.18 * topLight), 0.0, 0.52);
        finalColor = mix(finalColor, innerGlowColor, glowMix);
    }

    float fresnelAlpha = clamp(v_Params2.z, 0.0, 1.0);
    float baseAlpha = clamp(v_Params2.w, 0.0, 1.0);
    // New optics must not change surface coverage while the refracted image moves.
    // Retain the original scene-adaptive alpha behavior only for legacy Fresnel.
    if (!liquidOptics) {
        baseAlpha = clamp(baseAlpha + brightScene * (0.035 + 0.045 * (1.0 - fresnelMix)), 0.0, 1.0);
    }
    float edgeAlpha = liquidOptics
            ? clamp(liquidEdge * 0.09 + hairline * 0.07, 0.0, 0.16)
            : clamp(fresnel * (0.25 + 0.75 * fresnelMix) + hairline * 0.40, 0.0, 1.0);

    float materialAlpha = mix(baseAlpha, fresnelAlpha, edgeAlpha) * shapeAlpha * v_Color.a;
    float blurLayerAlpha = blurAlpha * shapeAlpha;
    float finalAlpha = materialAlpha + blurLayerAlpha * (1.0 - materialAlpha);
    if (finalAlpha <= 0.001) {
        discard;
    }

    vec3 blurLayerColor = blurColor.rgb;
    // The original frosting layer otherwise overlays the locally clear optical
    // result a second time. Let the SAME prepared scene contribute through both
    // coverage layers in the reactive patch, without changing final alpha.
    float opticalReveal = liquidOptics
            ? clamp(facetTransmission * 1.12
                    + reactiveReveal * clamp(v_Params9.w, 0.0, 1.0) * 0.85,
                    0.0, 0.88)
            : 0.0;
#ifdef COMBATANT_UI_UNDERLAY
    // Never wash out the actual UI underlay with a replacement scene sample.
    blurLayerColor = mix(blurLayerColor, uiUnderlayColor, uiCoverage);
    opticalReveal *= 1.0 - uiCoverage;
#endif
    blurLayerColor = mix(blurLayerColor, centerColor, opticalReveal);
    finalColor = (blurLayerColor * blurLayerAlpha * (1.0 - materialAlpha)
            + finalColor * materialAlpha) / max(finalAlpha, 1e-5);

    // Optional reusable backdrop compositor. NORMAL is bit-for-bit the ordinary glass source.
    // Other modes operate on the destination snapshot, while fixed-function alpha remains only
    // the final Porter-Duff coverage stage.
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


    fragColor = vec4(clamp(finalColor, 0.0, 1.0), finalAlpha);
}
