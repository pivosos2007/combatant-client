#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout (std140) uniform Fog {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
};

layout (std140) uniform SkyboxShader {
    vec4 u_Color;       // rgb = shader tint, a = layer alpha
    vec4 u_SkyFogColor; // rgb = vanilla sky color, a = sky/fog blend
    vec4 u_Params;      // x = time, y = speed, z = scale, w = intensity
    vec4 u_View;        // x/y = framebuffer size, z = yaw rad, w = pitch rad
    vec4 u_View2;       // x = fov, y = aurora enabled, z = aurora intensity, w = aurora speed
    vec4 u_StarCounts;  // x = small, y = dust, z = medium, w = large amount multipliers
    vec4 u_StarStyle;   // x = star brightness, y = twinkle strength
    vec4 u_Layers;      // x = packed shader layer toggles
};


out vec4 color;

const float PI = 3.14159265359;
const float TAU = 6.28318530718;

const float LAYER_WAVES = 0.0;
const float LAYER_RIBBONS = 1.0;
const float LAYER_SWEEPS = 2.0;
const float LAYER_VEIL = 3.0;
const float LAYER_NEBULA = 4.0;
const float LAYER_DETAIL_CURTAINS = 5.0;
const float LAYER_POLAR_ARC = 6.0;
const float LAYER_BURSTS = 7.0;
const float LAYER_WATER_VEIL = 8.0;
const float LAYER_CAUSTICS = 9.0;
const float LAYER_REFRACTED_AURORA = 10.0;
const float LAYER_NORTHERN_AURORA = 11.0;
const float LAYER_SMALL_STARS = 12.0;
const float LAYER_DUST_STARS = 13.0;
const float LAYER_MEDIUM_STARS = 14.0;
const float LAYER_LARGE_STARS = 15.0;
const float LAYER_DETAIL_STARS = 16.0;

float layerEnabled(float bit) {
    return step(0.5, floor(mod(u_Layers.x / exp2(bit), 2.0)));
}

mat3 rotX(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat3(
    1.0, 0.0, 0.0,
    0.0, c, s,
    0.0, -s, c
    );
}

mat3 rotY(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat3(
    c, 0.0, s,
    0.0, 1.0, 0.0,
    -s, 0.0, c
    );
}

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float starDensitySelect(float seed, float start, float end, float amount) {
    amount = clamp(amount, 0.0, 10.0);
    if (amount <= 0.0001) {
        return 0.0;
    }
    float s = clamp(1.0 - (1.0 - start) * amount, 0.0, 0.9990);
    float e = clamp(1.0 - (1.0 - end) * amount, s + 0.0001, 0.9999);
    return smoothstep(s, e, seed);
}

float starTwinkleByStrength(float wave, float low, float high) {
    float shaped = mix(low, high, clamp(wave, 0.0, 1.0));
    return max(0.04, 1.0 + (shaped - 1.0) * clamp(u_StarStyle.y, 0.0, 3.0));
}

