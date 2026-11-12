#version 330 core
#extension GL_ARB_separate_shader_objects : require
/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
/* The full-size temporal resolve contains premultiplied near + far radiance,
 * unpremultiplied for the pipeline's existing TRANSLUCENT blending mode. */
uniform sampler2D u_Clouds;
layout(location=0) in vec2 v_TexCoord;
layout(location=0) out vec4 color;
#include "include/scene_color.glsl"
void main() {
    vec4 clouds = texture(u_Clouds, v_TexCoord);
    vec3 light = max(clouds.rgb, vec3(0.));
    color = vec4(combatantOutputTransform(light), clouds.a);
}
