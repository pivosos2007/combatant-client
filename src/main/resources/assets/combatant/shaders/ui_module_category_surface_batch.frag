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

    vec2 emberGrid = vec2(p.x * 4.8 - time * 0.52, p.y * 7.0 - time * 2.2);
    vec2 emberCell = floor(emberGrid);
    vec2 emberUv = fract(emberGrid) - 0.5;
    vec2 emberOffset = hash22(emberCell + seed * 29.0) - 0.5;
    float ember = 1.0 - smoothstep(0.025, 0.095, length(emberUv - emberOffset * 0.48));
    ember *= step(0.982, hash21(emberCell + seed * 47.0));

    vec3 hotColor = mix(c1 * 1.12, hi, 0.12);
    vec3 color = mix(c0 * 0.46, c1 * 1.08, saturate(glow * 0.88 + interior * 0.46));
    color = mix(color, hotColor, heat * 0.30);
    color += c0 * glow * 0.30;
    color = mix(color, hi, ember * 0.44);

    float textBand = exp(-p.y * p.y * 30.0);
    float readability = 1.0 - textBand * 0.62;
    float alpha = pow(saturate(glow * 1.18), 1.52) * 0.58 + interior * 0.19 + ember * 0.36;
    alpha *= readability * easeOutCubic(reveal);
    return vec4(color, saturate(alpha));
}

float snowLayer(vec2 p, float time, float seed, float scale, float speed, float roundness, float densityThreshold) {
    vec2 windP = rotate2(-0.19) * p;
    vec2 q = windP * scale + vec2(time * speed, -time * speed * 0.16);
    vec2 cell = floor(q);
    vec2 f = fract(q) - 0.5;
    vec2 rnd = hash22(cell + vec2(seed * 37.0, seed * 61.0));
    f -= (rnd - 0.5) * 0.58;

    float angle = mix(-0.20, 0.20, rnd.x);
    f = rotate2(angle) * f;
    float halfLength = mix(0.08, 0.27, rnd.y);
    float thickness = mix(0.018, 0.052, hash21(cell + seed * 17.0));

    vec2 d = vec2(max(abs(f.x) - halfLength, 0.0), f.y);
    float streak = 1.0 - smoothstep(thickness, thickness * 2.1, length(d));
    float flake = 1.0 - smoothstep(thickness * 0.8, thickness * 2.2, length(f));
    return mix(streak, flake, roundness) * step(densityThreshold, hash21(cell + seed * 83.0));
}

vec4 blizzardSurface(vec2 p, float time, float reveal, float seed, vec3 c0, vec3 c1, vec3 hi) {
    vec2 flowP = rotate2(-0.19) * p;
    float broad = fbm21(flowP * vec2(0.72, 2.10) + vec2(time * 0.36, seed * 11.0));
    float torn = ridged21(flowP * vec2(1.30, 3.60) + vec2(time * 0.72, -time * 0.15));
    float mist = smoothstep(0.57, 0.84, broad * 0.74 + torn * 0.22);

    float farSnow = snowLayer(p, time, seed + 1.7, 2.6, 0.58, 0.24, 0.72);
    float midSnow = snowLayer(p, time, seed + 4.1, 4.8, 0.92, 0.16, 0.82);
    float nearSnow = snowLayer(p, time, seed + 8.9, 7.4, 1.34, 0.62, 0.92);
    float snow = farSnow * 0.24 + midSnow * 0.39 + nearSnow * 0.58;

    float iceGrain = ridged21(p * vec2(3.2, 7.0) + vec2(seed * 17.0, time * 0.09));
    float frost = smoothstep(0.76, 0.98, iceGrain) * (0.22 + mist * 0.46);
    float whiteout = saturate(mist * 0.46 + snow * 0.72 + frost * 0.20);

    vec3 color = mix(c0 * 0.44, c1 * 0.92, saturate(mist * 0.68 + torn * 0.14));
    color = mix(color, hi * 1.08, saturate(snow * 0.68 + frost * 0.31));
    color += hi * whiteout * 0.075;

    float textBand = exp(-p.y * p.y * 28.0);
    float readability = 1.0 - textBand * 0.56;
    float alpha = mist * (0.095 + torn * 0.075) + snow * 0.48 + frost * mist * 0.085;
    alpha *= readability * easeOutCubic(reveal);
    return vec4(color, saturate(alpha));
}

float octahedronSDF(vec3 p, vec3 scale) {
    vec3 safeScale = max(scale, vec3(0.015));
    vec3 q = p / safeScale;
    return (dot(abs(q), vec3(1.0)) - 1.0) * min(safeScale.x, min(safeScale.y, safeScale.z)) * 0.82;
}

