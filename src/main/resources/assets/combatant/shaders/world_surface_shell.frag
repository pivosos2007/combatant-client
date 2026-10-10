#version 330 core

in vec2 v_TexCoord;
in vec4 v_Color;
in vec4 v_Params;
in vec4 v_Params2;
in vec3 v_ViewPosition;
in vec3 v_WorldPosition;
out vec4 fragColor;

float sat(float x) { return clamp(x, 0.0, 1.0); }
float hash12(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123); }
float hash31(vec3 p) { return fract(sin(dot(p, vec3(127.1, 311.7, 191.999))) * 43758.5453123); }

float checkerNoise(vec2 uv, float scale) {
    vec2 cell = floor(uv * scale);
    float parity = mod(cell.x + cell.y, 2.0);
    float h = hash12(cell + vec2(17.0, 53.0));
    return mix(0.62 + 0.18 * h, 1.0 + 0.20 * h, parity);
}

float softHalo(float d, float start, float end) {
    return 1.0 - smoothstep(start, end, d);
}

void main() {
    vec3 center = v_Params.xyz;
    float radius = max(v_Params.w, 0.0001);
    float progress = sat(v_Params2.x);
    float frontWidth = max(v_Params2.y, 0.001);
    float trailWidth = max(v_Params2.z, 0.001);
    float packedProfile = floor(v_Params2.w + 0.5);
    float profile = mod(packedProfile, 4.0);
    float spherical = step(3.5, packedProfile);

    float totem = step(0.5, profile);
    float totemSelf = totem * (1.0 - step(1.5, profile));
    float totemOffensive = step(1.5, profile);
    float waveProfile = 1.0 - totem;

    vec3 delta = v_WorldPosition - center;
    float projectedDistance = length(delta.xz);
    float spatialDistance = length(delta);
    float metric = mix(projectedDistance, spatialDistance, spherical);

    float signedDistance = metric - radius;
    float shellDistance = abs(signedDistance);
    float aa = fwidth(shellDistance) * 1.5 + 1e-4;

    float edgeCoreWidth = mix(0.105, 0.145, spherical);
    float edgeCore = 1.0 - smoothstep(edgeCoreWidth - aa, edgeCoreWidth + aa, shellDistance);

    float outerHaloStart = edgeCoreWidth;
    float outerHaloEnd = mix(0.30, 0.38, spherical);
    float halo = softHalo(shellDistance, outerHaloStart, outerHaloEnd) * (1.0 - edgeCore * 0.82);

    float ahead = max(signedDistance, 0.0);
    float behind = max(-signedDistance, 0.0);
    float frontField = 1.0 - smoothstep(0.0, frontWidth + aa, ahead);
    float trailField = 1.0 - smoothstep(0.0, trailWidth + aa, behind);
    float bodyMask = 1.0 - smoothstep(edgeCoreWidth, max(frontWidth, trailWidth) + aa, shellDistance);
    float body = max(frontField * 0.18, trailField * 0.22) * bodyMask;

    vec3 dir = spherical > 0.5
            ? normalize(delta + vec3(1e-5))
            : normalize(vec3(delta.x, 1e-5, delta.z));
    float azimuth = atan(dir.z, dir.x);
    float elevation = asin(clamp(dir.y, -1.0, 1.0));
    float phase = progress * mix(6.2, 4.8, totem);

    vec2 waveUv = vec2(azimuth / 6.2831853 + 0.5, metric * 0.30 + progress * 0.18);
    float waveChecker = checkerNoise(waveUv, 9.0);
    float waveLinesA = 0.5 + 0.5 * sin(azimuth * 8.0 - phase * 1.10 + shellDistance * 5.4);
    float waveLinesB = 0.5 + 0.5 * sin(metric * 2.9 + azimuth * 2.8 + phase * 0.82);
    float waveLines = smoothstep(0.56, 0.92, waveLinesA * 0.68 + waveLinesB * 0.32);
    float wavePattern = mix(0.90, 1.12, waveChecker) * (0.88 + 0.18 * waveLines);

    vec2 totemUv = vec2(azimuth / 6.2831853 + 0.5 + elevation * 0.16, elevation * 0.72 + 0.5 + progress * 0.08);
    float totemChecker = checkerNoise(totemUv, 7.0);
    float totemSweep = 0.5 + 0.5 * sin(azimuth * 4.4 + metric * 1.7 - phase * 0.90);
    float totemFilaments = 0.5 + 0.5 * sin(dot(dir, normalize(vec3(0.71, -0.22, 0.67))) * 11.0 + phase * 0.74);
    float totemPattern = mix(0.94, 1.10, totemChecker) * (0.90 + 0.14 * smoothstep(0.42, 0.94, mix(totemSweep, totemFilaments, 0.30)));
    float offensivePulse = 0.92 + 0.16 * (0.5 + 0.5 * sin(phase * 0.92 + azimuth * 2.8));
    totemPattern *= mix(1.0, offensivePulse, totemOffensive);

    float pattern = mix(wavePattern, totemPattern, totem);

    float micro = 0.5 + 0.5 * sin(dot(delta, vec3(1.8, -1.2, 2.1)) * 2.2 + phase * 2.1 + hash31(floor(delta * 6.0)) * 5.0);

    vec3 viewDx = dFdx(v_ViewPosition);
    vec3 viewDy = dFdy(v_ViewPosition);
    vec3 viewNormal = normalize(cross(viewDx, viewDy));
    vec3 viewDir = normalize(-v_ViewPosition);
    float fresnel = pow(1.0 - sat(abs(dot(viewNormal, viewDir))), 1.45);

    vec3 base = v_Color.rgb;
    vec3 highlight = mix(base, vec3(1.0), 0.42 + 0.10 * pattern);
    vec3 haloTint = mix(base, vec3(1.0), mix(0.26, 0.18, totem));
    vec3 bodyTint = mix(base * 0.72, highlight, 0.22 + 0.34 * micro);

    vec3 color = vec3(0.0);
    color += highlight * edgeCore * (0.86 + 0.22 * pattern + fresnel * 0.16);
    color += haloTint * halo * (0.22 + 0.20 * pattern + fresnel * 0.12);
    color += bodyTint * body * mix(0.60 + fresnel * 0.18, 0.50 + fresnel * 0.10, totem);
    color += vec3(1.0) * waveProfile * waveLines * edgeCore * 0.08;
    color += vec3(1.0) * totem * smoothstep(0.52, 0.94, totemFilaments) * edgeCore * 0.03;
    color *= 0.90 + fresnel * 0.18;

    float alpha = v_Color.a * (
            edgeCore * (0.90 + 0.18 * pattern + fresnel * 0.14)
            + halo * mix(0.34 + 0.14 * pattern, 0.26 + 0.10 * pattern, totem)
            + body * mix(1.0, 0.72, totem)
    );
    alpha = sat(alpha);
    if (alpha <= 0.003) discard;

    fragColor = vec4(color, alpha);
}
