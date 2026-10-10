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

layout (std140) uniform HandRift {
    vec4 u_Fill;     // rgb = material tint, a = body tint amount
    vec4 u_Caustic;  // rgb = caustic tint, a = colored caustic amount
    vec4 u_Shadow;   // rgb = compression tint, a = amount
    vec4 u_Screen;   // xy = framebuffer size, z = time, w = outer field width in pixels
    vec4 u_Warp;     // x = distortion pixels, y = flow speed, z = caustic strength, w = quality
    vec4 u_Effects;  // x = glow strength, y = shadow strength, zw reserved
};

in vec2 v_TexCoord;

const vec3 RIFT_LUMA = vec3(0.2126, 0.7152, 0.0722);
const vec2 RIFT_EDGE_START_8[4] = vec2[](
    vec2(0.958072899, 0.286524553),
    vec2(0.835807361, 0.549022818),
    vec2(0.643455865, 0.765483213),
    vec2(0.397147891, 0.917754626)
);
const vec2 RIFT_EDGE_START_12[4] = vec2[](
    vec2(0.981292664, 0.192521967),
    vec2(0.925870585, 0.377840787),
    vec2(0.835807361, 0.549022818),
    vec2(0.714472680, 0.699663341)
);
const vec2 RIFT_EDGE_ROTATE_8 = vec2(0.707106781, 0.707106781);
const vec2 RIFT_EDGE_ROTATE_12 = vec2(0.866025404, 0.5);

float saturate(float v) {
    return clamp(v, 0.0, 1.0);
}

vec3 saturate(vec3 v) {
    return clamp(v, 0.0, 1.0);
}

vec2 safeNormalize(vec2 v, vec2 fallback) {
    float l2 = dot(v, v);
    return l2 > 1.0e-7 ? v * inversesqrt(l2) : fallback;
}

float luminance(vec3 c) {
    return dot(c, RIFT_LUMA);
}

float maskAt(vec2 uv) {
    vec4 m = texture(u_Mask, clamp(uv, vec2(0.0), vec2(1.0)));
    return max(m.a, max(m.r, max(m.g, m.b)));
}

