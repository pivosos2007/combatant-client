#version 330 core
#extension GL_ARB_separate_shader_objects : require
#include <combatant:reimagined_sky_common.glsl>

layout(location = 0) in vec2 v_TexCoord;
layout(location = 0) out vec4 color;

// Single-scattering evaluation in kilometre scale. The optical coefficients and
// extinction heights are taken from the registered Overworld atmosphere model.
vec3 overworldAtmosphere(vec3 ray) {
    float earthRadius = u_Optics.x;
    float airRadius = u_Optics.y;
    float observerHeight = max(u_Camera.y * 0.001, 0.001);
    vec3 origin = vec3(0.0, earthRadius + observerHeight, 0.0);
    float b = dot(origin, ray);
    float c = dot(origin, origin) - airRadius * airRadius;
    float topDistance = max(-b + sqrt(max(b * b - c, 0.0)), 0.0);

    vec3 sun = normalize(u_Sun.xyz);
    float mu = clamp(dot(ray, sun), -1.0, 1.0);
    float rayleighPhase = (3.0 / (16.0 * SKY_PI)) * (1.0 + mu * mu);
    float g = clamp(u_Optics.z, -0.90, 0.90);
    float gg = g * g;
    float miePhase = (1.0 - gg) /
        (4.0 * SKY_PI * pow(max(1.0 + gg - 2.0 * g * mu, 0.035), 1.5));
    vec3 betaR = u_Rayleigh.rgb;
    vec3 betaM = u_Mie.rgb;
    // Dense air and ozone strongly attenuate low-angle sunlight, causing the
    // orange horizon without the old decorative high-frequency sky patterns.
    vec3 ozone = u_Ozone.rgb;
    float daylight = smoothstep(-0.16, 0.03, sun.y);
    float twilight = smoothstep(-0.21, -0.015, sun.y) * (1.0 - daylight);
    float airMass = 1.0 / max(0.065, sun.y +
                    0.15 * pow(max(0.0, 1.0 - sun.y), 1.2));
    float segment = min(topDistance, 180.0) / 9.0;
    vec3 transmittance = vec3(1.0);
    vec3 scattering = vec3(0.0);
    for (int i = 0; i < 9; ++i) {
        float t = (float(i) + 0.5) * segment;
        vec3 point = origin + ray * t;
        float h = max(length(point) - earthRadius, 0.0);
        float densityR = exp(-h / max(u_Rayleigh.w, 0.1));
        float densityM = exp(-h / max(u_Mie.w, 0.1));
        float ozoneDensity = exp(-pow((h - u_Optics.w) / max(u_Ozone.w, 1.0), 2.0));
        vec3 extinction = betaR * densityR + betaM * densityM * 1.11 + ozone * ozoneDensity;
        vec3 sunAttenuation = exp(-airMass *
            (betaR * densityR * u_Rayleigh.w +
             betaM * densityM * u_Mie.w * 1.11 + ozone * ozoneDensity * 11.0));
        vec3 source = (betaR * densityR * rayleighPhase +
                       betaM * densityM * miePhase) * sunAttenuation;
        // Midpoint integration of transmittance; transmission evaluated at cell centre.
        scattering += transmittance * exp(-extinction * segment * 0.5) * source * segment;
        transmittance *= exp(-extinction * segment);
    }
    vec3 daylightSky = scattering * (16.0 * daylight + 3.3 * twilight);
    float haze = exp(-abs(ray.y) * 18.0);
    daylightSky += u_Sky.rgb * daylight * (0.018 + 0.12 * haze);
    float night = 1.0 - smoothstep(-0.16, 0.02, sun.y);
    vec3 nightGlow = vec3(0.002, 0.005, 0.015) *
                     (0.42 + 0.58 * max(ray.y, 0.0)) * night;
    vec3 result = daylightSky + nightGlow;
    // The sky is not a replacement for the level's ground-distance fog. Blend
    // the subsurface portion into vanilla's environment color at the horizon.
    float aboveHorizon = smoothstep(-0.080, 0.009, ray.y);
    vec3 groundHaze = u_Sky.rgb * (0.54 + 0.30 * daylight);
    return mix(groundHaze, result, aboveHorizon);
}

vec3 endSky(vec3 ray) {
    // Distant structured nebula; low-frequency HDR fields only. No caustics.
    vec3 q = ray * 6.0;
    float coarse = skyNoise(q);
    float detail = skyNoise(q * 2.14 + vec3(18.1, 3.5, 6.6));
    float band = pow(max(0.0, 1.0 - abs(ray.y + 0.10) * 1.35), 2.5);
    float nebula = smoothstep(0.39, 0.81, coarse * 0.76 + detail * 0.24);
    return vec3(0.006, 0.005, 0.016) +
           band * nebula * vec3(0.046, 0.024, 0.072) +
           pow(nebula, 3.0) * vec3(0.011, 0.009, 0.019);
}

vec3 netherSky(vec3 ray) {
    float altitude = clamp(ray.y * 0.5 + 0.5, 0.0, 1.0);
    float smoke = skyNoise(ray * 4.0 + vec3(0.0, u_Camera.w * 0.0008, 0.0));
    vec3 base = mix(vec3(0.11, 0.028, 0.018),
                    vec3(0.036, 0.012, 0.014), altitude);
    return base * (0.92 + 0.12 * smoke);
}

void main() {
    vec3 rd = skyRay(v_TexCoord);
    vec3 sky = u_Rotation.w < 0.5 ? overworldAtmosphere(rd) :
               u_Rotation.w < 1.5 ? endSky(rd) :
               u_Rotation.w < 2.5 ? netherSky(rd) : u_Sky.rgb;
    color = vec4(max(sky, vec3(0.0)), 1.0);
}
