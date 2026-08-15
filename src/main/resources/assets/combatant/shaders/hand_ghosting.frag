#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

in vec2 v_TexCoord;
out vec4 color;

uniform sampler2D u_Src;
uniform sampler2D u_History;

layout (std140) uniform HandGhosting {
    vec4 u_Screen; // xy = framebuffer size, z = delta seconds, w = time
    vec4 u_Color;  // rgba = resolved ghost color
    vec4 u_Params; // x = decay, y = strength, z = blur px, w = current rejection
};

float trailAt(vec2 uv) {
    vec2 temporal = texture(u_History, uv).rg;
    return max(temporal.r - temporal.g * clamp(u_Params.w, 0.0, 1.25), 0.0);
}

void main() {
    vec4 base = texture(u_Src, v_TexCoord);
    vec2 resolution = max(u_Screen.xy, vec2(1.0));
    vec2 texel = 1.0 / resolution;
    float radius = max(u_Params.z, 0.0);

    float core = trailAt(v_TexCoord);
    float halo = core;

    if (radius > 0.01) {
        // Six directions at two radii are enough for a soft trail. The history texture stores both
        // accumulated and current masks, so each sample is one texture read instead of two.
        const int DIRS = 6;
        for (int i = 0; i < DIRS; ++i) {
            float angle = (float(i) + 0.5) * 6.28318530718 / float(DIRS);
            vec2 dir = vec2(cos(angle), sin(angle));
            halo += trailAt(v_TexCoord + dir * texel * radius);
            halo += trailAt(v_TexCoord + dir * texel * radius * 0.45) * 0.65;
        }
        halo /= 1.0 + float(DIRS) * 1.65;
    }

    float strength = max(u_Params.y, 0.0);
    float trail = max(core, halo * 0.90);
    float alpha = clamp(trail * u_Color.a * strength, 0.0, 0.94);

    // A small additive halo keeps the afterimage readable over bright Metallic/Glass without
    // turning a stationary hand into a glow: current-frame coverage is rejected in trailAt().
    vec3 outColor = mix(base.rgb, u_Color.rgb, alpha);
    outColor += u_Color.rgb * halo * u_Color.a * strength * 0.16;
    color = vec4(clamp(outColor, 0.0, 1.0), base.a);
}
