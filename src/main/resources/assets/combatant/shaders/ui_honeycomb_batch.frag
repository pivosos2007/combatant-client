#version 330 core
#extension GL_ARB_separate_shader_objects : require

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
        float drain = 1.0 - smoothstep(1.02, 2.55, elapsed);
        float limit = 1.0 - smoothstep(radius * 2.75, radius * 3.55, distancePx);
        pulse = clamp(approach * drain * limit, 0.0, 1.0);
        float rimAngle = atan(local.y, local.x);
        float flow = 0.74 + 0.26 * sin(rimAngle * 2.0 - v_Params3.z * 4.1 + distancePx / radius * 0.86);
        float drainage = smoothstep(0.75, 2.15, elapsed);
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
    if (alpha <= 0.001) discard;

    vec3 hot = clamp(v_Color.rgb, vec3(0.0), vec3(1.0));
    vec3 cold = clamp(v_Params7.rgb, vec3(0.0), vec3(1.0));
    vec3 tint = mix(cold, hot, hotness);
    float white = inner * pulse * 0.76;
    vec3 color = mix(tint, vec3(1.0), clamp(white, 0.0, 0.80));
    fragColor = vec4(color, alpha);
#ifdef COMBATANT_ANALYTIC_CLIP
    vec2 pixel = uScreen.zw / max(uScreen.xy, vec2(1.0));
    fragColor.a *= combatantClipCoverage(combatantClipDistance(combatantLogicalFragCoord()), pixel);
    if (fragColor.a <= 0.001) discard;
#endif
}
