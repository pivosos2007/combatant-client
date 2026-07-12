#version 330 core

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

out vec4 fragColor;

uniform sampler2D u_Texture;     // clean scene/background source
uniform sampler2D u_BlurTexture; // existing prepared UI blur source

layout (std140) uniform UIBatch {
    vec4 uScreen; // xy = framebuffer, zw = logical
};

vec4 normalizeRadii(vec4 r, vec2 size) {
    float maxR = 0.5 * min(size.x, size.y);
    r = clamp(r, 0.0, maxR);

    float top = r.x + r.y;
    float bottom = r.w + r.z;
    float left = r.x + r.w;
    float right = r.y + r.z;

    float scale = 1.0;
    if (top > size.x && top > 0.0) scale = min(scale, size.x / top);
    if (bottom > size.x && bottom > 0.0) scale = min(scale, size.x / bottom);
    if (left > size.y && left > 0.0) scale = min(scale, size.y / left);
    if (right > size.y && right > 0.0) scale = min(scale, size.y / right);

    return r * scale;
}

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

float decodeDistort(float packedValue, out float cornerSmoothness, out float blurAlpha) {
    // New payload from Renderer2D.packLiquidGlassPayload():
    //   smoothness * 10000 + round(blurAlpha * 100) + distortion
    // Keep the old small-value path so stale meshes or external callers fail soft.
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

const vec3 LUMA_WEIGHTS = vec3(0.2126, 0.7152, 0.0722);

float luminance(vec3 c) {
    return dot(c, LUMA_WEIGHTS);
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

    // Shape must use perspective-correct local coordinates. Screen-space
    // gl_FragCoord is still used below for scene/blur sampling.
    vec2 frag = warpedLocal(v_Local);

    vec2 size = max(v_Rect.zw, vec2(1.0));
    vec2 center = v_Rect.xy + size * 0.5;
    vec2 pos = frag - center;
    vec2 halfSize = size * 0.5 - 1.0;

    vec4 radius = normalizeRadii(v_Params, size);

    float cornerSmoothness;
    float blurAlpha;
    float distortStrength = decodeDistort(max(v_TexCoord.y, 0.0), cornerSmoothness, blurAlpha);

    float d = roundedBoxSDF(pos, halfSize, radius, cornerSmoothness);
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
    float dx = roundedBoxSDF(pos + vec2(nStep, 0.0), halfSize, radius, cornerSmoothness)
             - roundedBoxSDF(pos - vec2(nStep, 0.0), halfSize, radius, cornerSmoothness);
    float dy = roundedBoxSDF(pos + vec2(0.0, nStep), halfSize, radius, cornerSmoothness)
             - roundedBoxSDF(pos - vec2(0.0, nStep), halfSize, radius, cornerSmoothness);
    vec2 sdfNormal = safeNormalize(vec2(dx, dy), safeNormalize(pos, vec2(0.0, -1.0)));
    vec2 uvNormal = vec2(sdfNormal.x, -sdfNormal.y);

    vec2 uv = gl_FragCoord.xy / fbSize;

    // Keep the body of the glass locked to screen UV. Previously the center was
    // displaced along a rect-relative radial vector, so changing a rect's bounds
    // visibly recomposed otherwise stationary scenery.
    float centerDistortPx = distortStrength * min(fbSize.x, fbSize.y) * 0.42;
    float edgeRefraction = smoothstep(0.42, 0.98, edgeGradient);
    edgeRefraction *= edgeRefraction;
    vec2 centerUv = clamp(uv + uvNormal * (centerDistortPx / fbSize) * edgeRefraction, vec2(0.001), vec2(0.999));

    // Rim: sample clean scene outside the SDF boundary. This is the only part that
    // should read as mirror/detail; center remains the existing blur material.
    float mirrorPx = thickness * (1.90 + 2.50 * fresnel) + centerDistortPx * 0.55;
    vec2 mirrorUv = clamp(uv + uvNormal * (mirrorPx / fbSize), vec2(0.001), vec2(0.999));
    vec2 chromaUvR = clamp(mirrorUv + uvNormal * (1.65 / fbSize), vec2(0.001), vec2(0.999));
    vec2 chromaUvB = clamp(mirrorUv - uvNormal * (1.65 / fbSize), vec2(0.001), vec2(0.999));

    vec4 blurColor = texture(u_BlurTexture, centerUv);
    vec4 cleanColor = texture(u_Texture, centerUv);
    vec4 mirrorColor = texture(u_Texture, mirrorUv);
    vec4 blurMirrorColor = texture(u_BlurTexture, mirrorUv);

    vec3 chromaMirror = vec3(
        texture(u_Texture, chromaUvR).r,
        mirrorColor.g,
        texture(u_Texture, chromaUvB).b
    );

    vec3 tint = clamp(v_Color.rgb, 0.0, 1.0);
    float fresnelMix = clamp(v_TexCoord.x, 0.0, 1.0);

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
    finalColor = (blurLayerColor * blurLayerAlpha * (1.0 - materialAlpha) + finalColor * materialAlpha) / max(finalAlpha, 1e-5);

    fragColor = vec4(finalColor, finalAlpha);
}
