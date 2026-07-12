#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

out vec4 color;

uniform sampler2D u_Texture;

layout (std140) uniform Heat {
    vec4 u_Params1;
    vec4 u_Params2;
};

in vec2 v_TexCoord;

void main() {
    float intensity = clamp(u_Params1.x, 0.0, 2.0);
    float distortion = u_Params1.y;
    float scale = max(u_Params1.z, 0.01);
    float speed = u_Params1.w;

    float vignetteStrength = clamp(u_Params2.x, 0.0, 1.0);
    float vignetteRadius = clamp(u_Params2.y, 0.1, 0.95);
    float vignetteSoftness = clamp(u_Params2.z, 0.01, 1.0);
    float time = u_Params2.w;

    float wave1 = sin((v_TexCoord.y * scale * 13.0) + time * speed * 2.4);
    float wave2 = cos((v_TexCoord.x * scale * 11.0) - time * speed * 2.1);
    float warp = wave1 * wave2;
    float wave3 = sin((v_TexCoord.x + v_TexCoord.y) * scale * 7.0 + time * speed * 1.4);
    vec2 offset = vec2(warp, wave3) * 0.0045 * distortion;

    vec2 chroma = offset * (1.0 + intensity * 0.7);

    float r = texture(u_Texture, v_TexCoord + chroma).r;
    float g = texture(u_Texture, v_TexCoord + offset * 0.5).g;
    float b = texture(u_Texture, v_TexCoord - chroma).b;
    vec3 col = vec3(r, g, b);

    float pulse = 0.5 + 0.5 * sin(time * speed * 2.8);
    vec3 portalTint = mix(vec3(0.45, 0.06, 0.7), vec3(1.1, 0.4, 1.35), pulse);
    float tintMix = clamp(intensity * 1.45, 0.0, 1.0);
    col = mix(col, col * portalTint + vec3(0.04, 0.0, 0.08), tintMix);

    float dist = distance(v_TexCoord, vec2(0.5));
    float edge = smoothstep(vignetteRadius, vignetteRadius + vignetteSoftness, dist);
    float vignette = edge * vignetteStrength;

    col = mix(col, col * vec3(0.55, 0.3, 0.9), vignette);
    col *= (1.0 - vignette * 0.18);

    color = vec4(col, 1.0);
}
