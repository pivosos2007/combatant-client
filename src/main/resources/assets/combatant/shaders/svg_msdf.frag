#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

out vec4 color;

uniform sampler2D u_Texture;

layout (std140) uniform MsdfText {
    vec4 u_Msdf; // x = pxRange, y = atlasWidth, z = atlasHeight
};

in vec2 v_TexCoord;
in vec4 v_Color;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

void main() {
    vec3 sample = texture(u_Texture, v_TexCoord).rgb;
    float sd = median(sample.r, sample.g, sample.b);

    vec2 unitRange = vec2(u_Msdf.x) / u_Msdf.yz;
    vec2 screenTexSize = vec2(1.0) / fwidth(v_TexCoord);
    float screenPxRange = max(0.5 * dot(unitRange, screenTexSize), 1.0);

    float alpha = clamp(screenPxRange * (sd - 0.5) + 0.5, 0.0, 1.0);
    color = vec4(v_Color.rgb, v_Color.a * alpha);
}
