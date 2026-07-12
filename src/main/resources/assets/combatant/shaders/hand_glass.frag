#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

in vec2 v_TexCoord;
out vec4 fragColor;

uniform sampler2D u_Src;
uniform sampler2D u_Mask;

layout (std140) uniform HandGlass {
    vec4 uScreen;   // xy = framebuffer size, z = time, w = inner edge width in pixels
    vec4 uTint;     // rgb = glass tint, a = overlay alpha
    vec4 uMaterial; // x = strength, y = refraction pixels, z = edge haze strength, w = frost blur radius pixels
    vec4 uReserved; // x = body frost strength, y = chromatic pixels, z = edge refraction multiplier, w = clarity
};

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

float saturate(float v) {
    return clamp(v, 0.0, 1.0);
}

vec3 saturate(vec3 v) {
    return clamp(v, 0.0, 1.0);
}

float luminance(vec3 c) {
    return dot(c, LUMA);
}

vec2 safeNormalize(vec2 v, vec2 fallback) {
    float len2 = dot(v, v);
    if (len2 <= 1e-6) return fallback;
    return v * inversesqrt(len2);
}

ivec2 clampPixel(ivec2 p) {
    ivec2 size = textureSize(u_Mask, 0);
    return clamp(p, ivec2(0), max(size - ivec2(1), ivec2(0)));
}

ivec2 pixelFromUv(vec2 uv) {
    ivec2 size = textureSize(u_Mask, 0);
    vec2 p = floor(clamp(uv, vec2(0.0), vec2(0.999999)) * vec2(size));
    return clampPixel(ivec2(p));
}

float rawMaskPixel(ivec2 p) {
    vec4 mask = texelFetch(u_Mask, clampPixel(p), 0);
    return max(mask.a, max(mask.r, max(mask.g, mask.b)));
}

float solidMaskPixel(ivec2 p) {
    return step(0.16, rawMaskPixel(p));
}

float maskSupport1(ivec2 p) {
    float s = solidMaskPixel(p);
    s += solidMaskPixel(p + ivec2( 1,  0));
    s += solidMaskPixel(p + ivec2(-1,  0));
    s += solidMaskPixel(p + ivec2( 0,  1));
    s += solidMaskPixel(p + ivec2( 0, -1));
    s += solidMaskPixel(p + ivec2( 1,  1));
    s += solidMaskPixel(p + ivec2(-1,  1));
    s += solidMaskPixel(p + ivec2( 1, -1));
    s += solidMaskPixel(p + ivec2(-1, -1));
    return s;
}

float maskSupport2(ivec2 p) {
    float s = 0.0;
    s += solidMaskPixel(p + ivec2( 2,  0));
    s += solidMaskPixel(p + ivec2(-2,  0));
    s += solidMaskPixel(p + ivec2( 0,  2));
    s += solidMaskPixel(p + ivec2( 0, -2));
    s += solidMaskPixel(p + ivec2( 2,  2));
    s += solidMaskPixel(p + ivec2(-2,  2));
    s += solidMaskPixel(p + ivec2( 2, -2));
    s += solidMaskPixel(p + ivec2(-2, -2));
    return s;
}

float filteredMaskPixel(ivec2 p) {
    float raw = rawMaskPixel(p);
    if (raw < 0.16) return 0.0;

    // Mask is captured from vanilla hand render, so glint/texture sparkle can leave
    // isolated alpha dots. Kill unsupported pixels before the glass material runs.
    float s1 = maskSupport1(p);
    float s2 = maskSupport2(p);
    float keep = step(2.5, s1) * step(0.5, s1 + s2);
    return raw * keep;
}

float maskAt(vec2 uv) {
    return filteredMaskPixel(pixelFromUv(uv));
}

float maskAtOffset(vec2 uv, ivec2 offset) {
    return filteredMaskPixel(pixelFromUv(uv) + offset);
}

