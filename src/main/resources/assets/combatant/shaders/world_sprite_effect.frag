#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */


uniform sampler2D u_Texture;

#include <combatant:scene_color.glsl>

layout(location = 0) in vec2 v_TexCoord;
layout(location = 1) in vec4 v_Color;
layout(location = 2) in vec4 v_Params;
layout(location = 3) in vec3 v_ViewPosition;
layout(location = 4) in vec3 v_ViewNormal;
layout(location = 0) out vec4 fragColor;

float texelSignal(vec4 sampleColor) {
    // Alpha carries silhouettes for white-on-transparent glow textures.
    // Luminance also carries detail in full-color, opaque sprites.
    float luma = dot(sampleColor.rgb, vec3(0.2126, 0.7152, 0.0722));
    return sampleColor.a * (0.6 + 0.4 * luma);
}

vec2 textureRelief(vec2 uv, vec4 center) {
    // Two directional neighbouring samples, shared by Fresnel and Energy Sweep.
    // Offsets are in texels, so the material follows the original artwork rather
    // than the resolution of the billboard or the outline of a generic square.
    vec2 stepUv = 1.75 / max(vec2(textureSize(u_Texture, 0)), vec2(1.0));
    vec4 sampleX = texture(u_Texture, clamp(uv + vec2(stepUv.x, 0.0), vec2(0.0), vec2(1.0)));
    vec4 sampleY = texture(u_Texture, clamp(uv + vec2(0.0, stepUv.y), vec2(0.0), vec2(1.0)));
    float centerSignal = texelSignal(center);
    return vec2(texelSignal(sampleX) - centerSignal,
                texelSignal(sampleY) - centerSignal);
}

void main() {
    vec4 material = combatantMaterial(v_Color);
    int mode = int(abs(v_Params.w) + 0.5);
    float intensity = clamp(v_Params.z, 0.0, 2.0);

    vec4 sprite = texture(u_Texture, v_TexCoord);
    vec4 base = vec4(combatantMaterialColor(sprite.rgb) * material.rgb,
                     sprite.a * material.a);
    if (base.a <= 0.001) discard;

    vec3 rgb = base.rgb;
    float alpha = base.a;

    if (mode == 1 || mode == 2) {
        // A feature is visible only where the artwork actually changes (alpha
        // contour or colored detail). No radius-dependent glow is permitted.
        vec2 relief = textureRelief(v_TexCoord, sprite);
        float feature = smoothstep(0.012, 0.46, length(relief) * 4.0);

        if (mode == 1) {
            // Faux Fresnel from texture-defined slopes, modulated by the actual
            // plane angle for world-aligned sprites. Camera-facing sprites rely
            // on the texture slopes, not on the arbitrary billboard boundary.
            vec3 eye = normalize(-v_ViewPosition);
            float grazing = pow(1.0 - abs(dot(normalize(v_ViewNormal), eye)), 2.0);
            float rim = feature * (0.74 + 0.26 * grazing);
            rgb *= 1.0 + intensity * 0.68 * rim;
            alpha *= 1.0 + intensity * 0.25 * rim;
        } else {
            // The animated arc illuminates only source contours/detail. It does
            // not draw a new white arc across transparent/flat parts of a quad.
            vec2 p = v_TexCoord * 2.0 - 1.0;
            vec2 dir = p / max(length(p), 0.0001);
            float front = max(dot(dir, v_Params.xy), 0.0);
            float front2 = front * front;
            float front4 = front2 * front2;
            float head = front4 * front4 * front4; // angular cone, no trigonometry
            vec2 trailDir = vec2(v_Params.x * 0.9492 + v_Params.y * 0.3146,
                                 -v_Params.x * 0.3146 + v_Params.y * 0.9492);
            float trail = max(dot(dir, trailDir), 0.0);
            float trail2 = trail * trail;
            float beam = head + 0.22 * trail2 * trail2 * trail2 * trail2;
            float lighting = clamp(beam * feature * intensity, 0.0, 1.6);
            rgb *= 1.0 + 0.95 * lighting;
            alpha *= 1.0 + 0.34 * lighting;
        }
    } else if (mode == 3) {
        // Preserve the original RGB dispersion, which samples the artwork.
        vec2 p = v_TexCoord * 2.0 - 1.0;
        vec2 dir = p / max(length(p), 0.001);
        vec2 offset = dir * (0.009 * intensity * v_Params.x);
        vec3 dispersed = vec3(
                texture(u_Texture, clamp(v_TexCoord + offset, 0.0, 1.0)).r,
                sprite.g,
                texture(u_Texture, clamp(v_TexCoord - offset, 0.0, 1.0)).b
        );
        rgb = mix(rgb, combatantMaterialColor(dispersed) * material.rgb, clamp(intensity * 0.86, 0.0, 1.0));
        float radius = length(p);
        float highlight = smoothstep(0.56, 0.92, radius) * (1.0 - smoothstep(0.94, 1.10, radius));
        alpha *= 1.0 - intensity * 0.15 + highlight * intensity * 0.38;
    }

    fragColor = vec4(combatantMaterialOutput(rgb), clamp(alpha, 0.0, 1.0));
}
