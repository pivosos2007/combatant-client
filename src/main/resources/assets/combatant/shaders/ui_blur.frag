#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

in vec2 v_TexCoord;
in vec4 v_Color;

out vec4 fragColor;

layout (std140) uniform UIBlur {
    vec4 uInputResolution; // xy = source size hint
    vec4 uSize;            // xy = target size hint
    vec4 uLocation;        // unused for full-screen Dual Kawase
    vec4 uParams;          // x = offset px, y = pass mode (0 down, 1 up), z = brightness
    vec4 uColor1;          // unused
};

uniform sampler2D u_Texture;

vec4 dualKawase(vec2 uv) {
    vec2 texel = 1.0 / vec2(textureSize(u_Texture, 0));
    float offsetPx = max(uParams.x, 0.0) + 0.5;
    float upPass = step(0.5, uParams.y);
    float spread = mix(1.0, 1.5, upPass);
    vec2 d = texel * offsetPx * spread;

    vec4 color = texture(u_Texture, uv + vec2( d.x,  d.y));
    color += texture(u_Texture, uv + vec2(-d.x,  d.y));
    color += texture(u_Texture, uv + vec2( d.x, -d.y));
    color += texture(u_Texture, uv + vec2(-d.x, -d.y));
    color *= 0.25;

    // Main/gui framebuffers do not provide stable alpha for postprocess sources.
    // The blur render target is an intermediate color buffer and must be written
    // as opaque; preserving source alpha makes TRANSLUCENT blending keep the
    // cleared black target when the captured scene alpha is zero.
    return vec4(color.rgb * uParams.z * v_Color.rgb, 1.0);
}

void main() {
    fragColor = dualKawase(v_TexCoord);
}
