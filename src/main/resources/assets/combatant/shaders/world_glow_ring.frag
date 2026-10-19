#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

// Procedural ground ring: bright main ring with halo, a dashed outer rim whose dashes rotate with
// the quad, and a faint inner disc. Vertex colours stay per-corner so animated palettes still show.

layout(location = 0) in vec2 v_TexCoord;
layout(location = 2) in vec4 v_Color;

layout(location = 0) out vec4 fragColor;

const float TAU = 6.28318530718;

void main() {
    vec2 p = v_TexCoord * 2.0 - 1.0;
    float r = length(p);
    if (r >= 1.0) discard;

    float ringR = 0.74;
    float dr = r - ringR;
    float core = exp(-dr * dr / 0.0030);
    float halo = exp(-abs(dr) * 8.5) * 0.42;

    float angle = atan(p.y, p.x);
    float dash = smoothstep(0.15, 0.55, 0.5 + 0.5 * sin(angle * 18.0));
    float rimR = 0.92;
    float rim = exp(-pow((r - rimR) / 0.022, 2.0)) * dash * 0.85;

    float disc = (1.0 - smoothstep(0.0, ringR, r)) * 0.10 + smoothstep(ringR - 0.26, ringR, r) * (1.0 - smoothstep(ringR, ringR + 0.02, r)) * 0.10;
    float spokes = exp(-pow((r - 0.40) / 0.012, 2.0)) * (0.5 + 0.5 * sin(angle * 6.0)) * 0.35;
    float edge = 1.0 - smoothstep(0.90, 1.0, r);

    float shape = (core * (0.85 + 0.15 * dash) + halo + rim * edge + disc + spokes) * (1.0 - smoothstep(0.96, 1.0, r));
    vec3 rgb = mix(v_Color.rgb, vec3(1.0), core * 0.35);
    float alpha = clamp(shape, 0.0, 1.0) * v_Color.a;
    if (alpha <= 0.002) discard;
    fragColor = vec4(rgb, alpha);
}
