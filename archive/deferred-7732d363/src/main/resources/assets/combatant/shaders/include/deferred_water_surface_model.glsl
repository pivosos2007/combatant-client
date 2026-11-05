#ifndef COMBATANT_DEFERRED_WATER_SURFACE_MODEL_GLSL
#define COMBATANT_DEFERRED_WATER_SURFACE_MODEL_GLSL

const float COMBATANT_WATER_PI = 3.14159265358979323846;
const float COMBATANT_WATER_IOR = 1.333;
const float COMBATANT_WATER_F0 = 0.02;
// Kept for dormant reflection shaders. Reflections are intentionally not sampled by the foundation surface pass.
const float COMBATANT_WATER_ROUGHNESS = 0.002;
const uint COMBATANT_WATER_FLAG_TOP_SURFACE = 1u;

mat2 combatantWaterRotation(float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat2(c, s, -s, c);
}

void combatantWaterBasis(vec3 normal, out vec3 tangent, out vec3 bitangent) {
    normal = normalize(normal);
    if (abs(normal.y) > 0.8) {
        tangent = vec3(1.0, 0.0, 0.0);
        bitangent = vec3(0.0, 0.0, 1.0);
    } else {
        tangent = normalize(cross(vec3(0.0, 1.0, 0.0), normal));
        bitangent = normalize(cross(normal, tangent));
    }
}

// Small, deterministic value-noise source used only to phase-shift the wave octaves. It is sampled
// once per octave and shared by the three finite-difference height evaluations below; this keeps the
// reference-style irregularity without the old nine-noise-evaluations-per-fragment cost.
float combatantWaterHash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float combatantWaterValueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = combatantWaterHash(i);
    float b = combatantWaterHash(i + vec2(1.0, 0.0));
    float c = combatantWaterHash(i + vec2(0.0, 1.0));
    float d = combatantWaterHash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

vec3 combatantWaterWaveNoise(vec2 coord, float time) {
    vec2 p = (coord + vec2(0.0, 0.25 * time)) * 0.007;
    float n0 = combatantWaterValueNoise(p);
    float n1 = combatantWaterValueNoise(p * 2.5);
    float n2 = combatantWaterValueNoise(p * 6.25);
    return vec3(n0, n1, n2) * 2.0;
}

void combatantWaterWaveSample(vec2 coord, vec2 direction, float time,
                               float noisePhase, float wavelength, float frequency,
                               float amplitude, inout float height, inout vec2 gradient) {
    const float gravity = 9.8;
    float k = (2.0 * COMBATANT_WATER_PI) / max(wavelength, 0.05);
    float omega = sqrt(gravity * k);
    float phase = omega * time - k * (dot(direction, coord * frequency) + noisePhase);
    float sinPhase = sin(phase);
    float cosPhase = cos(phase);
    float s = sinPhase * 0.5 + 0.5;
    height += (s * s) * amplitude;

    // Exact derivative of the squared sinusoidal height term. This reproduces the shape of the
    // finite-difference reference normal without evaluating the whole three-octave field three times.
    gradient += (-s * cosPhase * k * frequency * amplitude) * direction;
}

void combatantWaterHeightGradient(vec2 coord, vec2 flowDirection, bool flowing,
                                  float time, vec3 octaveNoise,
                                  out float height, out vec2 gradient) {
    vec2 direction = flowing ? normalize(flowDirection) : normalize(vec2(0.8660254, 0.5));
    mat2 rotation = flowing ? mat2(1.0) : combatantWaterRotation(2.39996323);
    float waveTime = time * (flowing ? 0.7 : 0.5);

    height = 0.0;
    gradient = vec2(0.0);
    float amplitude = 1.0;
    float frequency = 0.7;
    float wavelength = 1.0;

    combatantWaterWaveSample(coord, direction, waveTime, octaveNoise.x,
            wavelength, frequency, amplitude, height, gradient);
    amplitude *= 0.5;
    frequency *= 1.7;
    wavelength *= 1.5;
    direction = direction * rotation;

    combatantWaterWaveSample(coord, direction, waveTime, octaveNoise.y,
            wavelength, frequency, amplitude, height, gradient);
    amplitude *= 0.5;
    frequency *= 1.7;
    wavelength *= 1.5;
    direction = direction * rotation;

    combatantWaterWaveSample(coord, direction, waveTime, octaveNoise.z,
            wavelength, frequency, amplitude, height, gradient);

    const float amplitudeNormalization = 0.5714285714; // 1 / (1 + 0.5 + 0.25)
    height *= amplitudeNormalization;
    gradient *= amplitudeNormalization;
}