float crystalScene(vec3 world, float time, float reveal, float seed) {
    const float spacing = 1.38;
    float cellId = floor((world.x + spacing * 0.5) / spacing);
    float localX = mod(world.x + spacing * 0.5, spacing) - spacing * 0.5;
    float cellRnd = hash11(cellId + seed * 79.0);
    float growth = 0.08 + easeOutCubic(reveal) * 0.92;

    vec3 centerCrystal = vec3(localX + (cellRnd - 0.5) * 0.12, world.y, world.z);
    centerCrystal.y -= mix(0.48, -0.02, growth);
    centerCrystal.xy = rotate2((cellRnd - 0.5) * 0.26 + sin(time * 0.12 + cellRnd * 7.0) * 0.025) * centerCrystal.xy;
    float centerDistance = octahedronSDF(centerCrystal, vec3(0.30, 0.72 * growth, 0.38));

    vec3 leftCrystal = vec3(localX + 0.36, world.y - mix(0.43, 0.08, growth), world.z + 0.05);
    leftCrystal.xy = rotate2(-0.34 + cellRnd * 0.12) * leftCrystal.xy;
    float leftDistance = octahedronSDF(leftCrystal, vec3(0.22, 0.48 * growth, 0.29));

    vec3 rightCrystal = vec3(localX - 0.38, world.y - mix(0.45, 0.10, growth), world.z - 0.04);
    rightCrystal.xy = rotate2(0.31 - cellRnd * 0.10) * rightCrystal.xy;
    float rightDistance = octahedronSDF(rightCrystal, vec3(0.20, 0.42 * growth, 0.27));

    return min(centerDistance, min(leftDistance, rightDistance));
}

vec3 crystalNormal(vec3 p, float time, float reveal, float seed) {
    float e = 0.0045;
    float center = crystalScene(p, time, reveal, seed);
    return normalize(vec3(
        crystalScene(p + vec3(e, 0.0, 0.0), time, reveal, seed) - center,
        crystalScene(p + vec3(0.0, e, 0.0), time, reveal, seed) - center,
        crystalScene(p + vec3(0.0, 0.0, e), time, reveal, seed) - center
    ));
}

vec4 crystalSurface(vec2 p, float time, float reveal, float seed, vec3 c0, vec3 c1, vec3 hi) {
    float rayT = -1.35;
    float hitMask = 0.0;
    float proximityGlow = 0.0;
    vec3 hitPosition = vec3(p, 0.0);

    for (int i = 0; i < 42; i++) {
        vec3 position = vec3(p, rayT);
        float sceneDistance = crystalScene(position, time, reveal, seed);
        proximityGlow += exp(-abs(sceneDistance) * 34.0) * 0.010 * (1.0 - proximityGlow);
        if (sceneDistance < 0.0045) {
            hitMask = 1.0;
            hitPosition = position;
            break;
        }
        rayT += clamp(sceneDistance * 0.74, 0.006, 0.125);
        if (rayT > 1.38) break;
    }

    vec3 normal = crystalNormal(hitPosition, time, reveal, seed);
    vec3 viewDirection = vec3(0.0, 0.0, -1.0);
    vec3 keyLight = normalize(vec3(-0.48, -0.34, -0.82));
    vec3 reflected = reflect(viewDirection, normal);
    vec3 refracted = refract(viewDirection, normal, 0.74);

    float diffuse = 0.16 + 0.84 * max(dot(normal, keyLight), 0.0);
    float fresnel = pow(1.0 - abs(dot(normal, -viewDirection)), 3.2);
    float specular = pow(max(dot(reflected, -keyLight), 0.0), 42.0);
    float internal = fbm31(hitPosition * 3.4 + refracted * 2.1 + vec3(time * 0.07, seed * 31.0, 0.0));
    float facetWire = pow(saturate(1.0 - abs(normal.x * normal.y * normal.z) * 5.8), 8.0);
    float spectralPhase = dot(refracted, vec3(2.3, 3.7, 5.1)) + internal * 5.0 + seed * 9.0;
    vec3 dispersion = 0.5 + 0.5 * cos(vec3(0.0, 2.1, 4.2) + spectralPhase);

    vec3 color = mix(c0 * 0.40, c1 * 1.16, saturate(diffuse * 0.72 + internal * 0.43));
    color = mix(color, dispersion * c1 * 1.20, fresnel * 0.28);
    color = mix(color, hi * 1.62, saturate(specular * 0.92 + facetWire * 0.18));
    color += hi * proximityGlow * 0.42;

    float alpha = hitMask * (0.26 + diffuse * 0.16 + fresnel * 0.24 + specular * 0.48 + facetWire * 0.09);
    alpha += proximityGlow * 0.24;
    alpha *= easeOutCubic(reveal);
    return vec4(color, saturate(alpha));
}

vec2 waveDx(vec2 position, vec2 direction, float frequency, float phase) {
    float x = dot(direction, position) * frequency + phase;
    float wave = exp(sin(x) - 1.0);
    return vec2(wave, -wave * cos(x));
}

float waterWaves(vec2 position, float time, float seed) {
    float phaseShift = length(position) * 0.12;
    float iter = seed * 17.0;
    float frequency = 1.0;
    float timeMultiplier = 1.75;
    float weight = 1.0;
    float valueSum = 0.0;
    float weightSum = 0.0;

    for (int i = 0; i < 12; i++) {
        vec2 direction = vec2(sin(iter), cos(iter));
        vec2 wave = waveDx(position, direction, frequency, time * timeMultiplier + phaseShift);
        position += direction * wave.y * weight * 0.28;
        valueSum += wave.x * weight;
        weightSum += weight;
        weight *= 0.80;
        frequency *= 1.19;
        timeMultiplier *= 1.07;
        iter += 12.399963;
    }

    return valueSum / max(weightSum, 0.0001);
}

