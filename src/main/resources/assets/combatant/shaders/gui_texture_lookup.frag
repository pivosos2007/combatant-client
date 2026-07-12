#version 150

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

vec3 mixRgb(vec3 from, vec3 to, float t) {
    return mix(from, to, clamp(t, 0.0, 1.0));
}

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a <= 0.001) {
        discard;
    }

    vec3 base = vertexColor.rgb;
    float luma = dot(tex.rgb, vec3(0.2126, 0.7152, 0.0722));
    float ink = smoothstep(0.00, 0.34, luma);
    float shine = smoothstep(0.48, 1.00, luma);

    vec3 shadow = mixRgb(base * 0.30, vec3(0.0), 0.18);
    vec3 mid = mixRgb(base * 0.72, base, 0.58);
    vec3 highlight = mixRgb(base, vec3(1.0), 0.34);
    vec3 mapped = mix(shadow, mid, ink);
    mapped = mix(mapped, highlight, shine);

    fragColor = vec4(mapped, tex.a * vertexColor.a);
}
