#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

#include <combatant:shader_esp_palette.glsl>

layout(location = 0) out vec4 color;

uniform sampler2D u_Texture;

layout (std140) uniform ShaderEspGradient {
    vec4 u_Rect;           // xy = location, zw = size
    vec4 u_PrimaryColor;   // custom palette primary
    vec4 u_SecondaryColor; // custom palette secondary
    vec4 u_ColorParams;    // x = mode, y = animated base phase deg, z = spatial spread deg, w = gradient angle deg
    vec4 u_PassParams;     // x = dark multiplier, y = custom override flag, z = intensity, w = pass alpha
};

layout(location = 0) in vec2 v_TexCoord;

vec3 createMaskGradient(vec2 coords, vec3 baseColor, float darkMultiplier) {
    // Keep the inexpensive vertical shade, but drop the per-fragment sin() dither.
    float shade = mix(1.0, clamp(darkMultiplier, 0.0, 2.0), clamp(coords.y, 0.0, 1.0));
    return baseColor * shade;
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

    vec3 baseColor = mask.rgb;
    // Relations/entity-color mode is the common path. Avoid HSV conversion, trig and gradient
    // resolution entirely unless the user explicitly selected a custom animated palette.
    if (overrideColor > 0.5) {
        baseColor = shaderEspResolveColor(
            coords,
            colorMode,
            baseAngleDeg,
            spatialSpreadDeg,
            gradientAngleDeg,
            u_PrimaryColor.rgb,
            u_SecondaryColor.rgb
        );
    }
    baseColor *= intensity;
    color = vec4(createMaskGradient(coords, baseColor, darkMultiplier), mask.a * passAlpha);
}
