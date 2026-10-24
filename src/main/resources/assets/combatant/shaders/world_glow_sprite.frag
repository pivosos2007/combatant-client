#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

// Procedural soft glow sprite: hot core, wide halo, and a tiny cross flare. Replaces the baked
// firefly texture so large sprites stay smooth instead of showing texel steps.

layout(location = 0) in vec2 v_TexCoord;
layout(location = 2) in vec4 v_Color;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 p = v_TexCoord * 2.0 - 1.0;
    float d2 = dot(p, p);
    if (d2 >= 1.0) discard;

    float d = sqrt(d2);
    float core = exp(-d2 * 16.0);
    float halo = exp(-d * 3.6) * 0.46;
    float flare = exp(-abs(p.x) * 22.0) * exp(-abs(p.y) * 2.6) * 0.20
            + exp(-abs(p.y) * 22.0) * exp(-abs(p.x) * 2.6) * 0.20;
    float edge = 1.0 - smoothstep(0.78, 1.0, d);

    float shape = (core + halo + flare) * edge;
    vec3 rgb = mix(v_Color.rgb, vec3(1.0), core * 0.55);
    float alpha = clamp(shape, 0.0, 1.0) * v_Color.a;
    if (alpha <= 0.002) discard;
    fragColor = vec4(rgb, alpha);
}