float luminance(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

vec3 remapSkyDetail(vec3 detailColor, vec3 base, vec3 skyColor, float lum) {
    vec3 cold = mix(skyColor * 0.45, base * 0.85 + skyColor * 0.18, 0.62);
    vec3 greenCurtain = mix(base, vec3(0.42, 1.00, 0.76), 0.42);
    vec3 violetCurtain = mix(base, vec3(0.88, 0.38, 1.00), 0.52);
    vec3 starWhite = mix(vec3(1.0), base + skyColor * 0.20, 0.34);
    vec3 mapped = cold * (0.18 + lum * 0.72);
    mapped += greenCurtain * detailColor.g * 0.34;
    mapped += violetCurtain * max(detailColor.r, detailColor.b) * 0.28;
    mapped += starWhite * smoothstep(0.62, 1.10, max(max(detailColor.r, detailColor.g), detailColor.b)) * 0.20;
    return mapped;
}

mat2 rot2(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat2(c, -s, s, c);
}

float skyNoiseScalar2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float skyFbmScalar2(vec2 p) {
    float v = 0.0;
    float a = 0.58;
    vec2 q = p;
    for (int i = 0; i < 3; i++) {
        v += skyNoiseScalar2(q) * a;
        q = rot2(0.58 + float(i) * 0.17) * q * 2.04 + vec2(13.7, -8.2);
        a *= 0.50;
    }
    return v;
}

vec3 animatedSkyDomain(vec3 rayW, float rawTime, float layer) {
    vec3 d = normalize(rayW);
    float yaw = rawTime * (0.030 + layer * 0.006) + sin(rawTime * 0.017 + layer * 1.73) * 0.18;
    float pitch = sin(rawTime * (0.022 + layer * 0.004) + layer * 2.11) * 0.14;
    vec3 q = rotY(yaw) * rotX(pitch) * d;
    q += vec3(
        sin(rawTime * 0.21 + layer * 1.31),
        cos(rawTime * 0.17 - layer * 0.73),
        sin(rawTime * 0.13 + layer * 2.07)
    ) * 0.045;
    return normalize(q);
}



float skyFlowField(vec3 p, float time, float intensity, float speedBias) {
    float t = time * speedBias;
    vec3 q = p;
    float w1 = sin(q.x * 1.73 + q.y * 1.11 + t * 0.84)
             + cos(q.z * 1.41 - q.x * 0.67 - t * 0.58);
    float w2 = sin(q.z * 2.13 + q.x * 0.93 - t * 0.71)
             + cos(q.y * 1.58 + q.z * 0.44 + t * 0.39);
    vec2 causticUv = vec2(q.x + w1 * 0.20, q.z + w2 * 0.20);
    float a = sin(causticUv.x * 4.10 + t * 1.15 + sin(causticUv.y * 1.75));
    float b = sin(causticUv.y * 3.65 - t * 0.92 + cos(causticUv.x * 1.32));
    float ridges = 1.0 - abs(a * 0.58 + b * 0.42);
    float gain = clamp(intensity * 26.0, 0.24, 1.0);
    return smoothstep(0.74, 0.985, ridges) * gain;
}

float skyCausticLines(vec3 rayW, float rawTime, float scale, float intensity) {
    vec3 d = animatedSkyDomain(rayW, rawTime, 0.75);
    vec3 p = d * scale + vec3(rawTime * 0.070, sin(rawTime * 0.17) * 0.35, rawTime * -0.052);
    p.xz = rot2(rawTime * 0.026) * p.xz;

    float field = skyFlowField(p, rawTime * 0.28, intensity, 1.00);
    float pulse = 0.78 + 0.22 * sin(rawTime * 0.62 + dot(d, vec3(2.1, -1.3, 1.7)));
    float ridges = field * pulse;
    ridges *= 1.0 - smoothstep(0.70, 1.8, abs(normalize(rayW).y));
    return ridges * (1.0 - smoothstep(0.50, 0.95, ridges));
}

float skyWaterVeil(vec3 rayW, float rawTime, float scale, float intensity) {
    vec3 d = animatedSkyDomain(rayW, rawTime, 1.55);
    vec2 p = d.xz * scale + vec2(rawTime * 0.030, rawTime * -0.024);
    p += vec2(sin(d.y * 2.40 + rawTime * 0.18), cos(d.x * 1.70 - rawTime * 0.15)) * 0.18;

    float field = skyFbmScalar2(p * 0.72 + 4.1) * 0.72 + skyNoiseScalar2(p * 1.58 - 2.7) * 0.28;
    float soft = smoothstep(0.50, 0.82, field);
    float breathe = 0.82 + 0.18 * sin(rawTime * 0.34 + dot(d, vec3(1.8, 0.5, -1.2)));
    return soft * breathe * clamp(intensity * 18.0, 0.20, 0.82);
}


vec4 northernAuroraLayer(vec3 rayW, vec2 flow, float rawTime, vec3 base, vec3 skyColor, float intensity, float speed) {
    float auroraSpeed = max(speed, 0.0);
    float t = rawTime * auroraSpeed;
    float y = rayW.y;
    float verticalBand = smoothstep(0.045, 0.34, y) * (1.0 - smoothstep(0.86, 1.0, y));
    float horizonFade = 1.0 - smoothstep(0.00, 0.13, abs(y));
    float domeFade = verticalBand * (1.0 - horizonFade * 0.42);

    vec2 domain = vec2(
        flow.x * 0.42 + sin(flow.y * 0.20 + t * 0.060) * 0.92 + t * 0.030,
        y * 2.85 + sin(flow.x * 0.13 - t * 0.045) * 0.36
    );
    float lowNoise = skyFbmScalar2(domain * 0.64 + vec2(3.1, -4.7));
    float foldNoise = skyFbmScalar2(vec2(domain.x * 1.45, domain.y * 0.62) + vec2(-6.4, 2.8));

    float sheetA = sin(domain.x * 2.10 + lowNoise * 5.20 + t * 0.38);
    float sheetB = sin(domain.x * -1.24 + domain.y * 0.76 + foldNoise * 4.40 - t * 0.23 + 1.7);
    float sheet = sheetA * 0.62 + sheetB * 0.38;
    sheet = smoothstep(0.20, 0.92, sheet * 0.5 + 0.5);

    float curtains = sin(domain.x * 8.50 + foldNoise * 7.25 + t * 0.72) * 0.5 + 0.5;
    curtains = smoothstep(0.48, 0.92, curtains);
    float fineStriation = sin(domain.x * 18.0 + lowNoise * 9.0 - t * 1.05) * 0.5 + 0.5;
    fineStriation = smoothstep(0.62, 0.96, fineStriation) * 0.22;

    float heightFade = smoothstep(0.02, 0.20, y) * (1.0 - smoothstep(0.76, 0.96, y));
    float mask = (sheet * 0.58 + curtains * 0.34 + fineStriation) * domeFade * heightFade;
    mask *= clamp(intensity, 0.0, 1.5);

    vec3 green = mix(vec3(0.18, 1.00, 0.63), base + vec3(0.02, 0.28, 0.12), 0.24);
    vec3 cyan = mix(vec3(0.22, 0.82, 1.00), skyColor + base * 0.34, 0.35);
    vec3 violet = vec3(0.72, 0.30, 1.00);
    float violetMix = smoothstep(0.42, 0.88, y) * (0.25 + 0.55 * foldNoise);
    vec3 aurora = mix(green, cyan, smoothstep(0.20, 0.72, lowNoise));
    aurora = mix(aurora, violet, violetMix * 0.38);
    aurora *= 0.30 + sheet * 0.52 + curtains * 0.24;

    return vec4(aurora * mask, mask);
}

void main() {
    vec2 resolution = max(u_View.xy, vec2(1.0));
    vec2 uv = gl_FragCoord.xy / resolution;
    vec2 sp = uv * 2.0 - 1.0;
    float aspect = resolution.x / resolution.y;

    float tanV = tan(radians(max(u_View2.x, 1.0)) * 0.5);
    vec3 rayV = normalize(vec3(sp.x * tanV * aspect, sp.y * tanV, 1.0));
    vec3 rayW = rotY(u_View.z) * rotX(u_View.w) * rayV;

    vec3 base = u_Color.rgb;
    vec3 skyColor = max(u_SkyFogColor.rgb, vec3(0.01));
    float blend = clamp(u_SkyFogColor.a, 0.0, 1.0);
    float horizon = 1.0 - smoothstep(0.035, 0.42, abs(rayW.y));

    float rawTime = u_Params.x * u_Params.y;
    float t = rawTime * 0.035;
    vec2 flow = rayW.xz * max(u_Params.z, 0.001);

    float layerWaves = layerEnabled(LAYER_WAVES);
    float layerRibbons = layerEnabled(LAYER_RIBBONS);
    float layerSweeps = layerEnabled(LAYER_SWEEPS);
    float layerVeil = layerEnabled(LAYER_VEIL);
    float layerNebula = layerEnabled(LAYER_NEBULA);
    float layerDetailCurtains = layerEnabled(LAYER_DETAIL_CURTAINS);
    float layerPolarArc = layerEnabled(LAYER_POLAR_ARC);
    float layerBursts = layerEnabled(LAYER_BURSTS);
    float layerWaterVeil = layerEnabled(LAYER_WATER_VEIL);
    float layerCaustics = layerEnabled(LAYER_CAUSTICS);
    float layerRefractedAurora = layerEnabled(LAYER_REFRACTED_AURORA);
    float layerNorthernAurora = layerEnabled(LAYER_NORTHERN_AURORA);
    float layerSmallStars = layerEnabled(LAYER_SMALL_STARS);
    float layerDustStars = layerEnabled(LAYER_DUST_STARS);
    float layerMediumStars = layerEnabled(LAYER_MEDIUM_STARS);
    float layerLargeStars = layerEnabled(LAYER_LARGE_STARS);
    float layerDetailStars = layerEnabled(LAYER_DETAIL_STARS);

    vec2 nebulaUvA = flow * 0.34 + vec2(t * 0.42, -t * 0.26);
    vec2 nebulaUvB = rot2(0.74) * flow * 0.58 + vec2(-t * 0.21, t * 0.33);
    vec2 auroraUv = vec2(flow.x * 0.42 + sin(flow.y * 0.23 + t * 0.36) * 0.85 + t * 0.20,
                         rayW.y * 2.25 + sin(flow.x * 0.18 - t * 0.28) * 0.34);
    float proceduralNebula = skyFbmScalar2(nebulaUvA) * 0.62 + skyFbmScalar2(nebulaUvB + 4.7) * 0.38;
    float proceduralVeil = smoothstep(0.46, 0.86, skyFbmScalar2(auroraUv * 1.15 + 8.3));
    float proceduralCurtain = smoothstep(0.16, 0.86, sin(auroraUv.x * 2.8 + skyFbmScalar2(auroraUv * 0.85) * 5.4) * 0.5 + 0.5);
    float proceduralStarDust = 0.0;
    vec3 detailRaw = vec3(
            proceduralVeil * proceduralCurtain * 0.72 + proceduralNebula * 0.12,
            proceduralVeil * 0.86 + proceduralNebula * 0.18,
            proceduralNebula * 0.72 + proceduralCurtain * 0.20
    );
    vec3 detailWide = clamp(detailRaw + proceduralStarDust * vec3(0.10, 0.13, 0.17), 0.0, 1.0);
    float detailLum = luminance(detailRaw);
    float detailWideLum = luminance(detailWide);
    float detailPeak = max(max(detailRaw.r, detailRaw.g), detailRaw.b);
    vec3 detailMapped = remapSkyDetail(detailWide, base, skyColor, detailWideLum);
    float wideA = sin(flow.x * 0.72 + flow.y * 0.38 + t);
    float wideB = sin(flow.x * -0.31 + flow.y * 0.86 - t * 0.73 + 1.7);
    float wideC = sin((flow.x + flow.y) * 0.24 + t * 0.41 + 2.4);
    float bands = wideA * 0.46 + wideB * 0.34 + wideC * 0.20;
    bands = bands * 0.5 + 0.5;
    bands = smoothstep(0.38, 0.86, bands);

    float vertical = smoothstep(-0.18, 0.78, rayW.y);
    float dome = 1.0 - horizon * 0.55;
    float calm = bands * vertical * dome;
    calm = calm * calm * (3.0 - 2.0 * calm);
    calm *= layerWaves;

    float ribbonHeight = smoothstep(0.04, 0.36, rayW.y) * (1.0 - smoothstep(0.88, 1.0, rayW.y));
    float ribbonA = sin(flow.x * 1.18 + flow.y * 0.16 + t * 0.64);
    float ribbonB = sin(flow.x * 0.54 - flow.y * 0.47 - t * 0.38 + 2.1);
    float ribbonShape = ribbonA * 0.68 + ribbonB * 0.32;
    ribbonShape = ribbonShape * 0.5 + 0.5;
    float ribbon = smoothstep(0.68, 0.92, ribbonShape) * ribbonHeight * dome * layerRibbons;
    float ribbonCore = smoothstep(0.86, 0.98, ribbonShape) * ribbonHeight * dome * layerRibbons;

    float drift = sin(flow.x * 0.19 + flow.y * 0.27 + t * 0.22) * 0.5 + 0.5;
    float softPulse = 0.82 + drift * 0.18;

    float sweepBand = smoothstep(0.12, 0.52, rayW.y) * (1.0 - smoothstep(0.90, 1.0, rayW.y));
    float sweepCurveA = rayW.y - (0.34 + sin(flow.x * 0.34 + flow.y * 0.08 + t * 0.20) * 0.075);
    float sweepCurveB = rayW.y - (0.58 + sin(flow.y * 0.28 - flow.x * 0.12 - t * 0.16 + 1.6) * 0.065);
    float sweepA = 1.0 - smoothstep(0.018, 0.135, abs(sweepCurveA));
    float sweepB = 1.0 - smoothstep(0.016, 0.120, abs(sweepCurveB));
    float sweepFade = (sin(flow.x * 0.21 - flow.y * 0.18 + t * 0.18) * 0.5 + 0.5) * 0.35 + 0.65;
    float sweep = (sweepA * 0.55 + sweepB * 0.38) * sweepBand * dome * sweepFade * layerSweeps;

    float veilWave = sin(flow.x * 0.26 + sin(flow.y * 0.18 + t * 0.12) * 1.25 + t * 0.10);
    float veil = smoothstep(0.18, 0.82, veilWave * 0.5 + 0.5) * vertical * dome * 0.55 * layerVeil;

    float starMask = smoothstep(-0.10, 0.54, rayW.y) * (1.0 - horizon * 0.34);
    vec2 starUv = rayW.xz / max(rayW.y + 1.20, 0.18);
    starUv += vec2(t * 0.030, -t * 0.018);
    vec2 starGrid = starUv * 118.0;
    vec2 starCell = floor(starGrid);
    vec2 starLocal = fract(starGrid) - 0.5;
    float starSeed = hash21(starCell);
    vec2 starOffset = vec2(hash21(starCell + 17.7), hash21(starCell + 43.1)) - 0.5;
    float starDist = length(starLocal - starOffset * 0.62);
    float starDot = 1.0 - smoothstep(0.007, 0.034, starDist);
    float starSelect = starDensitySelect(starSeed, 0.970, 0.997, u_StarCounts.x * layerSmallStars);
    float starTwinklePhase = rawTime * (0.80 + starSeed * 1.70) + starSeed * 18.8496;
    float starTwinkleWave = (sin(starTwinklePhase) * 0.72 + sin(starTwinklePhase * 1.73 + starSeed * 5.1) * 0.28) * 0.5 + 0.5;
    float starTwinkle = starTwinkleByStrength(starTwinkleWave, 0.55, 1.25);
    float stars = starDot * starSelect * starMask * starTwinkle;

    vec2 dustGrid = (starUv + vec2(0.37, -0.21)) * 154.0;
    vec2 dustCell = floor(dustGrid);
    vec2 dustLocal = fract(dustGrid) - 0.5;
    float dustSeed = hash21(dustCell + 9.4);
    vec2 dustOffset = vec2(hash21(dustCell + 5.8), hash21(dustCell + 71.2)) - 0.5;
    float dustDist = length(dustLocal - dustOffset * 0.72);
    float dustDot = 1.0 - smoothstep(0.004, 0.020, dustDist);
    float dustSelect = starDensitySelect(dustSeed, 0.972, 0.997, u_StarCounts.y * layerDustStars);
    float dustTwinklePhase = rawTime * (0.55 + dustSeed * 1.10) + dustSeed * 13.7;
    float dustTwinkle = starTwinkleByStrength(sin(dustTwinklePhase) * 0.5 + 0.5, 0.45, 1.20);
    float starDust = dustDot * dustSelect * starMask * dustTwinkle;

    vec2 mediumGrid = (starUv + vec2(-0.18, 0.29)) * 64.0;
    vec2 mediumCell = floor(mediumGrid);
    vec2 mediumLocal = fract(mediumGrid) - 0.5;
    float mediumSeed = hash21(mediumCell + 53.2);
    vec2 mediumOffset = vec2(hash21(mediumCell + 21.3), hash21(mediumCell + 66.8)) - 0.5;
    float mediumDist = length(mediumLocal - mediumOffset * 0.54);
    float mediumDot = 1.0 - smoothstep(0.010, 0.048, mediumDist);
    float mediumGlow = 1.0 - smoothstep(0.018, 0.115, mediumDist);
    float mediumSelect = starDensitySelect(mediumSeed, 0.978, 0.998, u_StarCounts.z * layerMediumStars);
    float mediumTwinklePhase = rawTime * (0.44 + mediumSeed * 0.92) + mediumSeed * 17.2;
    float mediumTwinkle = starTwinkleByStrength(sin(mediumTwinklePhase) * 0.5 + 0.5, 0.50, 1.35);
    float mediumStars = (mediumDot * 0.78 + mediumGlow * 0.22) * mediumSelect * starMask * mediumTwinkle;

    vec2 largeGrid = (starUv + vec2(0.11, 0.44)) * 42.0;
    vec2 largeCell = floor(largeGrid);
    vec2 largeLocal = fract(largeGrid) - 0.5;
    float largeSeed = hash21(largeCell + 101.9);
    vec2 largeOffset = vec2(hash21(largeCell + 7.7), hash21(largeCell + 94.6)) - 0.5;
    vec2 largeVec = largeLocal - largeOffset * 0.46;
    float largeDist = length(largeVec);
    float largeDot = 1.0 - smoothstep(0.010, 0.050, largeDist);
    float largeHalo = 1.0 - smoothstep(0.025, 0.185, largeDist);
    float largeCross = (1.0 - smoothstep(0.004, 0.018, abs(largeVec.x))) * (1.0 - smoothstep(0.045, 0.220, abs(largeVec.y)));
    largeCross += (1.0 - smoothstep(0.004, 0.018, abs(largeVec.y))) * (1.0 - smoothstep(0.045, 0.220, abs(largeVec.x)));
    float largeSelect = starDensitySelect(largeSeed, 0.988, 0.999, u_StarCounts.w * layerLargeStars);
    float largeTwinklePhase = rawTime * (0.32 + largeSeed * 0.70) + largeSeed * 21.4;
    float largeTwinkle = starTwinkleByStrength(sin(largeTwinklePhase) * 0.5 + 0.5, 0.48, 1.43);
    float largeStars = (largeDot * 0.86 + largeHalo * 0.20 + largeCross * 0.18) * largeSelect * starMask * largeTwinkle;

    vec2 burstUv = rayW.xz / max(rayW.y + 1.05, 0.20);
    burstUv += vec2(rawTime * 0.0018, -rawTime * 0.0011);
    vec2 burstGrid = burstUv * 4.8;
    vec2 burstCell = floor(burstGrid);
    vec2 burstLocal = fract(burstGrid) - 0.5;
    float burstSeed = hash21(burstCell + 31.6);
    vec2 burstOffset = vec2(hash21(burstCell + 12.4), hash21(burstCell + 88.9)) - 0.5;
    vec2 burstVec = burstLocal - burstOffset * 0.66;
    float burstDist = length(burstVec);
    float burstAngle = atan(burstVec.y, burstVec.x);
    float burstPhase = fract(rawTime * 0.030 + burstSeed);
    float burstGate = smoothstep(0.885, 0.970, burstSeed);
    float burstLife = smoothstep(0.010, 0.060, burstPhase) * (1.0 - smoothstep(0.245, 0.390, burstPhase));
    float burstEase = burstPhase * burstPhase * (3.0 - 2.0 * burstPhase);
    float burstRadius = 0.030 + burstEase * 0.42;
    float burstRing = 1.0 - smoothstep(0.010, 0.046, abs(burstDist - burstRadius));
    float burstInnerRing = 1.0 - smoothstep(0.010, 0.040, abs(burstDist - burstRadius * 0.55));
    float burstHalo = (1.0 - smoothstep(0.020, burstRadius * 1.42 + 0.06, burstDist)) * 0.38;
    float burstCore = 1.0 - smoothstep(0.000, 0.034, burstDist);
    float burstPetal = 0.5 + 0.5 * sin(burstAngle * 6.0 + burstDist * 16.0 - burstPhase * 10.0 + burstSeed * 6.2831);
    burstPetal = smoothstep(0.54, 0.94, burstPetal);
    float burstRay = 0.5 + 0.5 * sin(burstAngle * 11.0 - burstDist * 9.0 + burstSeed * 9.17);
    burstRay = smoothstep(0.66, 0.98, burstRay) * (1.0 - smoothstep(0.06, 0.34, burstDist));
    vec2 sparkGrid = burstVec * 12.0 + vec2(0.5);
    vec2 sparkCell = floor(sparkGrid);
    vec2 sparkLocal = fract(sparkGrid) - 0.5;
    float sparkSeed = hash21(sparkCell + burstCell * 3.1);
    float sparkDist = length(sparkLocal - (vec2(hash21(sparkCell + 3.4), hash21(sparkCell + 18.6)) - 0.5) * 0.55);
    float sparkShell = 1.0 - smoothstep(0.055, 0.180, abs(length((sparkCell + 0.5) / 12.0) - burstRadius * 0.86));
    float burstSpark = (1.0 - smoothstep(0.010, 0.044, sparkDist)) * smoothstep(0.74, 0.96, sparkSeed) * sparkShell;
    float burstMask = smoothstep(-0.05, 0.50, rayW.y) * (1.0 - horizon * 0.40);
    float burst = (burstRing * (0.62 + burstPetal * 0.42) + burstInnerRing * 0.32 + burstHalo + burstCore * 0.28 + burstRay * 0.24 + burstSpark * 0.42)
    * burstLife * burstGate * burstMask * layerBursts;

    float detailDome = smoothstep(-0.18, 0.76, rayW.y) * (1.0 - horizon * 0.62);
    float detailNebula = smoothstep(0.055, 0.42, detailWideLum) * detailDome * layerNebula;
    float detailAurora = smoothstep(0.050, 0.42, max(detailWide.g * 0.94 + detailWide.b * 0.35, detailWide.r * 0.70) - detailWideLum * 0.24) * detailDome;
    float detailStars = smoothstep(0.62, 0.96, detailPeak) * starMask * (1.0 - smoothstep(0.20, 0.58, detailWideLum));
    detailStars *= clamp(max(u_StarCounts.x * layerSmallStars, u_StarCounts.y * layerDustStars), 0.0, 1.0) * layerDetailStars;

    float curtainBand = smoothstep(0.05, 0.46, rayW.y) * (1.0 - smoothstep(0.86, 1.0, rayW.y));
    float curtainWaveA = sin(flow.x * 1.38 + flow.y * 0.72 + detailWide.g * 4.2 + t * 0.85);
    float curtainWaveB = sin(flow.x * -0.64 + flow.y * 1.82 - detailWide.b * 3.4 - t * 0.48 + 1.6);
    float detailCurtain = smoothstep(0.36, 0.92, curtainWaveA * 0.62 + curtainWaveB * 0.26 + detailAurora * 0.42) * curtainBand * detailAurora * layerDetailCurtains;

    float polarArcCurve = rayW.y - (0.42 + sin(flow.x * 0.46 + flow.y * 0.78 + t * 0.22) * 0.085 + detailWideLum * 0.06);
    float polarArc = (1.0 - smoothstep(0.018, 0.145, abs(polarArcCurve))) * smoothstep(0.08, 0.55, rayW.y) * detailAurora * 0.72 * layerPolarArc;

    float liquidDome = smoothstep(-0.06, 0.74, rayW.y) * (1.0 - horizon * 0.66);
    float upperDome = smoothstep(0.12, 0.82, rayW.y) * (1.0 - smoothstep(0.94, 1.0, rayW.y));
    float detailEnergy = smoothstep(0.045, 0.42, detailWideLum + detailAurora * 0.30);
    float causticLines = 0.0;
    float waterVeilField = 0.0;
    if (liquidDome > 0.001) {
        if (layerWaterVeil > 0.5) {
            waterVeilField = skyWaterVeil(rayW, rawTime, max(u_Params.z * 0.40 + 1.45, 0.8), 0.020);
        }
        if (upperDome > 0.001 && layerCaustics > 0.5) {
            causticLines = skyCausticLines(rayW, rawTime, max(u_Params.z * 0.62 + 1.95, 1.0), 0.026 + detailAurora * 0.008);
        }
    }
    float causticMask = causticLines * liquidDome * upperDome * (0.20 + detailEnergy * 0.56);
    float waterVeilMask = waterVeilField * liquidDome * (0.14 + detailNebula * 0.32) * (1.0 - detailStars * 0.65);
    float refractedAurora = smoothstep(0.28, 0.90, causticLines + detailCurtain * 0.32) * curtainBand * liquidDome * 0.40 * layerRefractedAurora;

    vec4 northernAurora = vec4(0.0);
    if (u_View2.y > 0.5 && u_View2.z > 0.001 && layerNorthernAurora > 0.5) {
        northernAurora = northernAuroraLayer(rayW, flow, rawTime, base, skyColor, u_View2.z, u_View2.w);
    }

    float cloudCoverForStars = clamp(detailNebula * 0.74 + detailCurtain * 0.82 + northernAurora.a * 1.10 + waterVeilMask * 1.35 + causticMask * 0.90 + ribbon * 0.38 + veil * 0.32, 0.0, 1.0);
    float starClear = 1.0 - smoothstep(0.22, 0.78, cloudCoverForStars);
    float starTwinkleMix = starTwinkleByStrength(sin(rawTime * 0.62 + proceduralNebula * 3.4 + hash21(floor(starUv * 19.0)) * 6.2831) * 0.5 + 0.5, 0.82, 1.08);
    float starLayerVisibility = starClear * starTwinkleMix;

    float starBrightness = clamp(u_StarStyle.x, 0.0, 3.0);

    vec3 quietSky = mix(skyColor * 0.62, base * 0.26 + skyColor * 0.24, 0.55);
    vec3 waveColor = base * (0.18 + calm * 0.34);
    vec3 ribbonColor = mix(base, skyColor + base * 0.35, 0.38) * (ribbon * 0.18 + ribbonCore * 0.24) * softPulse;
    vec3 sweepColor = mix(skyColor, base, 0.72) * sweep * 0.16;
    vec3 veilColor = mix(skyColor, base, 0.45) * veil * 0.08;
    vec3 starColor = mix(vec3(1.0), base, 0.30) * stars * starLayerVisibility * 0.250 * starBrightness;
    vec3 dustColor = mix(vec3(0.86), base, 0.42) * starDust * starLayerVisibility * 0.082 * starBrightness;
    vec3 mediumColor = mix(vec3(0.96), base + skyColor * 0.16, 0.34) * mediumStars * starLayerVisibility * 0.225 * starBrightness;
    vec3 largeColor = mix(vec3(1.0), base + skyColor * 0.12, 0.28) * largeStars * starLayerVisibility * 0.335 * starBrightness;
    vec3 burstColor = mix(vec3(1.0), base + skyColor * 0.35, 0.46) * burst * 0.34;
    vec3 detailNebulaColor = detailMapped * detailNebula * 0.28;
    vec3 detailCurtainColor = mix(detailMapped, base + vec3(0.08, 0.23, 0.18), 0.24) * detailCurtain * 0.38;
    vec3 detailArcColor = mix(detailMapped, vec3(0.78, 0.92, 1.0), 0.28) * polarArc * 0.26;
    vec3 detailStarColor = mix(vec3(1.0), detailMapped, 0.28) * detailStars * starLayerVisibility * 0.110 * starBrightness;
    vec3 causticColor = mix(vec3(0.34, 0.92, 0.98), vec3(0.82, 0.38, 1.00), clamp(detailWide.r * 1.25 + detailWide.b * 0.55, 0.0, 1.0));
    vec3 waterVeilColor = mix(skyColor, base + detailMapped * 0.30, 0.58) * waterVeilMask * 0.070;
    vec3 causticSkyColor = causticColor * causticMask * 0.105;
    vec3 refractedAuroraColor = mix(detailMapped, causticColor, 0.46) * refractedAurora * 0.085;
    vec3 northernAuroraColor = northernAurora.rgb * (1.0 - horizon * 0.42);
    vec3 shaderColor = quietSky + waveColor + ribbonColor + sweepColor + veilColor
    + starColor + dustColor + mediumColor + largeColor + burstColor
    + detailNebulaColor + detailCurtainColor + detailArcColor + detailStarColor
    + waterVeilColor + causticSkyColor + refractedAuroraColor + northernAuroraColor;

    vec3 fogTarget = mix(skyColor, FogColor.rgb, clamp(FogColor.a + horizon * 0.45, 0.0, 1.0));
    vec3 skyFogTarget = mix(skyColor, fogTarget, clamp(horizon * FogColor.a, 0.0, 1.0));
    vec3 finalColor = mix(shaderColor, skyFogTarget, blend * horizon * 0.78);

    color = vec4(finalColor, u_Color.a);
}
