#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

in vec4 v_Local;
in vec4 v_Color;
in vec4 v_Rect;
in vec4 v_Params;
in vec4 v_Params2;
in vec4 v_Params3;
in vec4 v_Params4;
in vec4 v_Params5;
in vec4 v_Params6;

out vec4 fragColor;
uniform sampler2D u_Texture;     // clean menu background
uniform sampler2D u_BlurTexture; // prepared Dual Kawase blur

layout (std140) uniform UIBatch {
    vec4 uScreen;
    vec4 uLayer;
};

const float SQRT3 = 1.73205080757;
const float INV_SQRT3 = 0.57735026919;
const float COS30 = 0.86602540378;

const float HONEYCOMB_BLUR_ALPHA = 0.985;
const float STRUCTURE_RECOVERY = 0.035;

vec2 warpedLocal(vec4 local) {
    float invW = abs(local.z) > 0.000001 ? local.z : 1.0;
    return local.xy / invW;
}

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

float cellNoise(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

vec2 safeNormalize(vec2 value, vec2 fallback) {
    float len2 = dot(value, value);
    if (len2 <= 1e-6) return fallback;
    return value * inversesqrt(len2);
}

void nearestPointyHex(vec2 p, float radius, out vec2 cell) {
    float qf = (SQRT3 * p.x / 3.0 - p.y / 3.0) / radius;
    float rf = (2.0 * p.y / 3.0) / radius;
    vec3 cube = vec3(qf, -qf - rf, rf);
    vec3 roundedCube = floor(cube + 0.5);
    vec3 error = abs(roundedCube - cube);

    if (error.x > error.y && error.x > error.z) {
        roundedCube.x = -roundedCube.y - roundedCube.z;
    } else if (error.y > error.z) {
        roundedCube.y = -roundedCube.x - roundedCube.z;
    } else {
        roundedCube.z = -roundedCube.x - roundedCube.y;
    }

    float q = roundedCube.x;
    float r = roundedCube.z;
    cell = vec2(radius * SQRT3 * (q + r * 0.5), radius * 1.5 * r);
}

float pointyHexDistance(vec2 p, float radius) {
    p = abs(p);
    float verticalSide = p.x - COS30 * radius;
    float diagonalSide = (p.x * INV_SQRT3 + p.y - radius) * COS30;
    return max(verticalSide, diagonalSide);
}

bool insideChamferedCutout(vec2 p, vec4 rect, float cut) {
    if (rect.z <= 0.0 || rect.w <= 0.0) return false;
    vec2 halfSize = rect.zw * 0.5;
    vec2 local = abs(p - (rect.xy + halfSize));
    if (local.x > halfSize.x || local.y > halfSize.y) return false;
    float safeCut = clamp(cut, 0.0, min(halfSize.x, halfSize.y));
    return local.x + local.y <= halfSize.x + halfSize.y - safeCut;
}

void main() {
    vec2 position = warpedLocal(v_Local);

    // Optional menu-window hole. This is evaluated before cursor lighting/refraction so the
    // honeycomb cannot glow through the chamfered background corners.
    if (v_Params4.w > 0.5 && insideChamferedCutout(position, v_Params5, v_Params4.z)) {
        discard;
    }

    float radius = max(v_Params.x, 1.0);
    float rimWidth = max(v_Params.z, 0.45);
    float opacity = saturate(v_Params.w);

    vec2 cellCenter;
    nearestPointyHex(position - v_Params4.xy, radius, cellCenter);
    vec2 local = position - v_Params4.xy - cellCenter;
    float d = pointyHexDistance(local, radius);

    float logicalPixel = max(max(uScreen.z / max(uScreen.x, 1.0),
                                 uScreen.w / max(uScreen.y, 1.0)), 0.0001);
    float aa = max(logicalPixel, max(fwidth(d) * 0.80, 0.0001));
    float inside = 1.0 - smoothstep(-aa, aa, d);
    if (inside <= 0.001) discard;

    // The honeycomb does not need the full generic liquid-glass stack.
    // Use small edge refraction, dense blur and a thin cursor-lit rim.
    float normalStep = max(logicalPixel, 0.75);
    float dx = pointyHexDistance(local + vec2(normalStep, 0.0), radius)
             - pointyHexDistance(local - vec2(normalStep, 0.0), radius);
    float dy = pointyHexDistance(local + vec2(0.0, normalStep), radius)
             - pointyHexDistance(local - vec2(0.0, normalStep), radius);
    vec2 sdfNormal = safeNormalize(vec2(dx, dy), safeNormalize(local, vec2(0.0, -1.0)));
    vec2 uvNormal = vec2(sdfNormal.x, -sdfNormal.y);

    float edgeWidth = max(rimWidth * 3.0, 3.4);
    float edge = 1.0 - smoothstep(0.0, edgeWidth, abs(d));
    float wire = 1.0 - smoothstep(max(aa * 0.20, rimWidth * 0.10),
                                  max(aa * 1.15, rimWidth * 0.78), abs(d));

    float lightRadius = max(v_Params2.z, radius * 2.0);
    vec2 cursorVector = position - v_Params2.xy;
    float cursorDistance = length(cursorVector);
    float cursor = 1.0 - smoothstep(lightRadius * 0.16, lightRadius, cursorDistance);
    cursor = cursor * cursor * (3.0 - 2.0 * cursor);

    vec2 framebufferSize = max(uScreen.xy, vec2(1.0));
    vec2 logicalSize = max(uScreen.zw, vec2(1.0));
    vec2 uv = vec2(
        position.x / logicalSize.x,
        1.0 - position.y / logicalSize.y
    );

    // Only a restrained boundary bend. No mirror/chromatic/lens stack here.
    float refractionPx = edge * (1.10 + cursor * 1.75);
    vec2 refractedUv = clamp(uv + uvNormal * (refractionPx / framebufferSize),
                             vec2(0.001), vec2(0.999));

    vec3 blurredScene = texture(u_BlurTexture, refractedUv).rgb;
    vec3 cleanScene = texture(u_Texture, refractedUv).rgb;

    int materialMode = int(floor(v_Params6.x + 0.5));
    if (materialMode == 2) {
        vec3 detail = cleanScene - blurredScene;
        float recovery = STRUCTURE_RECOVERY + edge * 0.010 + cursor * edge * 0.008;
        vec3 body = clamp(blurredScene + detail * recovery, 0.0, 1.0);
        vec3 tint = clamp(v_Color.rgb, 0.0, 1.0);
        body = mix(body, tint, 0.012);
        vec3 coolRim = mix(tint, vec3(0.96, 0.985, 1.0), 0.82);
        vec3 warmRim = mix(v_Params3.rgb, vec3(1.0, 0.92, 0.60), 0.15);
        vec3 rimColor = mix(coolRim, warmRim, cursor * saturate(v_Params3.a));
        float rimAmount = saturate(wire * (0.16 + cursor * 0.34));
        vec3 material = mix(body, rimColor, rimAmount);
        float blurAlpha = HONEYCOMB_BLUR_ALPHA * inside * opacity;
        float rimAlpha = wire * opacity * (0.10 + cursor * 0.18);
        float alpha = blurAlpha + rimAlpha * (1.0 - blurAlpha);
        if (alpha <= 0.001) discard;
        vec3 composed = (material * blurAlpha
                + rimColor * rimAlpha * (1.0 - blurAlpha)) / max(alpha, 1e-5);
        fragColor = vec4(clamp(composed, 0.0, 1.0), alpha);
        return;
    }

    vec3 detail = cleanScene - blurredScene;
    float blurMix = saturate(v_Params6.y);
    float sceneDetail = saturate(v_Params6.z);
    float highlightCompression = saturate(v_Params2.w);
    float tintAbsorption = saturate(v_Params6.w);
    vec3 body = mix(cleanScene, blurredScene, blurMix) + detail * sceneDetail;

    vec3 tint = clamp(v_Color.rgb, 0.0, 1.0);
    float ceramic = materialMode == 1 ? 1.0 : 0.0;
    body = mix(body, tint * mix(0.34, 0.62, ceramic), tintAbsorption);

    vec2 lightDirection = safeNormalize(vec2(-0.42, -0.90)
            + safeNormalize(-cursorVector, vec2(0.0)) * cursor * 0.38, vec2(0.0, -1.0));
    float bevelLight = dot(sdfNormal, lightDirection) * 0.5 + 0.5;
    // An inset machined cell is a surface, not a thin outline on top of PNG.
    // Keep the original scene detail but make the SDF bevel/AO and grain visible
    // even when the backdrop contains high-frequency photographic detail.
    float insideDistance = max(-d, 0.0);
    float bevelBand = 1.0 - smoothstep(0.0, edgeWidth * 1.65, insideDistance);
    float cavityShade = 1.0 - smoothstep(0.0, edgeWidth * 3.5, insideDistance);
    float signedLight = dot(sdfNormal, lightDirection);
    float bevelIllumination = bevelBand * signedLight * mix(0.085, 0.055, ceramic);
    body *= 1.0 - cavityShade * mix(0.14, 0.075, ceramic) + bevelIllumination;

    float cellSeed = cellNoise(cellCenter * 0.031 + vec2(5.19, 9.77));
    float grain = cellNoise(floor((local + cellCenter * 0.17) * mix(0.46, 0.31, ceramic))) - 0.5;
    float grainAttenuation = 1.0 - smoothstep(1.5, 3.5, fwidth(local.x) + fwidth(local.y));
    float scratchPhase = local.x * 0.78 + local.y * 0.22 + cellSeed * 6.28318;
    float scratchFilter = 1.0 - smoothstep(0.55, 1.25, fwidth(scratchPhase));
    float satinFiber = sin(scratchPhase) * scratchFilter;
    body *= 1.0 + (cellSeed - 0.5) * mix(0.040, 0.020, ceramic);
    body += grain * grainAttenuation * mix(0.043, 0.016, ceramic)
            + satinFiber * mix(0.011, 0.0, ceramic);

    vec3 rimColor = mix(tint, v_Params3.rgb, cursor * saturate(v_Params3.a));
    float sourceLuma = luminance(cleanScene);
    float rimLimit = mix(0.78, 0.56, highlightCompression) + sourceLuma * 0.12;
    rimColor *= min(1.0, rimLimit / max(luminance(rimColor), 0.0001));

    float rimAmount = saturate(wire * (0.06 + cursor * 0.18));
    vec3 material = mix(body, rimColor, rimAmount);
    float peakLimit = mix(0.97, 0.83, highlightCompression);
    float materialLuma = luminance(material);
    material *= min(1.0, peakLimit / max(materialLuma, 0.0001));

    float bodyAlpha = inside * opacity * mix(0.82, 0.94, ceramic);
    float rimAlpha = wire * opacity * (0.04 + cursor * 0.10);
    float alpha = bodyAlpha + rimAlpha * (1.0 - bodyAlpha);
    if (alpha <= 0.001) discard;

    vec3 composed = (material * bodyAlpha
            + rimColor * rimAlpha * (1.0 - bodyAlpha)) / max(alpha, 1e-5);
    fragColor = vec4(clamp(composed, 0.0, 1.0), alpha);
}
