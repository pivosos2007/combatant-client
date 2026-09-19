#ifndef COMBATANT_DEFERRED_WATER_SURFACE_MODEL_GLSL
#define COMBATANT_DEFERRED_WATER_SURFACE_MODEL_GLSL

const float COMBATANT_WATER_PI = 3.14159265358979323846;
const float COMBATANT_WATER_GOLDEN_ANGLE = 2.39996322972865332;
const float COMBATANT_WATER_F0 = 0.02;
const float COMBATANT_WATER_ROUGHNESS = 0.002;
const float COMBATANT_WATER_IOR = 1.333;
const uint COMBATANT_WATER_FLAG_TOP_SURFACE = 1u;

float combatantWaterHash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float combatantWaterNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = combatantWaterHash(i);
    float b = combatantWaterHash(i + vec2(1.0, 0.0));
    float c = combatantWaterHash(i + vec2(0.0, 1.0));
    float d = combatantWaterHash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

mat2 combatantWaterRotation(float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat2(c, s, -s, c);
}

float combatantWaterWave(vec2 coord, vec2 direction, float time, float phaseNoise, float wavelength) {
    float k = 2.0 * COMBATANT_WATER_PI / wavelength;
    float omega = sqrt(9.8 * k);
    float phase = omega * time - k * (dot(direction, coord) + phaseNoise);
    float wave = sin(phase) * 0.5 + 0.5;
    return wave * wave;
}

float combatantWaterHeight(vec2 coord, vec2 flowDirection, bool flowing, float time) {
    vec2 direction = flowing ? normalize(flowDirection)
                             : vec2(cos(COMBATANT_WATER_PI / 6.0), sin(COMBATANT_WATER_PI / 6.0));
    if (dot(direction, direction) < 1.0e-8) direction = vec2(0.8660254, 0.5);
    mat2 rotation = flowing ? mat2(1.0) : combatantWaterRotation(COMBATANT_WATER_GOLDEN_ANGLE);
    float t = time * (flowing ? 0.7 : 0.5);

    float height = 0.0;
    float amplitude = 1.0;
    float frequency = 0.7;
    float wavelength = 1.0;
    vec2 noiseCoord = (coord + vec2(0.0, 0.25 * t)) * 0.007;
    for (int i = 0; i < 3; ++i) {
        float phaseNoise = combatantWaterNoise(noiseCoord) * 2.0;
        height += combatantWaterWave(coord * frequency, direction, t, phaseNoise, wavelength) * amplitude;
        amplitude *= 0.5;
        frequency *= 1.7;
        wavelength *= 1.5;
        direction = rotation * direction;
        noiseCoord *= 2.5;
    }
    return height * 0.5714285714285714;
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

vec3 combatantWaterSurfaceNormal(vec3 worldPosition, vec3 flatNormal, vec2 flowXZ,
                                 float skyLight, float time, float rainStrength) {
    vec3 n = normalize(flatNormal);
    if (n.y < -0.8) return n;

    vec3 tangent;
    vec3 bitangent;
    combatantWaterBasis(n, tangent, bitangent);
    vec2 coord = -vec2(dot(worldPosition, tangent), dot(worldPosition, bitangent));

    bool verticalSide = abs(n.y) < 0.25;
    vec2 flowDirection;
    if (verticalSide) {
        vec3 down = vec3(0.0, -1.0, 0.0);
        flowDirection = vec2(dot(down, tangent), dot(down, bitangent));
    } else {
        vec3 flowWorld = vec3(flowXZ.x, 0.0, flowXZ.y);
        flowDirection = vec2(dot(flowWorld, tangent), dot(flowWorld, bitangent));
    }
    bool flowing = verticalSide || dot(flowDirection, flowDirection) > 1.0e-8;
    if (flowing && dot(flowDirection, flowDirection) > 1.0e-8) flowDirection = normalize(flowDirection);

    const float h = 0.1;
    float w0 = combatantWaterHeight(coord, flowDirection, flowing, time);
    float wx = combatantWaterHeight(coord + vec2(h, 0.0), flowDirection, flowing, time);
    float wy = combatantWaterHeight(coord + vec2(0.0, h), flowDirection, flowing, time);

    float skyResponse = smoothstep(0.0, 1.0, clamp(skyLight, 0.0, 1.0));
    float influence = flowing ? 0.10
            : mix(0.01, 0.04 + 0.15 * clamp(rainStrength, 0.0, 1.0), skyResponse);
    vec3 toCamera = normalize(worldPosition - u_CurrentCameraTime.xyz);
    influence *= smoothstep(0.0, 0.15, abs(dot(n, toCamera)));

    vec3 localNormal = normalize(vec3((wx - w0) * influence, (wy - w0) * influence, h));
    return normalize(tangent * localNormal.x + bitangent * localNormal.y + n * localNormal.z);
}

float combatantWaterDisplacement(vec3 worldPosition, float skyLight, float time) {
    vec2 direction = vec2(cos(COMBATANT_WATER_PI / 6.0), sin(COMBATANT_WATER_PI / 6.0));
    float amplitude = max(u_Deformation0.x, 0.0);
    float spatial = max(u_Deformation0.y, 1.0e-4);
    float temporal = max(u_Deformation0.z, 0.0);
    float wave = combatantWaterWave(worldPosition.xz * spatial, direction, time * temporal, 0.0, 1.0);
    return (wave * 2.0 - 1.0) * amplitude
            * (clamp(skyLight, 0.0, 1.0) * 0.9 + 0.1);
}

vec3 combatantWaterTint(vec3 producerTint) {
    return clamp(producerTint, vec3(0.025), vec3(1.0));
}

vec3 combatantWaterAbsorption(vec3 producerTint) {
    vec3 tint = combatantWaterTint(producerTint);
    vec3 fromTint = -log(max(tint, vec3(0.025))) * 0.12;
    return max(fromTint, vec3(0.018, 0.007, 0.003));
}

vec3 combatantWaterNeutralEnvironment(vec3 producerTint) {
    vec3 tint = combatantWaterTint(producerTint);
    return mix(vec3(0.025, 0.040, 0.055), tint * 0.16, 0.35);
}

#endif
