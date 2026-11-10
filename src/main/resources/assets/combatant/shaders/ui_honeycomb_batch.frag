#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout(location = 1) in vec4 v_Local;
layout(location = 2) in vec4 v_Color;
layout(location = 3) in vec4 v_Rect;
layout(location = 4) in vec4 v_Params;
layout(location = 5) in vec4 v_Params2;
layout(location = 6) in vec4 v_Params3;
layout(location = 7) in vec4 v_Params4;
layout(location = 8) in vec4 v_Params5;
layout(location = 9) in vec4 v_Params6;
layout(location = 10) in vec4 v_Params7;
layout(location = 0) out vec4 fragColor;

layout(std140) uniform UIBatch {
    vec4 uScreen;
    vec4 uLayer;
};

#ifdef COMBATANT_ANALYTIC_CLIP
#include <combatant:ui_clip.glsl>
#endif

const float SQRT3 = 1.7320508075688772;
const float COS30 = 0.8660254037844386;

void nearestHex(vec2 p, float radius, out vec2 center, out vec2 axial) {
    float q = (p.x / SQRT3 - p.y / 3.0) / radius;
    float r = (2.0 * p.y / 3.0) / radius;
    vec3 c = vec3(q, -q - r, r);
    vec3 v = floor(c + 0.5);
    vec3 d = abs(v - c);
    if (d.x > d.y && d.x > d.z) v.x = -v.y - v.z;
    else if (d.y > d.z) v.y = -v.x - v.z;
    else v.z = -v.x - v.y;
    axial = vec2(v.x, v.z);
    center = vec2(SQRT3 * radius * (axial.x + axial.y * 0.5), 1.5 * radius * axial.y);
}

float hexEdge(vec2 p, float radius) {
    p = abs(p);
    return max(p.x - COS30 * radius,
               (p.x / SQRT3 + p.y - radius) * COS30);
}

