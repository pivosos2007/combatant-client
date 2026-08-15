#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

in vec2 v_TexCoord;
out vec4 color;

uniform sampler2D u_History;
uniform sampler2D u_Mask;

layout (std140) uniform HandGhosting {
    vec4 u_Screen;
    vec4 u_Color;
    vec4 u_Params;
};

float maskValue(sampler2D tex, vec2 uv) {
    vec4 v = texture(tex, uv);
    return max(v.a, max(v.r, max(v.g, v.b)));
}

void main() {
    float previous = texture(u_History, v_TexCoord).r;
    float current = maskValue(u_Mask, v_TexCoord);
    float decay = clamp(u_Params.x, 0.0, 1.0);

    // R carries accumulated history. G carries this frame's silhouette so the composite pass can
    // reject the current hand from every halo sample without sampling the full-resolution mask.
    float history = max(current, previous * decay);
    color = vec4(history, current, 0.0, history);
}
