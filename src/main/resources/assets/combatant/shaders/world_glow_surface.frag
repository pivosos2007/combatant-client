#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout(location = 0) in vec4 v_Color;
layout(location = 1) in vec2 v_UV;
layout(location = 2) in vec3 v_ViewNormal;
layout(location = 3) in vec3 v_ViewPosition;
layout(location = 4) in vec4 v_Fx;
layout(location = 5) in float v_Rim;

layout(location = 0) out vec4 fragColor;

void main() {
    float fade = clamp(v_Fx.x, 0.0, 1.0);
    float time = v_Fx.y;
    float phase = v_Fx.z;
    float scan = v_Fx.w;

    vec3 normal = normalize(v_ViewNormal);
    vec3 viewDir = normalize(-v_ViewPosition);
    float fresnel = pow(1.0 - abs(dot(normal, viewDir)), 2.2);

    // Edge distance measured in screen pixels so the rim keeps the same crisp width at any range
    // instead of ballooning on nearby faces and vanishing on distant ones.
    vec2 fw = max(fwidth(v_UV), vec2(1.0e-5));
    vec2 edgePx = min(v_UV, 1.0 - v_UV) / fw;
    float edge = min(edgePx.x, edgePx.y);
    float core = 1.0 - smoothstep(0.7, 1.9, edge);
    float halo = exp(-edge * 0.26);

    // Bright band that climbs the face, plus a faint diagonal sheen so large faces do not read as
    // flat translucent cards.
    float sweep = fract(v_UV.y * 0.7 + time * 0.38 + phase);
    float band = smoothstep(0.0, 0.05, sweep) * (1.0 - smoothstep(0.05, 0.30, sweep));
    float sheen = 0.5 + 0.5 * sin((v_UV.x + v_UV.y) * 17.0 - time * 2.1 + phase * 6.2831);

    vec3 base = v_Color.rgb;
    vec3 hot = mix(base, vec3(1.0), 0.55);

    float rimWeight = v_Rim * (0.12 + 0.88 * fade);
    float glow = core * 0.92 + halo * 0.34 + fresnel * 0.22;
    float bandGlow = band * scan * fade;

    vec3 rgb = base * (0.86 + 0.14 * sheen)
            + hot * (core * 0.85 + bandGlow * 0.55) * clamp(rimWeight * 2.0, 0.0, 1.0);

    float alpha = v_Color.a * (0.90 + 0.22 * sheen)
            + rimWeight * glow
            + v_Rim * bandGlow * 0.35;
    alpha = clamp(alpha, 0.0, 1.0);

    if (alpha <= 0.003) discard;
    fragColor = vec4(min(rgb, vec3(1.0)), alpha);
}
