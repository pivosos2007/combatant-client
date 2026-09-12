#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

#moj_import <combatant:shader_esp_palette.glsl>

out vec4 color;

uniform sampler2D u_Texture;

layout (std140) uniform ShaderEspGradient {
    vec4 u_Rect;           // xy = location, zw = size
    vec4 u_PrimaryColor;   // custom palette primary
    vec4 u_SecondaryColor; // custom palette secondary
    vec4 u_ColorParams;    // x = mode, y = animated base phase deg, z = spatial spread deg, w = gradient angle deg
    vec4 u_PassParams;     // x = dark multiplier, y = custom override flag, z = intensity, w = pass alpha
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
    int colorMode = int(floor(u_ColorParams.x + 0.5));
    float baseAngleDeg = u_ColorParams.y;
    float spatialSpreadDeg = u_ColorParams.z;
    float gradientAngleDeg = u_ColorParams.w;

    float darkMultiplier = clamp(u_PassParams.x, 0.0, 2.0);
    float overrideColor = step(0.5, u_PassParams.y);
    float intensity = clamp(u_PassParams.z, 0.0, 4.0);
    float passAlpha = clamp(u_PassParams.w, 0.0, 1.0);

    vec3 paletteColor = shaderEspResolveColor(
        coords,
        colorMode,
        baseAngleDeg,
        spatialSpreadDeg,
        gradientAngleDeg,
        u_PrimaryColor.rgb,
        u_SecondaryColor.rgb
    );

    vec3 baseColor = mix(mask.rgb, paletteColor, overrideColor) * intensity;
    color = vec4(createMaskGradient(coords, baseColor, darkMultiplier), mask.a * passAlpha);
}