float roundedRectSdf(vec2 p, vec4 rect, float radius) {
    vec2 halfSize = rect.zw * 0.5;
    float r = clamp(radius, 0.0, min(halfSize.x, halfSize.y));
    vec2 q = abs(p - (rect.xy + halfSize)) - (halfSize - vec2(r));
    return length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

bool chamferedContains(vec2 p, vec4 rect, float cut) {
    if (rect.z <= 0.0 || rect.w <= 0.0) return false;
    vec2 h = rect.zw * 0.5;
    vec2 d = abs(p - rect.xy - h);
    float c = clamp(cut, 0.0, min(h.x, h.y));
    return d.x <= h.x && d.y <= h.y && d.x + d.y <= h.x + h.y - c;
}

float hash21(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float crystalHash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float crystalNoise(vec2 p) {
    vec2 f = fract(p);
    vec2 i = floor(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(crystalHash(i), crystalHash(i + vec2(1.0, 0.0)), f.x),
               mix(crystalHash(i + vec2(0.0, 1.0)), crystalHash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float crystalMass(vec2 p) {
    float result = 0.0;
    float weight = 0.56;
    for (int i = 0; i < 3; i++) {
        result += crystalNoise(p) * weight;
        p = p * 2.07 + vec2(13.7, 7.3);
        weight *= 0.48;
    }
    return result;
}

vec4 crystalInterior(vec2 local, vec2 axial, float radius, float stroke, float time,
                     vec2 p, vec2 mousePosition, vec3 themeColor, vec3 secondaryColor,
                     float strength, float sheen, float speed, float aa) {
    float signedEdge = hexEdge(local, radius);
    float interior = 1.0 - smoothstep(-stroke * 3.5 - aa * 2.0,
                                       -stroke * 2.05 - aa * 1.1, signedEdge);
    if (interior <= 0.0001) return vec4(0.0);

    float id = crystalHash(axial + vec2(27.1, 13.7));
    float id2 = crystalHash(axial + vec2(67.3, 29.8));
    float cellTime = time * max(speed, 0.02) * (0.09 + 0.045 * id2);
    vec2 q = local / radius;
    float angle = cellTime * mix(-1.0, 1.0, step(0.5, id)) + 6.2831853 * id;
    float sn = sin(angle);
    float cs = cos(angle);
    vec2 orbit = vec2(cs * q.x - sn * q.y, sn * q.x + cs * q.y);
    vec2 uv = orbit * 3.25 + vec2(id * 8.9, id2 * 7.1);
    vec2 drift = vec2(sin(cellTime * 1.3 + id * 5.0), cos(cellTime * 1.1 + id2 * 5.0));
    float warp = crystalNoise(uv * 0.78 + drift * 0.55);
    float density = crystalMass(uv + vec2(warp * 0.62, -warp * 0.51) + drift * 0.32);
    float folds = smoothstep(0.27, 0.76, density);

    float cursor = 0.0;
    if (mousePosition.x >= 0.0 && mousePosition.y >= 0.0) {
        float d = length(p - mousePosition);
        cursor = 1.0 - smoothstep(radius * 0.30, radius * 2.70, d);
    }
    float activity = clamp(folds * 0.42 + cursor * 0.48, 0.0, 1.0);
    float subtleSheen = pow(max(0.0, 0.55 + 0.45 * (q.y * 0.65 - q.x * 0.35)), 2.0);

    vec3 tint = mix(secondaryColor, themeColor, 0.67);
    vec3 color = tint * (0.34 + 0.38 * folds + 0.19 * cursor);
    color += tint * (0.04 + 0.13 * sheen) * subtleSheen;
    float alpha = interior * strength * (0.055 + 0.095 * folds + 0.060 * cursor);
    alpha *= 0.88 + 0.12 * activity;
    return vec4(clamp(color, vec3(0.0), vec3(1.0)), clamp(alpha, 0.0, 0.38));
}


void main() {
    vec2 p = v_Local.xy / (abs(v_Local.z) > 0.000001 ? v_Local.z : 1.0);
    if (v_Params4.w > 1.5 && chamferedContains(p, v_Params5, v_Params4.z)) discard;
    float coverage = 1.0;
    if (v_Params4.w > 0.5 && v_Params4.w < 1.5) {
        float d = roundedRectSdf(p, v_Params5, v_Params4.z);
        float aa = max(fwidth(d), 0.7);
        coverage = 1.0 - smoothstep(-aa, aa, d);
        if (coverage < 0.001) discard;
    }

    float radius = max(v_Params.x, 2.0);
    float stroke = max(v_Params.y, 0.15);
    vec2 center, cell;
    nearestHex(p - v_Params4.xy, radius, center, cell);
    vec2 local = p - v_Params4.xy - center;
    float edge = abs(hexEdge(local, radius));
    float aa = max(fwidth(edge), 0.65);
    float mode = v_Params3.w;
    float pulse = 0.0;
    float hotness = 1.0;

    if (mode < 0.5 && v_Params2.w > 0.0001
            && v_Params6.x <= 0.0001 && v_Params6.y <= 0.0001 && v_Params6.z <= 0.0001) {
        fragColor = crystalInterior(local, cell, radius, stroke, v_Params.w,
                                    p, v_Params2.xy, clamp(v_Color.rgb, 0.0, 1.0),
                                    clamp(v_Params7.rgb, 0.0, 1.0),
                                    v_Params2.w, v_Params7.w, v_Params6.w, aa);
        fragColor.a *= clamp(v_Params.z, 0.0, 1.0) * v_Color.a * coverage;
#ifdef COMBATANT_ANALYTIC_CLIP
        vec2 pixel = uScreen.zw / max(uScreen.xy, vec2(1.0));
        fragColor.a *= combatantClipCoverage(combatantClipDistance(combatantLogicalFragCoord()), pixel);
#endif
        if (fragColor.a <= 0.001) discard;
        return;
    }


    if (mode > 0.5 && mode < 1.5) {
        vec2 source = v_Params3.xy;
        vec2 displacement = p - source;
        float distancePx = length(displacement);
        float speed = radius * 2.35 * max(v_Params6.w, 0.3);
        float angle = atan(displacement.y, displacement.x);
        float bend = radius * 0.10 * sin(angle * 3.0 + v_Params3.z * 1.25)
                   + radius * 0.045 * sin((p.x + p.y) / radius * 2.4 - v_Params3.z * 2.1);
        float arrival = max(0.0, distancePx + bend) / speed;
        float elapsed = v_Params3.z - arrival;
        float approach = smoothstep(-0.14, 0.30, elapsed);
        float drain = 1.0 - smoothstep(1.65, 4.85, elapsed);
        float limit = 1.0 - smoothstep(radius * 2.75, radius * 3.55, distancePx);
        pulse = clamp(approach * drain * limit, 0.0, 1.0);
        float rimAngle = atan(local.y, local.x);
        float flow = 0.74 + 0.26 * sin(rimAngle * 2.0 - v_Params3.z * 4.1 + distancePx / radius * 0.86);
        float drainage = smoothstep(1.25, 4.10, elapsed);
        pulse *= mix(1.0, flow, drainage);
        float end = smoothstep(radius * 2.35, radius * 3.40, distancePx);
        float streak = 0.55 + 0.45 * sin(angle * 5.0 + distancePx / radius * 1.55 - v_Params3.z * 1.5);
        float ending = smoothstep(0.20, 0.78, streak);
        pulse *= mix(1.0, ending, end * (1.0 - drain) * 0.55);
        hotness = 1.0 - smoothstep(radius * 0.55, radius * 2.25, distancePx);
    } else if (mode < 0.5 && v_Params2.x >= 0.0 && v_Params2.y >= 0.0) {
        float distancePx = length(p - v_Params2.xy);
        pulse = 0.18 * (1.0 - smoothstep(0.0, radius * 1.1, distancePx));
    } else if (mode > 1.5) {
        float distancePx = length(p - v_Params3.xy);
        float falloff = 1.0 - smoothstep(v_Params2.z * 0.25, v_Params2.z, distancePx);
        float flutter = 0.74 + 0.26 * sin(v_Params3.z + cell.x * 0.31 + cell.y * 0.22);
        pulse = max(0.0, falloff * flutter);
        hotness = 0.55 + 0.20 * sin(v_Params3.z * 0.48 + distancePx / radius);
    }

    float base = (1.0 - smoothstep(stroke * 0.35, stroke + aa, edge)) * v_Params6.x;
    if (mode > 0.5) base = 0.0;

    float wide = stroke * 1.22 + 0.78 + pulse * 2.05;
    float inner = 1.0 - smoothstep(wide - aa, wide + 1.5 * aa, edge);
    float bevel = exp(-pow(max(0.0, edge - wide) / 3.4, 1.45));
    float normalZ = clamp(1.0 - max(0.0, edge - wide) / 7.0, 0.0, 1.0);
    float fresnel = pow(1.0 - normalZ, 3.0);
    float shoulder = bevel * (0.30 + 0.70 * fresnel);
    float halo = exp(-0.5 * pow(max(0.0, edge - wide) / 7.6, 2.0));

    float glow = (inner * (0.58 + pulse * 0.28) * v_Params6.y + shoulder * 0.34 + halo * (0.17 + pulse * 0.15) * v_Params6.z) * pulse;
    float alpha = clamp(base + glow, 0.0, 0.965);
    alpha *= clamp(v_Params.z, 0.0, 1.0) * v_Color.a * coverage;

    vec3 hot = clamp(v_Color.rgb, vec3(0.0), vec3(1.0));
    vec3 cold = clamp(v_Params7.rgb, vec3(0.0), vec3(1.0));
    vec3 tint = mix(cold, hot, hotness);
    float white = inner * pulse * 0.76;
    vec3 color = mix(tint, vec3(1.0), clamp(white, 0.0, 0.80));

    if (mode > 0.5 && v_Params2.w > 0.0001 && pulse > 0.0001) {
        float sdf = hexEdge(local, radius);
        float interior = 1.0 - smoothstep(-stroke * 3.5 - aa * 2.0,
                                          -stroke * 2.05 - aa * 1.1, sdf);
        if (interior > 0.0001) {
            float scattering = interior * clamp(v_Params2.w * 0.075 * pulse, 0.0, 0.14);
            scattering *= clamp(v_Params.z, 0.0, 1.0) * v_Color.a * coverage;
            float combined = alpha + scattering * (1.0 - alpha);
            color = (color * alpha + tint * (0.46 + 0.19 * pulse) * scattering * (1.0 - alpha))
                    / max(combined, 0.0001);
            alpha = combined;
        }
    }
    if (alpha <= 0.001) discard;
    fragColor = vec4(color, alpha);
#ifdef COMBATANT_ANALYTIC_CLIP
    vec2 pixel = uScreen.zw / max(uScreen.xy, vec2(1.0));
    fragColor.a *= combatantClipCoverage(combatantClipDistance(combatantLogicalFragCoord()), pixel);
    if (fragColor.a <= 0.001) discard;
#endif
}