vec3 sceneAt(vec2 uv) {
    return texture(u_Src, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
}

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm3(vec2 p) {
    float f = 0.0;
    float a = 0.56;
    mat2 r = mat2(0.80, 0.60, -0.60, 0.80);
    for (int i = 0; i < 3; ++i) {
        f += valueNoise(p) * a;
        p = r * p * 2.07 + vec2(17.3, 31.7);
        a *= 0.50;
    }
    return f;
}

vec2 riftFlow(vec2 uv, float time, float speed) {
    vec2 aspect = vec2(u_Screen.x / max(u_Screen.y, 1.0), 1.0);
    vec2 p = (uv - 0.5) * aspect * 4.2;
    float t = time * speed;
    float a = fbm3(p + vec2(0.11 * t, -0.17 * t));
    float b = fbm3(p * 1.17 + vec2(9.1 - 0.15 * t, 3.7 + 0.12 * t));
    vec2 flow = vec2(a - 0.5, b - 0.5);
    flow += vec2(
        sin(p.y * 2.35 + t * 0.72 + b * 4.0),
        cos(p.x * 2.10 - t * 0.63 + a * 4.4)
    ) * 0.22;
    return flow;
}

vec2 maskNormal(vec2 uv, vec2 texel, float radiusPx) {
    float r = clamp(radiusPx, 1.0, 7.0);
    vec2 dx = vec2(texel.x * r, 0.0);
    vec2 dy = vec2(0.0, texel.y * r);
    float l = maskAt(uv - dx);
    float rr = maskAt(uv + dx);
    float d = maskAt(uv - dy);
    float uu = maskAt(uv + dy);
    return safeNormalize(vec2(l - rr, d - uu), vec2(0.0, -1.0));
}

float innerEdgeBand(vec2 uv, vec2 texel, float radiusPx) {
    vec2 dx = vec2(texel.x * radiusPx, 0.0);
    vec2 dy = vec2(0.0, texel.y * radiusPx);
    float center = maskAt(uv);
    float eroded = min(min(maskAt(uv - dx), maskAt(uv + dx)),
                       min(maskAt(uv - dy), maskAt(uv + dy)));
    return saturate((center - eroded) * 2.25);
}

float outerField(vec2 uv, vec2 texel, float radiusPx, int quality, out vec2 towardHand, out float hitMask) {
    float best = 2.0;
    float bestMask = 0.0;
    vec2 bestDir = vec2(0.0, -1.0);
    int rings = quality <= 1 ? 3 : 4;
    int dirs = quality <= 2 ? 8 : 12;
    bool eight = dirs == 8;
    vec2 rotation = eight ? RIFT_EDGE_ROTATE_8 : RIFT_EDGE_ROTATE_12;

    for (int ring = 1; ring <= 4; ++ring) {
        if (ring > rings) continue;
        float rr;
        if (rings == 3) {
            rr = ring == 1 ? 0.20 : (ring == 2 ? 0.54 : 1.0);
        } else {
            rr = ring == 1 ? 0.16 : (ring == 2 ? 0.38 : (ring == 3 ? 0.66 : 1.0));
        }
        float distPx = radiusPx * rr;
        vec2 dir = eight ? RIFT_EDGE_START_8[ring - 1] : RIFT_EDGE_START_12[ring - 1];
        for (int i = 0; i < 12; ++i) {
            if (i >= dirs) break;
            float m = maskAt(uv + dir * texel * distPx);
            if (m > 0.018) {
                best = rr;
                bestMask = max(bestMask, m);
                bestDir = dir;
            }
            dir = vec2(dir.x * rotation.x - dir.y * rotation.y,
                       dir.x * rotation.y + dir.y * rotation.x);
        }
        // Rings are visited from near to far; once one intersects the silhouette, the coarser
        // rings cannot improve the distance estimate enough to justify another 8/12 probes.
        if (bestMask > 0.018) break;
    }

    towardHand = safeNormalize(bestDir, vec2(0.0, -1.0));
    hitMask = bestMask;
    return best;
}

vec3 chromaticScene(vec2 uv, vec2 axis, vec2 texel, float splitPx) {
    vec2 c = safeNormalize(axis, vec2(1.0, 0.0)) * texel * splitPx;
    return vec3(
        sceneAt(uv + c).r,
        sceneAt(uv).g,
        sceneAt(uv - c).b
    );
}

void main() {
    vec2 resolution = max(u_Screen.xy, vec2(1.0));
    vec2 texel = 1.0 / resolution;
    vec2 uv = v_TexCoord;
    vec3 base = sceneAt(uv);

    float mask = saturate(maskAt(uv));
    float time = u_Screen.z;
    float fieldWidth = max(u_Screen.w, 1.0);
    float distortionPx = max(u_Warp.x, 0.0);
    float flowSpeed = max(u_Warp.y, 0.0);
    float causticStrength = max(u_Warp.z, 0.0);
    int quality = clamp(int(u_Warp.w + 0.5), 1, 4);
    float glowStrength = max(u_Effects.x, 0.0);
    float shadowStrength = max(u_Effects.y, 0.0);

    if (mask > 0.01) {
        vec2 flow = riftFlow(uv, time, flowSpeed);
        // The object is not merely tinted: its already-rendered surface is advected through a
        // silhouette-aware refractive field. Near the edge the sampling can cross the mask, which
        // bends the apparent contour instead of leaving a perfectly rigid cutout.
        vec2 normal = maskNormal(uv, texel, 2.5 + distortionPx * 0.08);
        vec2 tangent = vec2(-normal.y, normal.x);
        float edgeTight = innerEdgeBand(uv, texel, 2.0);
        float edgeWide = innerEdgeBand(uv, texel, 6.0 + distortionPx * 0.10);

        vec2 px = uv * resolution;
        float foldPhase = dot(px / max(fieldWidth, 6.0), tangent) * 5.4
                + fbm3(uv * vec2(11.0, 8.0) + flow * 1.8) * 5.2
                - time * flowSpeed * 1.32;
        float fold = sin(foldPhase);
        float torsion = cos(foldPhase * 0.61 + flow.x * 4.0 + time * flowSpeed * 0.37);
        float bodyEnvelope = 0.42 + edgeWide * 0.58;
        vec2 warpDir = safeNormalize(
                tangent * (fold * 0.78 + flow.x * 0.90)
                + normal * (torsion * 0.48 + flow.y * 0.62),
                normal
        );
        float warpPx = distortionPx * bodyEnvelope * (0.44 + 0.34 * abs(fold) + 0.22 * abs(torsion));
        vec2 warpedUv = clamp(uv + warpDir * texel * warpPx, vec2(0.001), vec2(0.999));

        // Crossing the silhouette is deliberate but bounded. This is what gives swords/tools a
        // slight liquid bend instead of only scrolling their texture inside an unchanged outline.
        float warpedMask = maskAt(warpedUv);
        float crossEdge = saturate((0.30 - warpedMask) * 2.6) * (0.32 + edgeWide * 0.68);
        vec2 guardedUv = mix(warpedUv, uv + warpDir * texel * warpPx * 0.28, crossEdge * 0.72);

        float chromaPx = min(3.2, 0.30 + distortionPx * 0.115) * (0.35 + edgeWide * 0.65);
        vec3 warped = chromaticScene(guardedUv, warpDir, texel, chromaPx);

        float ridge = pow(1.0 - abs(sin(foldPhase * 1.27 + torsion * 1.8)), 5.0);
        float secondary = pow(1.0 - abs(sin(foldPhase * 0.73 - flow.y * 5.0 + 1.4)), 7.0);
        float caustic = saturate((ridge * 0.72 + secondary * 0.46) * causticStrength);
        caustic *= 0.44 + edgeWide * 0.56;

        float warpedLuma = luminance(warped);
        float baseLuma = luminance(base);
        float opticalGain = saturate((warpedLuma - baseLuma) * 2.2 + 0.34);
        vec3 focused = warped * (1.0 + caustic * (0.07 + opticalGain * 0.12));
        focused += u_Caustic.rgb * caustic * u_Caustic.a * glowStrength * 0.075;

        float compression = edgeTight * u_Shadow.a * shadowStrength * (0.10 + 0.08 * abs(torsion));
        focused = mix(focused, u_Shadow.rgb, saturate(compression));

        // Tint is intentionally weak: Rift remains a refractive material, not a flat Chams fill.
        float tintAmount = saturate(u_Fill.a) * (0.08 + caustic * 0.055);
        focused = mix(focused, focused * mix(vec3(1.0), u_Fill.rgb, 0.34), tintAmount);

        color = vec4(saturate(focused), 1.0);
        return;
    }

    vec2 towardHand;
    float hitMask;
    float distanceNorm = outerField(uv, texel, fieldWidth, quality, towardHand, hitMask);
    if (hitMask <= 0.01 || distanceNorm > 1.0) {
        color = vec4(base, 1.0);
        return;
    }

    // Outside the silhouette, space is pulled toward the object while the tangential curl makes
    // the distortion trail like smoke. Caustic ridges use the same nearest-edge direction, so
    // they inherit the held item's shape instead of looking like a screen-space overlay.
    float envelope = saturate(1.0 - distanceNorm);
    envelope = envelope * envelope * (3.0 - 2.0 * envelope);
    vec2 flow = riftFlow(uv, time, flowSpeed);
    vec2 normal = safeNormalize(towardHand, vec2(0.0, -1.0));
    vec2 tangent = vec2(-normal.y, normal.x);
    vec2 px = uv * resolution;

    float smokePhase = dot(px / max(fieldWidth, 6.0), tangent) * 7.1
            + distanceNorm * 8.4
            + fbm3(uv * vec2(9.0, 6.5) + flow * 1.7) * 6.0
            - time * flowSpeed * 1.48;
    float ribbon = pow(1.0 - abs(sin(smokePhase)), 6.5);
    float ribbon2 = pow(1.0 - abs(sin(smokePhase * 0.61 + flow.y * 5.4 + 1.7)), 8.0);
    float caustic = saturate((ribbon * 0.80 + ribbon2 * 0.52) * causticStrength) * envelope;

    vec2 curl = tangent * (flow.x * 1.25 + sin(smokePhase * 0.31) * 0.34)
              + normal * (0.70 + flow.y * 0.42);
    vec2 warpDir = safeNormalize(curl, normal);
    float suction = distortionPx * envelope * (0.48 + caustic * 0.52);
    vec2 refractUv = clamp(uv + warpDir * texel * suction, vec2(0.001), vec2(0.999));

    float chromaPx = min(2.8, 0.24 + distortionPx * 0.09) * envelope * (0.55 + caustic * 0.45);
    vec3 refracted = chromaticScene(refractUv, warpDir, texel, chromaPx);

    // Pull a second sample from slightly deeper in the field. Where that sample enters the hand
    // mask, the item's rendered edge gets optically dragged into the wake, visibly bending shape.
    vec2 foldUv = clamp(refractUv + normal * texel * distortionPx * envelope * 0.38,
                        vec2(0.001), vec2(0.999));
    float foldMask = maskAt(foldUv);
    vec3 folded = sceneAt(foldUv);
    refracted = mix(refracted, folded, saturate(foldMask * envelope * (0.28 + caustic * 0.18)));

    float sceneGain = saturate(abs(luminance(refracted) - luminance(base)) * 2.4 + 0.18);
    vec3 outCol = mix(base, refracted, saturate(envelope * (0.56 + distortionPx * 0.018)));
    outCol += refracted * caustic * (0.014 + sceneGain * 0.028);
    outCol += u_Caustic.rgb * caustic * u_Caustic.a * glowStrength * (0.045 + sceneGain * 0.035);

    float darkWake = envelope * (1.0 - caustic) * u_Shadow.a * shadowStrength * 0.095;
    outCol = mix(outCol, u_Shadow.rgb, saturate(darkWake));

    color = vec4(saturate(outCol), 1.0);
}
