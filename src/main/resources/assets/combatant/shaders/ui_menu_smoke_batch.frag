#version 330 core
#extension GL_ARB_separate_shader_objects : require

layout(location = 1) in vec4 v_Local;
layout(location = 2) in vec4 v_Color;
layout(location = 3) in vec4 v_Rect;
layout(location = 4) in vec4 v_Params;
layout(location = 5) in vec4 v_Params2;
layout(location = 0) out vec4 fragColor;

layout(std140) uniform UIBatch {
    vec4 uScreen;
    vec4 uLayer;
};

#ifdef COMBATANT_ANALYTIC_CLIP
#include <combatant:ui_clip.glsl>
#endif

float hash21(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(cell), hash21(cell + vec2(1.0, 0.0)), f.x),
               mix(hash21(cell + vec2(0.0, 1.0)), hash21(cell + vec2(1.0)), f.x), f.y);
}

void main() {
    vec2 p = v_Local.xy / (abs(v_Local.z) > 0.000001 ? v_Local.z : 1.0);
    vec2 relative = (p - v_Params.xy) / max(v_Params.zw, vec2(1.0));
    float age = v_Params2.x;
    float fade = 1.0 - smoothstep(0.30, 2.80, age);
    if (fade <= 0.001) discard;
    vec2 velocity = vec2(v_Params2.y, v_Params2.w);
    relative -= velocity * age * 0.10;
    float angle = age * 0.48 * sign(velocity.x + 0.01);
    mat2 spin = mat2(cos(angle), -sin(angle), sin(angle), cos(angle));
    relative = spin * relative;
    float n = noise(relative * 5.0 + vec2(age * 0.12, -age * 0.08));
    float f = noise(relative * 10.5 - vec2(age * 0.09, age * 0.13));
    float warp = 1.0 + (n - 0.5) * 0.17 + (f - 0.5) * 0.06;
    float radius = length(relative * vec2(1.0, 1.15));
    float body = 1.0 - smoothstep(0.22 * warp, 0.64 * warp, radius);
    float alpha = body * fade * v_Params2.z * v_Color.a * 0.105;
    if (alpha <= 0.001) discard;
    fragColor = vec4(v_Color.rgb, clamp(alpha, 0.0, 0.16));
#ifdef COMBATANT_ANALYTIC_CLIP
    vec2 pixel = uScreen.zw / max(uScreen.xy, vec2(1.0));
    fragColor.a *= combatantClipCoverage(combatantClipDistance(combatantLogicalFragCoord()), pixel);
    if (fragColor.a <= 0.001) discard;
#endif
}