vec4 waterSurface(vec2 p, float time, float reveal, float seed, vec3 c0, vec3 c1, vec3 hi) {
    vec2 waterP = p * 1.34 + vec2(seed * 11.0, 0.0);
    float center = waterWaves(waterP, time, seed);
    float epsilon = 0.018;
    float dx = waterWaves(waterP + vec2(epsilon, 0.0), time, seed) - center;
    float dy = waterWaves(waterP + vec2(0.0, epsilon), time, seed) - center;
    vec3 normal = normalize(vec3(-dx / epsilon, -dy / epsilon, 1.55));

    vec3 viewDir = normalize(vec3(p * 0.055, 1.0));
    vec3 sunDir = normalize(vec3(-0.44, -0.31, 1.0));
    vec3 halfDir = normalize(viewDir + sunDir);
    float fresnel = 0.04 + 0.96 * pow(1.0 - max(dot(normal, viewDir), 0.0), 5.0);
    float specular = pow(max(dot(normal, halfDir), 0.0), 72.0);

    float interference = abs(dx) + abs(dy);
    float caustic = pow(saturate(1.0 - interference * 5.2), 11.0);
    float deepFlow = fbm21(waterP * vec2(0.72, 1.35) + vec2(time * 0.12, -time * 0.08));
    float foam = smoothstep(0.78, 1.03, center + interference * 1.35);

    vec3 reflectedRay = reflect(-viewDir, normal);
    float horizon = saturate(reflectedRay.y * 0.5 + 0.5);
    float sunReflection = pow(max(dot(reflectedRay, sunDir), 0.0), 96.0);
    vec3 atmosphere = mix(c0 * 0.46, c1 * 0.92, horizon);
    atmosphere = mix(atmosphere, hi * 1.24, sunReflection);

    float depth = saturate(0.28 + center * 0.52 + deepFlow * 0.32);
    vec3 subsurface = mix(c0 * 0.30, c1 * 0.78, depth);
    vec3 refractedColor = mix(subsurface, c1, saturate(normal.x * 0.16 + normal.y * 0.13 + 0.44));
    vec3 color = mix(refractedColor, atmosphere, saturate(fresnel * 0.82 + 0.08));
    color += hi * (specular * 1.42 + sunReflection * 0.82 + caustic * 0.12 + foam * 0.16);

    float alpha = 0.12 + depth * 0.12 + fresnel * 0.25 + specular * 0.54 + sunReflection * 0.34 + foam * 0.12;
    alpha *= easeOutCubic(reveal);
    return vec4(color, saturate(alpha));
}

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
        material = blizzardSurface(p, time, reveal, seed, c0, c1, hi);
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
    float mouseGlint = exp(-dot(mouseDelta, mouseDelta) * 5.8);
    material.rgb += hi * mouseGlint * 0.075 * material.a;

    float normalStep = max(max(logicalPixel.x, logicalPixel.y), 0.75);
    float shapeDx = roundedBoxSDF(shapePosition + vec2(normalStep, 0.0), halfSize, radius)
                  - roundedBoxSDF(shapePosition - vec2(normalStep, 0.0), halfSize, radius);
    float shapeDy = roundedBoxSDF(shapePosition + vec2(0.0, normalStep), halfSize, radius)
                  - roundedBoxSDF(shapePosition - vec2(0.0, normalStep), halfSize, radius);
    vec2 shapeNormal = normalize(vec2(shapeDx, shapeDy) + vec2(0.00001));
    float topLight = saturate(dot(shapeNormal, normalize(vec2(-0.32, -1.0))) * 0.5 + 0.5);
    float insideDistance = max(-shapeDistance, 0.0);
    float edgeGradient = 1.0 - saturate(insideDistance / max(radius * 0.92, 2.5));
    float hairline = 1.0 - smoothstep(0.0, aa * 1.65, abs(shapeDistance));
    float glassRim = saturate(edgeGradient * edgeGradient * (0.055 + topLight * 0.085) + hairline * (0.12 + topLight * 0.16));
    glassRim *= easeOutCubic(reveal);

    material.rgb = acesApprox(material.rgb * 1.28);
    vec3 rimColor = mix(hi, c1, 0.16 + (1.0 - topLight) * 0.10);
    float rimMix = saturate(glassRim * (0.52 + 0.48 * (1.0 - material.a)));
    material.rgb = mix(material.rgb, rimColor, rimMix);

    float materialAlpha = saturate(material.a * intensity * v_Color.a) * shapeAlpha;
    float rimAlpha = glassRim * shapeAlpha * v_Color.a;
    float finalAlpha = saturate(materialAlpha + rimAlpha * (1.0 - materialAlpha));
    if (finalAlpha <= 0.001) discard;

    fragColor = vec4(material.rgb, finalAlpha);
}