float combatantWaterHeight(vec2 coord, vec2 flowDirection, bool flowing,
                           float time, vec3 octaveNoise) {
    float height;
    vec2 gradient;
    combatantWaterHeightGradient(coord, flowDirection, flowing, time, octaveNoise, height, gradient);
    return height;
}

vec3 combatantWaterSurfaceNormal(vec3 worldPosition, vec3 flatNormal, vec2 flowXZ,
                                 float skyLight, float time, float rainStrength) {
    vec3 n = normalize(flatNormal);
    if (n.y < -0.8) return n;

    vec3 tangent;
    vec3 bitangent;
    combatantWaterBasis(n, tangent, bitangent);
    vec2 coord = vec2(dot(worldPosition, tangent), dot(worldPosition, bitangent));

    bool verticalSide = abs(n.y) < 0.25;
    vec3 flowWorld = verticalSide ? vec3(0.0, -1.0, 0.0) : vec3(flowXZ.x, 0.0, flowXZ.y);
    vec2 flow = vec2(dot(flowWorld, tangent), dot(flowWorld, bitangent));
    bool flowing = verticalSide || dot(flow, flow) > 1.0e-8;
    if (!flowing) flow = vec2(0.8660254, 0.5);

    vec3 octaveNoise = combatantWaterWaveNoise(coord, time * (flowing ? 0.7 : 0.5));
    float height;
    vec2 gradient;
    combatantWaterHeightGradient(coord, flow, flowing, time, octaveNoise, height, gradient);

    float sky = clamp(skyLight, 0.0, 1.0);
    float skyResponse = sky * sky * (3.0 - 2.0 * sky);
    float influence = flowing ? 0.10 : mix(0.01, 0.04 + 0.15 * clamp(rainStrength, 0.0, 1.0), skyResponse);

    // Suppress unstable near-silhouette perturbation while preserving the actual water geometry.
    vec3 toCamera = normalize(u_CurrentCameraTime.xyz - worldPosition);
    influence *= smoothstep(0.0, 0.15, abs(dot(n, toCamera)));

    float distanceToCamera = length(worldPosition - u_CurrentCameraTime.xyz);
    influence *= 1.0 - smoothstep(96.0, 144.0, distanceToCamera);

    vec3 localNormal = normalize(vec3(gradient * influence, 1.0));
    return normalize(tangent * localNormal.x + bitangent * localNormal.y + n * localNormal.z);
}

float combatantWaterDisplacement(vec3 worldPosition, float skyLight, float time) {
    float amplitude = max(u_Deformation0.x, 0.0);
    if (amplitude <= 1.0e-6) return 0.0;

    vec2 coord = worldPosition.xz;
    vec2 flow = vec2(0.8660254, 0.5);
    vec3 noise = combatantWaterWaveNoise(coord, time * 0.5);
    float height = combatantWaterHeight(coord, flow, false, time, noise);
    float centered = height * 2.0 - 1.0;
    float sky = clamp(skyLight, 0.0, 1.0);
    return centered * amplitude * mix(0.15, 1.0, sky);
}

vec3 combatantWaterTint(vec3 producerTint) {
    return clamp(producerTint, vec3(0.025), vec3(1.0));
}

/*
 * Biome water color is metadata for spectral extinction, not a paint layer. The scene behind the
 * surface is never directly multiplied by the producer tint.
 */
vec3 combatantWaterAbsorption(vec3 producerTint, vec3 baseline) {
    vec3 tint = combatantWaterTint(producerTint);
    vec3 spectral = tint * tint;
    float peak = max(max(spectral.r, spectral.g), spectral.b);
    vec3 retained = spectral / max(peak, 1.0e-4);

    float valley = min(min(retained.r, retained.g), retained.b);
    float chroma = clamp(1.0 - valley, 0.0, 1.0);
    float luma = dot(tint, vec3(0.2126, 0.7152, 0.0722));
    float murk = 1.0 - smoothstep(0.18, 0.58, luma);

    vec3 absorption = max(baseline, vec3(0.020));
    absorption += (vec3(1.0) - retained) * mix(0.012, 0.055, chroma);
    absorption += vec3(0.018, 0.017, 0.016) * murk;
    return clamp(absorption, vec3(0.020), vec3(0.135));
}

#endif
