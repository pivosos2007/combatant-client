#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 *
 * Visual technique credits:
 * - Flame reference by Anatole Duprat (XT95), 2013
 *   Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported.
 * - Procedural water reference by afl_ext, 2017-2024, MIT License.
 * - Crystal hover uses a lightweight continuous faceted surface inspired by Xenolith-style crystal glazing.
 */

in vec4 v_Local;
in vec4 v_Color;
in vec4 v_Rect;
in vec4 v_CornerModes;
in vec4 v_CornerExtentX;
in vec4 v_CornerExtentY;
in vec4 v_EdgeModes;
in vec4 v_EdgeData;

out vec4 fragColor;

layout (std140) uniform UIBatch {
    vec4 uScreen;
};

const float PI = 3.14159265359;

float saturate(float x) {
    return clamp(x, 0.0, 1.0);
}

mat2 rotate2(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat2(c, s, -s, c);
}

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float noise21(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float noise31(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);

    float n000 = hash31(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash31(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash31(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash31(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash31(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash31(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash31(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash31(i + vec3(1.0, 1.0, 1.0));

    float z0 = mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y);
    float z1 = mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y);
    return mix(z0, z1, u.z);
}

float fbm21(vec2 p) {
    float sum = 0.0;
    float weight = 0.52;
    mat2 basis = mat2(0.80, 0.60, -0.60, 0.80);
    for (int i = 0; i < 5; i++) {
        sum += noise21(p) * weight;
        p = basis * p * 2.03 + vec2(13.17, 7.31);
        weight *= 0.47;
    }
    return sum;
}

float fbm31(vec3 p) {
    float sum = 0.0;
    float weight = 0.54;
    for (int i = 0; i < 4; i++) {
        sum += noise31(p) * weight;
        p = p.yzx * 2.01 + vec3(11.7, 5.3, 17.1);
        weight *= 0.48;
    }
    return sum;
}

float ridged21(vec2 p) {
    float sum = 0.0;
    float weight = 0.55;
    mat2 basis = mat2(0.86, 0.51, -0.51, 0.86);
    for (int i = 0; i < 4; i++) {
        float n = 1.0 - abs(noise21(p) * 2.0 - 1.0);
        sum += n * n * weight;
        p = basis * p * 2.12 + vec2(9.2, 19.7);
        weight *= 0.48;
    }
    return sum;
}

vec2 warpedLocal(vec4 local) {
    float invW = abs(local.z) > 0.000001 ? local.z : 1.0;
    return local.xy / invW;
}

float easeOutCubic(float x) {
    x = saturate(x);
    float k = 1.0 - x;
    return 1.0 - k * k * k;
}

vec3 acesApprox(vec3 x) {
    x = max(x, vec3(0.0));
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

float roundedBoxSDF(vec2 p, vec2 halfSize, float radius) {
    float r = clamp(radius, 0.0, min(halfSize.x, halfSize.y));
    vec2 q = abs(p) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float flameDistance(vec3 world, float time, float seed) {
    const float spacing = 0.74;
    float cellId = floor((world.x + spacing * 0.5) / spacing);
    vec3 p = world;
    p.x = mod(world.x + spacing * 0.5, spacing) - spacing * 0.5;

    float cellRnd = hash11(cellId + seed * 67.0);
    float height = 0.30 + cellRnd * 0.22;
    float fromBottom = 0.5 - p.y;
    float rise = saturate(fromBottom / max(height, 0.001));

    p.x += (cellRnd - 0.5) * 0.13;
    p.x += sin(fromBottom * 6.0 - time * 1.65 + cellRnd * 13.0) * (0.015 + rise * 0.040);

    float tongueRadius = mix(0.205 + cellRnd * 0.030, 0.018, pow(rise, 0.76));
    float radialDistance = length(vec2(p.x, p.z)) - tongueRadius;
    float heightDistance = (fromBottom - height) * 0.52;
    float tongueDistance = max(radialDistance, heightDistance);

    float baseDistance = max(abs(p.z) - 0.23, abs(p.y - 0.445) - 0.055);
    float body = min(tongueDistance, baseDistance);

    vec3 flow = vec3(p.x * 2.2, p.y * 2.7 - time * 1.45, p.z * 2.1);
    float coarse = fbm31(flow + vec3(cellRnd * 19.0, seed * 7.0, 0.0));
    float detail = noise31(flow * 3.1 + vec3(0.0, -time * 2.7, seed * 31.0));
    body += (coarse - 0.54) * mix(0.040, 0.105, rise);
    body += (detail - 0.5) * 0.018 * rise;
    return body;
}

vec4 flameSurface(vec2 p, float time, float reveal, float seed, vec3 c0, vec3 c1, vec3 hi) {
    float rayT = -1.18;
    float glow = 0.0;
    float interior = 0.0;
    float closest = 10.0;
    vec3 hitPosition = vec3(p, 0.0);

    for (int i = 0; i < 30; i++) {
        vec3 position = vec3(p, rayT);
        float distanceToFlame = flameDistance(position, time, seed);
        float shell = exp(-abs(distanceToFlame) * 34.0);
        glow += (1.0 - glow) * shell * 0.054;
        interior += (1.0 - interior) * (1.0 - smoothstep(-0.020, 0.042, distanceToFlame)) * 0.086;

        if (abs(distanceToFlame) < closest) {
            closest = abs(distanceToFlame);
            hitPosition = position;
        }

        rayT += clamp(abs(distanceToFlame) * 0.62, 0.018, 0.115);
        if (rayT > 1.24) break;
    }

    float coreNoise = ridged21(vec2(hitPosition.x * 1.5, hitPosition.y * 3.8 - time * 1.7) + seed * 13.0);
    float heat = saturate(interior * 0.66 + coreNoise * interior * 0.20);

    vec2 sparkGrid = vec2(p.x * 3.8 - time * 0.34, p.y * 6.2 + time * 1.72);
    vec2 sparkCell = floor(sparkGrid);
    vec2 sparkUv = fract(sparkGrid) - 0.5;
    vec2 sparkOffset = hash22(sparkCell + seed * 29.0) - 0.5;
    sparkUv -= sparkOffset * 0.42;
    vec2 sparkDistance = vec2(sparkUv.x, max(abs(sparkUv.y) - 0.13, 0.0));
    float spark = 1.0 - smoothstep(0.022, 0.072, length(sparkDistance));
    spark *= step(0.875, hash21(sparkCell + seed * 47.0));
    spark *= 0.38 + 0.62 * hash21(sparkCell + floor(time * 7.0));

    vec2 smokeP = vec2(
        p.x * 0.72 + sin(p.y * 3.1 - time * 0.24) * 0.16,
        p.y * 2.35 + time * 0.18
    );
    float smokeWarp = fbm21(smokeP + vec2(seed * 17.0, 0.0));
    float smokeCurl = ridged21(rotate2(0.48) * smokeP * 1.45 + vec2(-time * 0.10, seed * 23.0));
    float smoke = smoothstep(0.53, 0.79, smokeWarp * 0.78 + smokeCurl * 0.25);
    float smokeHeight = 1.0 - smoothstep(0.12, 0.52, p.y);
    smoke *= smokeHeight;

    vec3 hotColor = mix(c1 * 1.12, hi, 0.12);
    vec3 flameColor = mix(c0 * 0.46, c1 * 1.08, saturate(glow * 0.88 + interior * 0.46));
    flameColor = mix(flameColor, hotColor, heat * 0.30);
    flameColor += c0 * glow * 0.30;

    float textBand = exp(-p.y * p.y * 30.0);
    float readability = 1.0 - textBand * 0.62;
    float revealGain = easeOutCubic(reveal);
    float flameAlpha = (pow(saturate(glow * 1.18), 1.52) * 0.58 + interior * 0.19) * readability;

    float smokeReadability = 1.0 - textBand * 0.74;
    float smokeAlpha = smoke * (0.045 + smokeCurl * 0.075) * smokeReadability;
    vec3 smokeColor = mix(vec3(0.115, 0.125, 0.145), c0 * 0.48, 0.42 + smokeCurl * 0.16);

    float sparkEnvelope = mix(0.34, 1.0, smoothstep(-0.48, 0.46, p.y));
    float sparkAlpha = spark * sparkEnvelope * 0.52;
    vec3 sparkColor = mix(c1 * 1.10, hi * 1.16, 0.48);

    flameAlpha *= revealGain;
    smokeAlpha *= revealGain;
    sparkAlpha *= revealGain;

    float fireAndSmokeAlpha = flameAlpha + smokeAlpha * (1.0 - flameAlpha);
    vec3 fireAndSmokeColor = (
        flameColor * flameAlpha
        + smokeColor * smokeAlpha * (1.0 - flameAlpha)
    ) / max(fireAndSmokeAlpha, 0.0001);

    float alpha = fireAndSmokeAlpha + sparkAlpha * (1.0 - fireAndSmokeAlpha);
    vec3 color = (
        fireAndSmokeColor * fireAndSmokeAlpha
        + sparkColor * sparkAlpha * (1.0 - fireAndSmokeAlpha)
    ) / max(alpha, 0.0001);
    return vec4(color, saturate(alpha));
}

#moj_import <combatant:ui_module_hover_movement.glsl>
#moj_import <combatant:ui_module_hover_crystal.glsl>
#moj_import <combatant:ui_module_hover_water.glsl>

vec4 plasmaSurface(vec2 p, float time, float reveal, float seed, vec3 c0, vec3 c1, vec3 hi) {
    vec2 q = p * vec2(0.92, 2.4);
    float warpA = fbm21(q + vec2(time * 0.13, seed * 17.0));
    float warpB = fbm21(rotate2(0.71) * q * 1.7 + vec2(-time * 0.19, seed * 31.0));
    vec2 warped = q + vec2(warpA - 0.5, warpB - 0.5) * 1.15;
    float veins = ridged21(warped * 1.34 - vec2(time * 0.22, 0.0));
    float filament = pow(saturate(veins), 4.2);
    float field = saturate(warpA * 0.56 + warpB * 0.52);
    float pulse = 0.5 + 0.5 * sin(time * 1.4 + field * 8.0 + seed * 23.0);
    float body = smoothstep(0.54, 0.82, field * 0.64 + filament * 0.62);

    vec3 spectral = 0.5 + 0.5 * cos(vec3(0.0, 2.1, 4.2) + field * 5.2 + time * 0.15);
    vec3 color = mix(c0 * 0.48, c1 * 1.12, field);
    color = mix(color, spectral * c1 * 1.18, 0.24 + pulse * 0.12);
    color = mix(color, hi * 1.48, saturate(filament * 0.78 + pulse * filament * 0.42));

    float alpha = body * (field * 0.22 + filament * 0.58 + pulse * filament * 0.12);
    alpha *= easeOutCubic(reveal);
    return vec4(color, saturate(alpha));
}

void main() {
    vec2 frag = warpedLocal(v_Local);
    vec2 size = max(v_Rect.zw, vec2(1.0));
    vec2 uv = (frag - v_Rect.xy) / size;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) discard;

    float mode = floor(v_CornerModes.y + 0.5);
    float reveal = saturate(v_CornerModes.z);
    float intensity = max(v_CornerModes.w, 0.0);
    if (reveal <= 0.001 || intensity <= 0.001) discard;

    float aspect = size.x / max(size.y, 1.0);
    vec2 p = vec2((uv.x - 0.5) * aspect, uv.y - 0.5);
    float time = v_EdgeModes.x;
    float seed = fract(v_EdgeModes.w + 0.173);

    vec3 c0 = v_Color.rgb;
    vec3 c1 = v_CornerExtentX.rgb;
    vec3 hi = v_CornerExtentY.rgb;

    vec2 logicalPixel = uScreen.zw / max(uScreen.xy, vec2(1.0));
    float aa = max(max(logicalPixel.x, logicalPixel.y) * 1.35, 0.55);
    vec2 localCenter = v_Rect.xy + size * 0.5;
    vec2 shapePosition = frag - localCenter;
    vec2 halfSize = size * 0.5 - vec2(0.75);
    float radius = clamp(v_CornerModes.x, 0.0, min(halfSize.x, halfSize.y));
    float shapeDistance = roundedBoxSDF(shapePosition, halfSize, radius);
    float shapeAlpha = 1.0 - smoothstep(-aa * 0.5, aa, shapeDistance);
    if (shapeAlpha <= 0.001) discard;

    vec4 material;
    if (mode < 0.5) {
        material = flameSurface(p, time, reveal, seed, c0, c1, hi);
    } else if (mode < 1.5) {
        material = movementSurface(p, time, reveal, seed, c0, c1, hi);
    } else if (mode < 2.5) {
        material = crystalSurface(p, time, reveal, seed, c0, c1, hi);
    } else if (mode < 3.5) {
        material = waterSurface(p, time, reveal, seed, c0, c1, hi);
    } else {
        material = plasmaSurface(p, time, reveal, seed, c0, c1, hi);
    }

    vec2 mouseUv = clamp(v_EdgeModes.yz, vec2(0.0), vec2(1.0));
    vec2 mouseP = vec2((mouseUv.x - 0.5) * aspect, mouseUv.y - 0.5);
    vec2 mouseDelta = p - mouseP;
    float mouseDistance2 = dot(mouseDelta, mouseDelta);
    float mouseGlint = 1.0 / (1.0 + mouseDistance2 * 5.8);
    mouseGlint *= mouseGlint;
    material.rgb += hi * mouseGlint * 0.075 * material.a;

    // Cheap row-rim lighting. The previous version reconstructed a rounded-box
    // normal with four extra SDF evaluations for every hover pixel. For this
    // shallow module-row surface, a vertical light bias is visually equivalent
    // and substantially cheaper.
    float topLight = 1.0 - uv.y;
    float insideDistance = max(-shapeDistance, 0.0);
    float edgeGradient = 1.0 - saturate(insideDistance / max(radius * 0.92, 2.5));
    float hairline = 1.0 - smoothstep(0.0, aa * 1.65, abs(shapeDistance));
    float glassRim = saturate(edgeGradient * edgeGradient * (0.060 + topLight * 0.070)
                            + hairline * (0.105 + topLight * 0.115));
    glassRim *= easeOutCubic(reveal);

    if (mode >= 1.5 && mode < 3.5) {
        // Crystal/water are already low-energy translucent materials. Avoid the
        // full ACES rational curve here; it both costs ALU and muddies facet/color separation.
        material.rgb = clamp(material.rgb * 1.06, 0.0, 1.0);
    } else {
        material.rgb = acesApprox(material.rgb * 1.28);
    }
    vec3 rimColor = mix(hi, c1, 0.16 + (1.0 - topLight) * 0.10);
    float refractiveRimScale = (mode >= 1.5 && mode < 3.5) ? 0.68 : 1.0;
    float rimMix = saturate(glassRim * refractiveRimScale * (0.52 + 0.48 * (1.0 - material.a)));
    material.rgb = mix(material.rgb, rimColor, rimMix);

    float materialAlpha = saturate(material.a * intensity * v_Color.a) * shapeAlpha;
    float rimAlpha = glassRim * shapeAlpha * v_Color.a;
    float finalAlpha = saturate(materialAlpha + rimAlpha * (1.0 - materialAlpha));
    if (finalAlpha <= 0.001) discard;

    fragColor = vec4(material.rgb, finalAlpha);
}
