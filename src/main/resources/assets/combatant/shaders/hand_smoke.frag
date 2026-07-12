#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

out vec4 color;

uniform sampler2D u_Src;
uniform sampler2D u_Mask;

layout (std140) uniform HandSmoke {
    vec4 u_Fill;    // rgb, fill alpha
    vec4 u_Glow;    // rgb, alpha
    vec4 u_Shadow;  // rgb, alpha
    vec4 u_Params0; // edgeWidth, quality, octaves, time
    vec4 u_Params1; // resX, resY, scale, contrast
    vec4 u_Params2; // swirl, glowStrength, shadowStrength, density
};

in vec2 v_TexCoord;

float handSmokeHash21(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

float handSmokeValueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = handSmokeHash21(i);
    float b = handSmokeHash21(i + vec2(1.0, 0.0));
    float c = handSmokeHash21(i + vec2(0.0, 1.0));
    float d = handSmokeHash21(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float handSmokeFbm(vec2 p, int octaves) {
    float v = 0.0;
    float a = 0.55;
    mat2 r = mat2(0.80, 0.60, -0.60, 0.80);
    int o = clamp(octaves, 1, 4);
    for (int i = 0; i < 4; ++i) {
        if (i >= o) break;
        v += a * handSmokeValueNoise(p);
        p = r * p * 2.03 + vec2(37.1, 17.7);
        a *= 0.52;
    }
    return v;
}

float handMaskAt(vec2 uv) {
    vec4 mask = texture(u_Mask, uv);
    return max(mask.a, max(mask.r, max(mask.g, mask.b)));
}

float handEdgeDistance(vec2 oneTexel, int quality, float edgeWidth, out float hitAmount) {
    float best = 1.0e5;
    float hit = 0.0;
    int rings = clamp(quality, 1, 4);
    int dirs = quality <= 1 ? 8 : 12;
    float radiusPx = max(edgeWidth, 1.0);

    for (int r = 1; r <= 4; ++r) {
        if (r > rings) continue;
        float rr = float(r) / float(rings);
        float distPx = radiusPx * rr;
        for (int i = 0; i < 12; ++i) {
            if (i >= dirs) continue;
            float angle = (float(i) + float(r) * 0.37) * 6.28318530718 / float(dirs);
            vec2 dir = vec2(cos(angle), sin(angle));
            float a = handMaskAt(v_TexCoord + dir * oneTexel * distPx);
            if (a > 0.02) {
                best = min(best, rr);
                hit = max(hit, a);
            }
        }
    }

    hitAmount = hit;
    return best;
}

vec3 handSmokeField(vec2 fragCoord, vec2 resolution, float time, int octaves) {
    float scale = max(u_Params1.z, 0.05);
    float contrast = max(u_Params1.w, 0.05);
    float swirl = u_Params2.x;
    float density = max(u_Params2.w, 0.0);

    vec2 uv = fragCoord / max(resolution, vec2(1.0));
    vec2 aspect = vec2(resolution.x / max(resolution.y, 1.0), 1.0);
    vec2 p = (uv - 0.5) * aspect * scale;

    float slow = time * 0.13;
    vec2 curl = vec2(
        handSmokeFbm(p * 1.35 + vec2(0.0, slow), octaves),
        handSmokeFbm(p * 1.35 + vec2(5.2, -slow * 0.85), octaves)
    ) - 0.5;
    p += curl * (0.45 + swirl * 0.35);
    p.y -= time * 0.035;

    float body = handSmokeFbm(p * 2.0 + vec2(time * 0.025, -time * 0.055), octaves);
    float wisps = handSmokeFbm(p * 5.2 + vec2(-time * 0.06, time * 0.025), max(octaves - 1, 1));
    float veins = 0.5 + 0.5 * sin((p.x + curl.y * 0.8) * 7.5 + body * 3.2);
    veins = smoothstep(0.28, 0.88, veins);

    float m = mix(body, body * wisps, 0.34);
    m = mix(m, m * (0.74 + veins * 0.38), 0.42);
    m = pow(clamp(m * (0.9 + density * 0.35), 0.0, 1.0), 1.0 / contrast);

    vec3 smoke = mix(u_Fill.rgb * 0.48, u_Fill.rgb, m);
    smoke = mix(smoke, u_Glow.rgb, clamp((m - 0.62) * 1.55, 0.0, 0.38));
    return smoke * (0.50 + m * 0.72);
}

void main() {
    vec4 base = texture(u_Src, v_TexCoord);
    float mask = handMaskAt(v_TexCoord);

    vec2 resolution = max(u_Params1.xy, vec2(1.0));
    vec2 oneTexel = 1.0 / resolution;
    float edgeWidth = max(u_Params0.x, 0.0);
    int quality = int(u_Params0.y + 0.5);
    int octaves = clamp(int(u_Params0.z + 0.5), 1, 4);
    float time = u_Params0.w;
    float glowStrength = max(u_Params2.y, 0.0);
    float shadowStrength = max(u_Params2.z, 0.0);

    if (mask > 0.01) {
        vec3 smoke = handSmokeField(gl_FragCoord.xy, resolution, time, octaves);

        float nL = handMaskAt(v_TexCoord + vec2(-oneTexel.x, 0.0));
        float nR = handMaskAt(v_TexCoord + vec2(oneTexel.x, 0.0));
        float nU = handMaskAt(v_TexCoord + vec2(0.0, -oneTexel.y));
        float nD = handMaskAt(v_TexCoord + vec2(0.0, oneTexel.y));
        float innerEdge = clamp(1.0 - min(min(nL, nR), min(nU, nD)), 0.0, 1.0);
        innerEdge = pow(innerEdge, 0.72);

        float alpha = clamp(u_Fill.a * mask, 0.0, 1.0);
        vec3 outCol = mix(base.rgb, smoke, alpha);
        outCol = mix(outCol, u_Shadow.rgb, innerEdge * u_Shadow.a * shadowStrength * 0.34);
        outCol += u_Glow.rgb * innerEdge * u_Glow.a * glowStrength * 0.24;
        color = vec4(clamp(outCol, 0.0, 1.0), 1.0);
        return;
    }

    float hitAmount = 0.0;
    float d = handEdgeDistance(oneTexel, quality, edgeWidth, hitAmount);
    if (hitAmount > 0.01) {
        float falloff = clamp(1.0 - d, 0.0, 1.0);
        falloff = falloff * falloff * (3.0 - 2.0 * falloff);
        vec3 smoke = handSmokeField(gl_FragCoord.xy, resolution, time, octaves);
        float shadowA = falloff * u_Shadow.a * shadowStrength * 0.54;
        float glowA = pow(falloff, 1.32) * u_Glow.a * glowStrength * 0.40;
        vec3 outCol = mix(base.rgb, u_Shadow.rgb, shadowA);
        outCol = mix(outCol, smoke, glowA * 0.28);
        outCol += u_Glow.rgb * glowA;
        color = vec4(clamp(outCol, 0.0, 1.0), 1.0);
        return;
    }

    color = vec4(base.rgb, 1.0);
}
