/*
 * Thin water hover material for ClickGUI Modules.
 * Four analytic waves provide height + slope directly. No numerical normal
 * sampling, no exp-based iterative field and no dense body alpha.
 */

void waterWave(vec2 p,
               vec2 dir,
               float frequency,
               float phase,
               float amplitude,
               inout float height,
               inout vec2 slope) {
    float x = dot(p, dir) * frequency + phase;
    float s = sin(x);
    float c = cos(x);
    height += s * amplitude;
    slope += dir * (c * frequency * amplitude);
}

float pow5Fast(float x) {
    float x2 = x * x;
    return x2 * x2 * x;
}

float pow32Fast(float x) {
    float x2 = x * x;
    float x4 = x2 * x2;
    float x8 = x4 * x4;
    float x16 = x8 * x8;
    return x16 * x16;
}

vec4 waterSurface(vec2 p, float time, float reveal, float seed, vec3 c0, vec3 c1, vec3 hi) {
    vec2 q = p * vec2(1.12, 1.55) + vec2(seed * 3.7, -seed * 1.9);
    float height = 0.0;
    vec2 slope = vec2(0.0);

    waterWave(q, normalize(vec2( 0.94,  0.34)), 1.35, time * 0.92 + seed * 2.1, 0.42, height, slope);
    waterWave(q, normalize(vec2(-0.52,  0.85)), 2.05, time * 1.17 - seed * 1.4, 0.25, height, slope);
    waterWave(q, normalize(vec2( 0.21, -0.98)), 3.20, time * 1.44 + seed * 4.8, 0.16, height, slope);
    waterWave(q, normalize(vec2(-0.83, -0.56)), 4.65, time * 1.73 - seed * 3.2, 0.09, height, slope);

    vec3 normal = normalize(vec3(-slope * 0.20, 1.45));
    vec3 viewDir = normalize(vec3(p * 0.035, 1.0));
    vec3 lightDir = normalize(vec3(-0.45, -0.28, 1.0));
    vec3 halfDir = normalize(viewDir + lightDir);

    float ndv = saturate(dot(normal, viewDir));
    float fresnel = 0.025 + 0.975 * pow5Fast(1.0 - ndv);
    float specBase = saturate(dot(normal, halfDir));
    float specular = pow32Fast(specBase);
    float crest = smoothstep(0.34, 0.70, height + length(slope) * 0.10);

    float reflectedY = reflect(-viewDir, normal).y;
    float horizon = saturate(reflectedY * 0.5 + 0.5);
    vec3 color = mix(c0 * 0.24, c1 * 0.78, 0.38 + height * 0.16 + horizon * 0.34);
    color = mix(color, hi * 1.12, fresnel * 0.20 + specular * 0.52 + crest * 0.06);

    float alpha = 0.010 + abs(height) * 0.012 + length(slope) * 0.008;
    alpha += fresnel * 0.075 + specular * 0.30 + crest * 0.030;
    alpha *= easeOutCubic(reveal);
    return vec4(color, saturate(alpha));
}
