#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

out vec4 color;

layout (std140) uniform SkySun {
    vec4 u_Params;
};

in vec2 v_TexCoord;
in vec4 v_Color;

void main() {
    vec2 p = v_TexCoord * 2.0 - 1.0;
    float r = length(p);

    float innerRatio = clamp(u_Params.x, 0.05, 0.95);
    float haloStrength = max(u_Params.y, 0.0);
    float coreStrength = max(u_Params.z, 0.0);
    float rayStrength = max(u_Params.w, 0.0);

    float disc = 1.0 - smoothstep(innerRatio * 0.66, innerRatio * 1.01, r);
    float haloOuter = 1.0 - smoothstep(innerRatio * 0.92, 1.0, r);
    float halo = pow(haloOuter, 1.45) * haloStrength;

    vec2 n = normalize(p + vec2(0.0001));
    float crossAxis = max(abs(n.x), abs(n.y));
    float ray = pow(max(1.0 - r, 0.0), 5.0) * pow(crossAxis, 22.0) * rayStrength;

    float alpha = clamp(disc * coreStrength + halo + ray, 0.0, 1.0);
    if (alpha <= 0.0001) discard;

    vec3 rgb = v_Color.rgb * alpha;
    color = vec4(rgb, alpha * v_Color.a);
}
