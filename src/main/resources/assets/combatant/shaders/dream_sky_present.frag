#version 330 core
#extension GL_ARB_separate_shader_objects : require
/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
uniform sampler2D u_SkyHDR;
layout(location = 0) in vec2 v_TexCoord;
layout(location = 0) out vec4 color;

#include "include/scene_color.glsl"

void main() {
    vec3 linear = max(texture(u_SkyHDR, v_TexCoord).rgb, vec3(0.));
    vec3 encoded = combatantOutputTransform(linear);
    vec2 p = floor(gl_FragCoord.xy);
    float n0 = fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
    float n1 = fract(sin(dot(p + vec2(31.7, 97.1), vec2(269.5, 183.3))) * 47291.274);
    color = vec4(clamp(encoded + (n0 - n1) / 255., 0., 1.), 1.);
}