vec3 sceneAt(vec2 uv) {
    return texture(u_Src, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
}

float erodeMin8(vec2 uv, float radiusPx) {
    int r = int(clamp(floor(radiusPx + 0.5), 1.0, 32.0));
    ivec2 p = pixelFromUv(uv);
    float m = filteredMaskPixel(p);
    m = min(m, filteredMaskPixel(p + ivec2( r, 0)));
    m = min(m, filteredMaskPixel(p + ivec2(-r, 0)));
    m = min(m, filteredMaskPixel(p + ivec2(0,  r)));
    m = min(m, filteredMaskPixel(p + ivec2(0, -r)));
    m = min(m, filteredMaskPixel(p + ivec2( r,  r)));
    m = min(m, filteredMaskPixel(p + ivec2(-r,  r)));
    m = min(m, filteredMaskPixel(p + ivec2( r, -r)));
    m = min(m, filteredMaskPixel(p + ivec2(-r, -r)));
    return m;
}

vec2 maskNormal(vec2 uv, float radiusPx) {
    int r = int(clamp(floor(radiusPx + 0.5), 1.0, 8.0));
    ivec2 p = pixelFromUv(uv);
    float l = filteredMaskPixel(p + ivec2(-r, 0));
    float rgt = filteredMaskPixel(p + ivec2( r, 0));
    float d = filteredMaskPixel(p + ivec2(0, -r));
    float u = filteredMaskPixel(p + ivec2(0,  r));
    return safeNormalize(vec2(l - rgt, d - u), vec2(0.0, -1.0));
}

vec2 liquidFlow(vec2 uv, float time) {
    vec2 p = uv * vec2(5.2, 3.7);
    float a = sin(p.y * 1.62 + time * 0.42 + sin(p.x * 0.84 + time * 0.18));
    float b = cos(p.x * 1.48 - time * 0.36 + cos(p.y * 0.92 - time * 0.14));
    float c = sin((p.x - p.y) * 0.98 + time * 0.25);
    float d = cos((p.x + p.y) * 0.72 - time * 0.20);
    return vec2(a + c * 0.34 + d * 0.12, b - c * 0.30 + d * 0.10) * 0.5;
}

vec3 frostedScene(vec2 uv, vec2 normal, vec2 texel, float radiusPx) {
    vec2 n = safeNormalize(normal, vec2(0.0, -1.0));
    vec2 t = vec2(-n.y, n.x);
    float r = max(radiusPx, 0.15);

    vec2 n1 = n * texel * r;
    vec2 t1 = t * texel * r;
    vec2 n2 = n * texel * (r * 1.85);
    vec2 t2 = t * texel * (r * 1.55);
    vec2 d1 = (n + t) * texel * (r * 1.15);
    vec2 d2 = (n - t) * texel * (r * 1.15);
    vec2 d3 = (-n + t) * texel * (r * 1.15);
    vec2 d4 = (-n - t) * texel * (r * 1.15);

    vec3 c = sceneAt(uv) * 0.185;
    c += sceneAt(uv + n1) * 0.095;
    c += sceneAt(uv - n1) * 0.095;
    c += sceneAt(uv + t1) * 0.095;
    c += sceneAt(uv - t1) * 0.095;
    c += sceneAt(uv + d1) * 0.070;
    c += sceneAt(uv + d2) * 0.070;
    c += sceneAt(uv + d3) * 0.070;
    c += sceneAt(uv + d4) * 0.070;
    c += sceneAt(uv + n2) * 0.0425;
    c += sceneAt(uv - n2) * 0.0425;
    c += sceneAt(uv + t2) * 0.0425;
    c += sceneAt(uv - t2) * 0.0425;
    c += sceneAt(uv + n2 + t1 * 0.75) * 0.025;
    c += sceneAt(uv + n2 - t1 * 0.75) * 0.025;
    c += sceneAt(uv - n2 + t1 * 0.75) * 0.025;
    c += sceneAt(uv - n2 - t1 * 0.75) * 0.025;
    return c;
}

vec3 glassTone(vec3 scene, vec3 tint, float strength, float haze) {
    float l = max(luminance(scene), 1e-4);
    float peak = max(scene.r, max(scene.g, scene.b));
    float bright = smoothstep(0.42, 0.88, l);
    float glare = smoothstep(0.72, 1.00, peak);

    float targetLuma = mix(l, 0.56 + 0.26 * sqrt(l), saturate(0.42 + haze * 0.48 + strength * 0.025));
    targetLuma *= 1.0 - glare * 0.055;
    vec3 mapped = scene * (targetLuma / l);

    float ml = luminance(mapped);
    float sat = mix(0.82, 1.05, saturate(1.0 - haze * 0.55));
    mapped = vec3(ml) + (mapped - vec3(ml)) * sat;

    float tl = luminance(tint);
    mapped += (tint - vec3(tl)) * (0.055 + strength * 0.018 + haze * 0.050);
    mapped += mix(vec3(1.0), tint, 0.18) * (0.020 + bright * 0.020) * haze;
    return saturate(mapped);
}

void main() {
    vec2 fbSize = max(uScreen.xy, vec2(1.0));
    vec2 texel = 1.0 / fbSize;
    vec2 uv = v_TexCoord;

    vec3 base = sceneAt(uv);
    float mask = saturate(maskAt(uv));

    if (mask <= 0.001) {
        fragColor = vec4(base, 1.0);
        return;
    }

    float time = uScreen.z;
    float edgeWidthPx = clamp(uScreen.w, 3.0, 28.0);
    float strength = max(uMaterial.x, 0.0);
    float refractionPx = max(uMaterial.y, 0.0);
    float hazeStrength = saturate(uMaterial.z);
    float frostBlurPx = max(uMaterial.w, 0.0);
    float bodyFrost = saturate(uReserved.x);
    float chromaPxBase = max(uReserved.y, 0.0);
    float edgeRefractionMul = max(uReserved.z, 0.0);
    float clarity = saturate(uReserved.w);
    vec3 tint = saturate(uTint.rgb);
    float alpha = saturate(uTint.a);

    // Inner silhouette zones only. This is a fog/refraction band, not legacy outline.
    float erodedTight = erodeMin8(uv, max(1.5, edgeWidthPx * 0.28));
    float erodedMid = erodeMin8(uv, max(2.5, edgeWidthPx * 0.62));
    float erodedWide = erodeMin8(uv, edgeWidthPx);
    float tightEdge = saturate((mask - erodedTight) * 3.10);
    float midEdge = saturate((mask - erodedMid) * 2.15);
    float broadEdge = saturate((mask - erodedWide) * 1.55);
    float edge = saturate(max(tightEdge, max(midEdge * 0.86, broadEdge * 0.72)));
    float body = smoothstep(0.03, 0.98, mask);

    vec2 maskN = maskNormal(uv, mix(1.4, 6.6, edge));
    vec2 flowN = safeNormalize(liquidFlow(uv, time), maskN);
    vec2 normal = safeNormalize(mix(flowN, maskN, saturate(0.48 + edge * 0.48)), maskN);

    float pulse = 0.84 + 0.16 * sin(time * 0.52 + uv.y * 9.0 + uv.x * 5.0);
    float bodyRefraction = refractionPx * (0.14 + bodyFrost * 0.18) * body;
    float edgeRefraction = refractionPx * edgeRefractionMul * (0.34 + edge * 1.18) * pulse;
    vec2 refractUv = clamp(uv + normal * texel * (bodyRefraction + edgeRefraction), vec2(0.001), vec2(0.999));

    float blurPx = frostBlurPx * (0.78 + bodyFrost * 0.34 + edge * 0.58);
    vec3 blurred = frostedScene(refractUv, normal, texel, blurPx);
    vec3 widerBlur = frostedScene(refractUv + liquidFlow(uv * 0.56 + 0.17, time) * texel * blurPx * 0.42,
                                  normal, texel, blurPx * (1.48 + edge * 0.34));
    vec3 clean = sceneAt(refractUv);

    float edgeHaze = saturate(edge * hazeStrength);
    float fogAmount = saturate(bodyFrost * body * (0.64 + 0.11 * strength) + edgeHaze * 0.74);
    vec3 glassScene = mix(clean, blurred, fogAmount);
    glassScene = mix(glassScene, widerBlur, saturate((edgeHaze * 0.56 + bodyFrost * 0.30) * (1.0 - clarity * 0.50)));

    vec3 glass = glassTone(glassScene, tint, strength, saturate(fogAmount + edgeHaze * 0.45));

    float chromaPx = chromaPxBase * (0.28 + edge * 1.32 + bodyFrost * 0.18);
    vec2 chroma = normal * texel * chromaPx;
    vec3 prism = vec3(
        sceneAt(refractUv + chroma).r,
        frostedScene(refractUv, normal, texel, blurPx * 0.82).g,
        sceneAt(refractUv - chroma).b
    );
    prism = glassTone(prism, tint, strength + 0.28, saturate(edgeHaze + 0.22));
    glass = mix(glass, prism, saturate(edge * 0.26 + tightEdge * 0.14));

    float topLight = saturate(dot(normal, normalize(vec2(-0.30, 0.96))) * 0.5 + 0.5);
    float sceneLuma = luminance(glassScene);
    float brightScene = smoothstep(0.50, 0.90, sceneLuma);

    // Smooth liquid movement only. No thresholded caustic/sparkle pattern: it was
    // visible as isolated white pixels on enchanted/high-contrast item textures.
    float waveA = 0.5 + 0.5 * sin((uv.x + normal.x * 0.05) * 7.5 + time * 0.52);
    float waveB = 0.5 + 0.5 * cos((uv.y - normal.y * 0.04) * 5.2 - time * 0.38);
    float liquidSheen = waveA * waveB;
    glass += mix(tint, vec3(1.0), 0.58) * liquidSheen * body * (1.0 - edge * 0.45) * (0.010 + 0.014 * saturate(strength));

    vec3 fogTint = mix(vec3(luminance(glass)), mix(vec3(1.0), tint, 0.16), 0.64);
    float milkyFog = saturate(edgeHaze * (0.38 + 0.28 * topLight) + bodyFrost * body * 0.17);
    milkyFog *= 1.0 - brightScene * 0.28;
    glass = mix(glass, fogTint, milkyFog);

    vec3 edgeHighlight = mix(vec3(1.0), tint, 0.10 + brightScene * 0.06);
    float highlight = saturate((tightEdge * 0.10 + midEdge * 0.075) * (0.74 + 0.30 * topLight) * (1.0 - brightScene * 0.45));
    glass = mix(glass, edgeHighlight, highlight);

    glass = saturate(glass);

    float bodyMix = body * alpha * (0.30 + 0.12 * saturate(strength) + bodyFrost * 0.18);
    float edgeMix = edge * alpha * (0.56 + 0.18 * saturate(strength)) * (0.82 + 0.18 * topLight);
    float finalMix = saturate(bodyMix + edgeMix);
    finalMix *= mix(1.0, 0.82, clarity);

    vec3 outColor = mix(base, glass, finalMix);
    fragColor = vec4(saturate(outColor), 1.0);
}
