#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

out vec4 color;

uniform sampler2D u_Texture;

layout (std140) uniform DamageTint {
    vec4 u_LowHealth; // x strength, y desaturation, z contrast, w pulse
    vec4 u_Impact;    // x impact strength, y red pressure, z edge flash, w chromatic split
    vec4 u_Direction; // xy screen direction, z directional flag, w distortion
};

in vec2 v_TexCoord;

float safeEdge(vec2 centered) {
    return smoothstep(0.34, 0.73, length(centered));
}

void main() {
    vec2 uv = v_TexCoord;
    vec2 centered = uv - vec2(0.5);
    float centeredLen = length(centered);
    vec2 radialDir = centeredLen > 0.0001 ? centered / centeredLen : vec2(0.0, 1.0);

    float lowStrength = clamp(u_LowHealth.x, 0.0, 1.5);
    float impactStrength = clamp(u_Impact.x, 0.0, 1.5);
    float redPressure = clamp(u_Impact.y, 0.0, 1.5);
    float edgeFlash = clamp(u_Impact.z, 0.0, 1.5);
    float chromatic = clamp(u_Impact.w, 0.0, 1.5);
    float distortion = clamp(u_Direction.w, 0.0, 1.0);

    bool directional = u_Direction.z > 0.5 && dot(u_Direction.xy, u_Direction.xy) > 0.001;
    vec2 impactDir = directional ? normalize(u_Direction.xy) : radialDir;

    float edge = safeEdge(centered);
    float sideResponse = 1.0;
    if (directional && centeredLen > 0.0001) {
        float facing = dot(radialDir, impactDir);
        sideResponse = mix(0.20, 1.0, smoothstep(-0.55, 0.80, facing));
    }
    float impactEdge = edge * sideResponse;

    vec2 texel = 1.0 / vec2(textureSize(u_Texture, 0));
    vec2 sampleUv = uv;
    if (distortion > 0.0001 && impactStrength > 0.0001) {
        float warpPixels = 2.2 * distortion * impactStrength * impactEdge;
        sampleUv += impactDir * texel * warpPixels;
    }
    sampleUv = clamp(sampleUv, texel * 0.5, vec2(1.0) - texel * 0.5);

    vec3 col;
    if (chromatic > 0.0001 && impactStrength > 0.0001) {
        float splitPixels = 3.2 * chromatic * impactStrength;
        vec2 split = impactDir * texel * splitPixels;
        vec2 uvR = clamp(sampleUv + split, texel * 0.5, vec2(1.0) - texel * 0.5);
        vec2 uvB = clamp(sampleUv - split, texel * 0.5, vec2(1.0) - texel * 0.5);
        col = vec3(
            texture(u_Texture, uvR).r,
            texture(u_Texture, sampleUv).g,
            texture(u_Texture, uvB).b
        );
    } else {
        col = texture(u_Texture, sampleUv).rgb;
    }

    // Persistent low-health response: deliberately slow/subtle and independent from hit impulses.
    float desat = clamp(u_LowHealth.y, 0.0, 1.0);
    float contrast = clamp(u_LowHealth.z, 0.0, 1.0);
    float luma = dot(col, vec3(0.2126, 0.7152, 0.0722));
    col = mix(col, vec3(luma), desat);
    col = (col - 0.5) * (1.0 + contrast) + 0.5;

    float lowVignette = clamp(edge * lowStrength, 0.0, 1.0);
    vec3 lowTint = col * vec3(1.0, 0.72, 0.72);
    col = mix(col, lowTint, lowVignette * 0.48);
    col *= 1.0 - 0.12 * lowVignette;

    // Impact sequence: edge flash -> chromatic split -> red pressure -> nonlinear envelope decay.
    float flash = clamp(edgeFlash * impactEdge, 0.0, 1.0);
    col += vec3(0.30, 0.035, 0.015) * flash;

    float pressure = clamp(redPressure * mix(0.52, 1.0, impactEdge), 0.0, 1.0);
    vec3 impactTint = col * vec3(1.08, 0.40, 0.40);
    col = mix(col, impactTint, pressure * 0.82);
    col *= 1.0 - 0.10 * impactStrength * impactEdge;

    color = vec4(clamp(col, 0.0, 1.0), 1.0);
}
