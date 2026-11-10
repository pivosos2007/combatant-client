/*
 * Density modulation for the temporal hand trail.
 * The history is the smoke's coverage; this field breaks it into drifting,
 * semi-transparent wisps instead of drawing opaque circles over the hand.
 */
float handGhostHash(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

float handGhostNoise(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = handGhostHash(cell);
    float b = handGhostHash(cell + vec2(1.0, 0.0));
    float c = handGhostHash(cell + vec2(0.0, 1.0));
    float d = handGhostHash(cell + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// Optional, subdued vortices: they deform the smoke density, not add bright disks.
float handGhostRingField(vec2 uv, vec2 aspect, float time, vec4 controls) {
    vec2 p = (uv - 0.5) * aspect * max(controls.z, 0.5);
    vec2 cell = floor(p);
    vec2 jitter = vec2(handGhostHash(cell + 1.3), handGhostHash(cell + 7.9));
    vec2 local = fract(p) - (0.44 + jitter * 0.12);
    float distanceToCenter = length(local);
    float phase = fract(time * max(controls.w, 0.0) * 0.12 + handGhostHash(cell + 13.1));
    float radius = 0.10 + phase * 0.29;
    float distortion = (handGhostNoise(local * 3.5 + cell * 0.71 + time * 0.07) - 0.5) * 0.085;
    float shell = exp(-pow((distanceToCenter + distortion - radius) * 12.0, 2.0));
    float window = 1.0 - smoothstep(0.29, 0.47, distanceToCenter);
    return shell * window * (1.0 - phase) * (0.60 + 0.40 * jitter.x);
}

float handGhostDetail(vec2 uv, vec2 aspect, float time, float turbulence,
                      float scale, float speed, vec4 controls) {
    float smoke = clamp(controls.x, 0.0, 2.0);
    float body = 1.0;
    if (smoke > 0.001) {
        // Sheared, moving domains give the noise a directional, filament-like flow.
        // Low-frequency opacity and finer breakup are mixed with the history flow field.
        vec2 p = (uv - 0.5) * aspect * (max(scale, 0.5) * 1.2);
        p = vec2(p.x + p.y * 0.44, p.y * 0.74);
        float t = time * max(speed, 0.0) * 0.12;
        vec2 drift = vec2(t * 0.27, -t * 0.46);
        vec2 warp = vec2(turbulence - 0.5,
                         handGhostNoise(p * 0.75 - drift * 0.58 + 11.3) - 0.5) * 1.35;
        float large = handGhostNoise(p * 0.85 + drift + warp * 0.75);
        float fine = handGhostNoise(p * 2.7 - drift * 0.72 + warp * 1.15);
        float cloud = large * 0.63 + fine * 0.24 + turbulence * 0.13;
        float veil = smoothstep(0.21, 0.75, cloud);
        // Never create a hard alpha cutoff; thin parts stay faintly visible.
        body = mix(1.0, 0.09 + 1.17 * veil, min(smoke * 0.86, 1.0));
    }
    if (controls.y > 0.001) {
        float rings = handGhostRingField(uv, aspect, time, controls);
        body *= 1.0 + rings * min(controls.y, 2.0) * 0.18;
    }
    return max(body, 0.0);
}
