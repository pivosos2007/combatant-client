#version 330 core

/*

This file is part of the Combatant Client distribution.
Copyright (c) 2026 pivosos2007.


The original wave effect was based on a shader published on ShaderToy.
The original shader link and author attribution were not preserved.
This implementation has since been substantially modified for Combatant.

Licensed under the GNU General Public License v3.0.
*/

out vec4 color;

layout (std140) uniform MenuBackground {
    vec4 u_Params;     // x = width, y = height, z = time, w = pad
    vec4 u_Accent;     // rgb
    vec4 u_Background; // rgb
};

in vec2 v_TexCoord;

void main() {
    vec2 res = u_Params.xy;
    float time = u_Params.z;

    vec2 fragCoord = v_TexCoord * res;
    vec2 uv = (2.0 * fragCoord - res) / min(res.x, res.y);

    for (float i = 1.0; i < 8.0; i++) {
        uv.y += 0.1 * sin(uv.x * i * i + time * 0.5)
                * sin(uv.y * i * i + time * 0.5);
    }

    float v = clamp(uv.y, 0.0, 1.0);
    float band = smoothstep(0.0, 1.0, v);
    float crest = smoothstep(0.35, 0.95, v);

    vec3 bg = u_Background.rgb;
    vec3 accent = u_Accent.rgb;
    vec3 mid = mix(bg, accent, 0.35);
    vec3 light = mix(accent, vec3(1.0), 0.25);

    vec3 col = mix(bg, mid, band);
    col = mix(col, light, crest * 0.55);
    col = max(col, bg * 0.65 + vec3(0.01, 0.015, 0.02));

    color = vec4(clamp(col, 0.0, 1.0), 1.0);
}
