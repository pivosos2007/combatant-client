#version 330 core
#extension GL_ARB_separate_shader_objects : require
#include <combatant:reimagined_sky_common.glsl>

layout(location = 0) in vec2 v_TexCoord;
layout(location = 0) out vec4 color;

// Weather is a large-scale world-space field, not screen-aligned noise.
float weatherField(vec2 xz) {
    // 2D value noise: four hashes instead of the eight of a 3D weather field.
    vec2 uv = xz * u_CloudShape.x * 0.40;
    vec2 cell = floor(uv);
    vec2 f = fract(uv);
    f = f * f * (3.0 - 2.0 * f);
    float a = skyHash(vec3(cell, 7.8));
    float b = skyHash(vec3(cell + vec2(1.0, 0.0), 7.8));
    float c = skyHash(vec3(cell + vec2(0.0, 1.0), 7.8));
    float d = skyHash(vec3(cell + vec2(1.0, 1.0), 7.8));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float densityAt(vec3 p, bool detail) {
    if (p.y < u_Layer.x - u_CloudWeather.x ||
        p.y > u_Layer.y + u_CloudWeather.x + u_CloudWeather.y + u_CloudWeather.w * 0.3)
        return 0.0;
    vec3 q = p + vec3(u_Camera.w * u_Climate.w * 1.3, 0.0,
                      u_Camera.w * u_Climate.w * 0.49);
    float weather = weatherField(q.xz);
    float base = u_Layer.x + (weather - 0.5) * u_CloudWeather.x * 2.0;
    float thickness = max(24.0, u_Layer.y - u_Layer.x +
                          (weather - 0.5) * u_CloudWeather.y * 2.0 +
                          u_Climate.y * u_CloudWeather.w * 0.32);
    float h = (p.y - base) / thickness;
    // Stratiform sheets appear in humid/rainy conditions underneath the larger
    // convective crowns. Both families use values from CloudProfileRegistry.
    float hFlat = (p.y - u_Stratus.x) / max(1.0, u_Stratus.y);
    float flatEnvelope = smoothstep(0.0, 0.13, hFlat) *
                         (1.0 - smoothstep(0.60, 1.0, hFlat));
    float flatCoverage = clamp(u_Climate.z + u_Stratus.w +
                               0.75 * u_Climate.x + 0.55 * u_Climate.y, 0.0, 1.0);
    float sheet = smoothstep(mix(0.82, 0.28, flatCoverage) - 0.06,
                             mix(0.82, 0.28, flatCoverage) + 0.11, weather);
    float stratus = sheet * flatEnvelope * u_Stratus.z *
                    (0.28 + 0.72 * u_Climate.x);
    if (h <= 0.0 || h >= 1.0) return stratus;

    float coverage = clamp(u_Climate.z + u_Layer.w + u_Climate.x * 0.30 +
                           u_Climate.y * u_CloudWeather.z * 0.13, 0.0, 0.97);
    float threshold = mix(0.80, 0.32, coverage);
    float macro = skyNoise(q * u_CloudShape.x);
    float coarse = smoothstep(threshold - 0.11, threshold + 0.08,
                              macro * 0.77 + weather * 0.23);
    if (coarse < u_CloudLighting.w) return stratus;
    float shape = coarse;
    if (detail) {
        float erosion = skyNoise(q * u_CloudShape.y + vec3(13.0, 4.0, 7.0));
        shape = clamp(coarse - (1.0 - erosion) * (1.0 - coarse) * 0.43, 0.0, 1.0);
    }
    float underside = smoothstep(0.0, 0.16, h);
    float top = 1.0 - smoothstep(0.62, 1.0, h);
    // More structured cauliflower crowns and flattened bottoms, rather than
    // the uniform fog-slice thickness of the prototype.
    float envelope = underside * top * mix(0.74, 1.18, smoothstep(0.1, 0.58, h));
    return shape * envelope * u_Layer.z + stratus;
}

float hg(float mu, float g) {
    float gg = g * g;
    return (1.0 - gg) / (4.0 * SKY_PI * pow(max(1.0 + gg - 2.0 * g * mu, 0.06), 1.5));
}

vec4 highCloud(vec3 rd, vec3 sun) {
    if (rd.y < 0.025 || u_Cirrus.y <= 0.001) return vec4(0.0);
    float t = (u_Cirrus.x - u_Camera.y) / rd.y;
    if (t <= 0.0 || t > u_CloudShape.w * 1.35) return vec4(0.0);
    vec3 p = u_Camera.xyz + rd * t + vec3(u_Camera.w * u_Climate.w * 2.2, 0.0, 0.0);
    vec3 q = p * u_Cirrus.z;
    float wisps = skyNoise(q) * 0.68 +
                  skyNoise(p * u_Cirrus.w + vec3(11.0, 0.0, 3.0)) * 0.32;
    float a = smoothstep(0.60, 0.83, wisps) * u_Cirrus.y * 0.30 * u_Climate.z;
    a *= smoothstep(0.025, 0.13, rd.y);
    float daylight = smoothstep(-0.13, 0.14, sun.y);
    vec3 shade = mix(vec3(0.037, 0.051, 0.086),
                     vec3(0.74, 0.77, 0.82) * (0.7 + 0.3 * max(0.0, dot(rd, sun))), daylight);
    return vec4(shade * a, a);
}

void main() {
    color = vec4(0.0);
    if (u_Rotation.w > 0.5 || u_Climate.z <= 0.001) return;
    vec3 rd = skyRay(v_TexCoord);
    vec3 sun = normalize(u_Sun.xyz);
    vec4 cirrus = highCloud(rd, sun);
    if (abs(rd.y) < 0.007) { color = cirrus; return; }

    // Conservative envelope bounds include the maximum vertical weather variation.
    float lower = u_Layer.x - u_CloudWeather.x;
    float upper = u_Layer.y + u_CloudWeather.x + u_CloudWeather.y + u_CloudWeather.w * 0.3;
    float a = (lower - u_Camera.y) / rd.y;
    float b = (upper - u_Camera.y) / rd.y;
    float start = max(0.0, min(a, b));
    float stop = min(u_CloudShape.w, max(a, b));
    if (stop <= start) { color = cirrus; return; }

    int steps = int(u_Quality.x);
    float dt = (stop - start) / float(max(steps, 1));
    // Time-varying interleaved-gradient noise + temporal history: no fixed bands.
    vec2 pixel = floor(gl_FragCoord.xy);
    float jitter = fract(52.9829189 * fract(dot(pixel +
                   vec2(u_Temporal.y * 13.0, u_Temporal.y * 17.0), vec2(0.06711056, 0.00583715))));
    float sunAmount = smoothstep(-0.12, 0.12, sun.y) * (1.0 - 0.60 * u_Climate.y);
    // Clouds also receive phase-correct moonlight. Direction from the celestial
    // producer is stable; the phase index determines lunar irradiance.
    vec3 moon = normalize(u_Moon.xyz);
    float phaseAmount = 0.5 + 0.5 * cos(u_Rotation.z * (SKY_PI * 0.25));
    float moonAmount = smoothstep(-0.04, 0.12, moon.y) * (1.0 - sunAmount) *
                       pow(phaseAmount, 1.2) * (1.0 - 0.65 * u_Climate.x);
    vec3 ambient = mix(vec3(0.038, 0.050, 0.080),
                       max(vec3(0.08), u_Sky.rgb * 0.38) + vec3(0.08, 0.10, 0.12), sunAmount);
    vec3 direct = mix(vec3(0.19, 0.25, 0.38),
                      vec3(1.0, 0.93, 0.81), sunAmount);
    float mu = dot(rd, sun);
    float phase = hg(mu, u_CloudLighting.x) * 0.88 +
                  hg(mu, -0.22) * 0.12;
    float forward = clamp(phase * 3.8, 0.32, 3.4);
    float moonForward = clamp(hg(dot(rd, moon), 0.42) * 4.0, 0.3, 2.2);
    vec3 radiance = vec3(0.0);
    float through = 1.0;
    for (int i = 0; i < 32; ++i) {
        if (i >= steps || through < 0.009) break;
        float t = start + (float(i) + jitter) * dt;
        vec3 p = u_Camera.xyz + rd * t;
        float density = densityAt(p, true);
        if (density < 0.008) continue;
        float extinction = density * dt * u_CloudShape.z * 0.62;
        float stepThrough = exp(-extinction);
        // Only one coarse sun probe per occupied sample. Cheap multiple-scattering
        // approximation adds energy at depth without simulating a second light engine.
        vec3 lightPoint = p + sun * (44.0 + dt * 0.5) +
                          vec3(u_Camera.w * u_Climate.w * 1.3, 0.0,
                               u_Camera.w * u_Climate.w * 0.49);
        float lightMacro = skyNoise(lightPoint * u_CloudShape.x);
        float lightDensity = smoothstep(0.39, 0.72, lightMacro) * u_Layer.z;
        float sunTrans = exp(-lightDensity * u_CloudShape.z * 38.0);
        float multi = u_CloudLighting.z * (1.0 - sunTrans) * 0.48;
        vec3 lighting = ambient * (0.92 + multi) +
                        direct * sunAmount * (0.14 + sunTrans * 0.86) *
                        forward * u_CloudLighting.y +
                        u_MoonColor.rgb * (18.0 * moonAmount * moonForward) *
                        (0.22 + 0.78 * sunTrans);
        float opacity = 1.0 - stepThrough;
        radiance += lighting * (through * opacity);
        through *= stepThrough;
    }
    vec4 lowCloud = vec4(radiance, 1.0 - through);
    // The high ice layer lies behind the denser cumuliform layer.
    color = lowCloud + cirrus * (1.0 - lowCloud.a);
    color.a = clamp(color.a, 0.0, 1.0);
}
