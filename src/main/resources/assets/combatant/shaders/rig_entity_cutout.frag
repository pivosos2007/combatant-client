#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

out vec4 color;

uniform sampler2D u_Texture;

in vec2 v_TexCoord;
in vec4 v_Color;
in vec3 v_Normal;

vec4 combatantApplyViewObstructionFade(vec4 shadeColor) {
    int a = int(floor(clamp(shadeColor.a, 0.0, 1.0) * 255.0 + 0.5));
    if (a >= 255) return shadeColor;
    int r = int(floor(clamp(shadeColor.r, 0.0, 1.0) * 255.0 + 0.5));
    int g = int(floor(clamp(shadeColor.g, 0.0, 1.0) * 255.0 + 0.5));
    int b = int(floor(clamp(shadeColor.b, 0.0, 1.0) * 255.0 + 0.5));
    ivec3 mark = ivec3(r & 1, g & 1, b & 1);
    bool ditherMark = all(equal(mark, ivec3(1, 0, 1)));
    bool a2cMark = all(equal(mark, ivec3(1, 1, 0)));
    if (!ditherMark && !a2cMark) return shadeColor;
    return vec4(float(r & 254) / 255.0,
                float(g & 254) / 255.0,
                float(b & 254) / 255.0,
                float(a) / 255.0);
}

void main() {
    vec4 texel = texture(u_Texture, v_TexCoord);
    // Cutout tests the texture itself. View-obstruction alpha must not turn the whole player into
    // discarded geometry when the fade drops below the cutout threshold.
    if (texel.a < 0.1) discard;
    color = texel * combatantApplyViewObstructionFade(v_Color);
}
