#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout(location = 0) out vec4 color;

uniform sampler2D u_Texture;      // MSDF glyph atlas
uniform sampler2D u_SceneTexture; // aptured scene
uniform sampler2D u_BlurTexture;  // prepared backdrop blur

layout (std140) uniform MsdfText {
    vec4 u_Msdf; // x = pxRange, y = atlasWidth, z = atlasHeight
};

layout (std140) uniform UIBatch {
    vec4 uScreen; // xy = framebuffer size, zw = logical size
    vec4 uLayer;
};

layout(location = 0) in vec2 v_TexCoord;
layout(location = 2) in vec4 v_Color;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

float luminance(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

vec2 safeNormalize(vec2 v) {
    float len2 = dot(v, v);
    return len2 > 1e-7 ? v * inversesqrt(len2) : vec2(0.0, 1.0);
}

void main() {
    vec3 atlas = texture(u_Texture, v_TexCoord).rgb;
    float sd = median(atlas.r, atlas.g, atlas.b);

    vec2 atlasSize = max(u_Msdf.yz, vec2(1.0));
    vec2 unitRange = vec2(max(u_Msdf.x, 0.5)) / atlasSize;
    vec2 screenTexSize = vec2(1.0) / max(fwidth(v_TexCoord), vec2(1e-6));
    float screenPxRange = max(0.5 * dot(unitRange, screenTexSize), 1.0);

    // Positive inside the glyph, measured in approximately screen pixels.
    float signedPx = screenPxRange * (sd - 0.5);
    float glyphAlpha = smoothstep(-0.62, 0.54, signedPx);
    if (glyphAlpha * v_Color.a <= 0.001) discard;

    // The SDF itself is the glass boundary. Derivatives give a stable screen-space edge normal,
    // so refraction/rim stay attached to the actual glyph instead of a surrounding rectangle.
    vec2 sdfGradient = vec2(dFdx(signedPx), dFdy(signedPx));
    vec2 normal = safeNormalize(sdfGradient);
    float edgeDistance = abs(signedPx);
    float rim = 1.0 - smoothstep(0.18, 2.20, edgeDistance);
    float hairline = 1.0 - smoothstep(0.00, 0.72, edgeDistance);
    float body = smoothstep(0.25, 3.25, signedPx);

    vec2 fbSize = max(uScreen.xy, vec2(1.0));
    vec2 uv = clamp(gl_FragCoord.xy / fbSize, vec2(0.001), vec2(0.999));

    // Keep the body optically calm; most displacement belongs to the rim, as with a thin glass
    // surface. This avoids fuzzy letter interiors while retaining visible refraction at the edge.
    float refractPx = 0.20 + rim * 1.35 + hairline * 0.35;
    vec2 refractedUv = clamp(uv + normal * (refractPx / fbSize), vec2(0.001), vec2(0.999));

    vec3 cleanScene = texture(u_SceneTexture, refractedUv).rgb;
    vec3 blurredScene = texture(u_BlurTexture, refractedUv).rgb;
    float clarity = 0.20 + body * 0.14;
    vec3 glass = mix(blurredScene, cleanScene, clarity);

    // Retain source chroma after blur and add only a restrained theme tint. v_Color is animated by
    // the caller, so the color flow moves through the material without changing glyph geometry.
    float glassLuma = max(luminance(glass), 1e-4);
    glass *= mix(1.0, 0.72 / glassLuma, smoothstep(0.72, 1.0, glassLuma) * 0.12);
    vec3 tint = clamp(v_Color.rgb, 0.0, 1.0);
    glass = mix(glass, tint, 0.13 + rim * 0.10);

    float directional = clamp(dot(normal, safeNormalize(vec2(-0.42, 0.91))) * 0.5 + 0.5, 0.0, 1.0);
    float rimLight = rim * (0.12 + 0.25 * directional);
    float specular = hairline * (0.10 + 0.20 * directional);
    glass += mix(vec3(1.0), tint, 0.28) * rimLight;
    glass += vec3(1.0) * specular;

    // Slight interior haze is sampled from the prepared blur, but alpha remains SDF-sharp.
    glass = mix(glass, blurredScene, (1.0 - body) * 0.06);
    // Real backdrop solarize. Thresholding is applied to the refracted scene under the glyph,
    // then mixed with the animated tint so the result changes with both backdrop and theme phase.
    vec3 backdrop = clamp(mix(blurredScene, cleanScene, 0.60 + body * 0.18), 0.0, 1.0);
    vec3 threshold = smoothstep(vec3(0.46), vec3(0.62), backdrop);
    vec3 solarizedBackdrop = mix(backdrop, vec3(1.0) - backdrop, threshold);
    vec3 exclusion = solarizedBackdrop + tint - 2.0 * solarizedBackdrop * tint;
    vec3 solarBlend = mix(solarizedBackdrop, exclusion, 0.24 + rim * 0.12);
    solarBlend += mix(vec3(1.0), tint, 0.28) * (hairline * 0.06 + rim * 0.04);
    glass = mix(clamp(glass, 0.0, 1.0), clamp(solarBlend, 0.0, 1.0),
                clamp(0.68 + body * 0.14 + rim * 0.09, 0.0, 0.92));

    float materialAlpha = glyphAlpha * v_Color.a * (0.87 + rim * 0.08 + hairline * 0.05);
    color = vec4(clamp(glass, 0.0, 1.0), clamp(materialAlpha, 0.0, 1.0));
}
