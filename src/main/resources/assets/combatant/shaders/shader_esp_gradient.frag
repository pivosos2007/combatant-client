#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

out vec4 color;

uniform sampler2D u_Texture;

layout (std140) uniform ShaderEspGradient {
    vec4 u_Rect;    // xy = location, zw = size
    vec4 u_Color1;  // rgb = override color, a = pass alpha
    vec4 u_Color2;  // x = dark multiplier
    vec4 u_Color3;  // x = override color flag, y = intensity
    vec4 u_Color4;
};

in vec2 v_TexCoord;

#define NOISE (0.5 / 255.0)

vec3 createMaskGradient(vec2 coords, vec3 baseColor, float darkMultiplier) {
    vec2 c = clamp(coords, 0.0, 1.0);
    vec3 top = baseColor;
    vec3 bottom = baseColor * clamp(darkMultiplier, 0.0, 2.0);
    vec3 base = mix(top, bottom, c.y);
    float dither = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    return base + mix(NOISE, -NOISE, dither);
}

void main() {
    vec4 mask = texture(u_Texture, v_TexCoord);
    if (mask.a <= 0.001) {
        discard;
    }

    vec2 coords = (gl_FragCoord.xy - u_Rect.xy) / max(u_Rect.zw, vec2(1.0));
    float passAlpha = clamp(u_Color1.a, 0.0, 1.0);
    float darkMultiplier = clamp(u_Color2.r, 0.0, 2.0);
    float overrideColor = step(0.5, u_Color3.x);
    float intensity = clamp(u_Color3.y, 0.0, 4.0);

    vec3 baseColor = mix(mask.rgb, u_Color1.rgb, overrideColor) * intensity;
    color = vec4(createMaskGradient(coords, baseColor, darkMultiplier), mask.a * passAlpha);
}
