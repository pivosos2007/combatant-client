#version 430 core
/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout(binding = 0) uniform sampler2D u_BlockAtlas;
layout(location = 0) in vec2 v_UV;
layout(location = 1) in float v_VFraction;
layout(location = 2) flat in vec4 v_Atlas0;
layout(location = 3) flat in vec4 v_Atlas1;
layout(location = 4) flat in uvec4 v_Style;
layout(location = 0) out vec4 fragColor;

vec4 unpackArgb(uint argb) {
    return vec4(float((argb >> 16u) & 255u), float((argb >> 8u) & 255u),
                float(argb & 255u), float((argb >> 24u) & 255u)) / 255.0;
}

vec4 tileSprite(vec4 rect, vec2 blockUv) {
    vec2 span = rect.zw - rect.xy;
    // Derivatives BEFORE fract prevent a tile seam from requesting the wrong mip level.
    vec2 du = dFdx(blockUv) * span;
    vec2 dv = dFdy(blockUv) * span;
    return textureGrad(u_BlockAtlas, rect.xy + fract(blockUv) * span, du, dv);
}

void main() {
    uint flags = v_Style.w;
    vec4 color = unpackArgb(v_Style.x);
    if ((flags & 1u) != 0u) {
        // Real baked model quads already provide absolute atlas UVs.
        color = texture(u_BlockAtlas, v_UV) * unpackArgb(v_Style.y);
    } else {
        if ((flags & 2u) != 0u) {
            color = tileSprite(v_Atlas0, v_UV) * unpackArgb(v_Style.y);
        }
        if ((flags & 4u) != 0u) {
            vec4 overlay = tileSprite(v_Atlas1, v_UV) * unpackArgb(v_Style.z);
            color.rgb = mix(color.rgb, overlay.rgb, overlay.a);
            color.a = overlay.a + color.a * (1.0 - overlay.a);
        }
        if ((flags & 16u) != 0u) {
            color.rgb *= mix(1.05, 0.86, clamp(v_VFraction, 0.0, 1.0));
        }
    }
    const float bayer[16] = float[16](
        0., 8., 2., 10., 12., 4., 14., 6.,
        3., 11., 1., 9., 15., 7., 13., 5.);
    int index = (int(gl_FragCoord.y) & 3) * 4 + (int(gl_FragCoord.x) & 3);
    if (color.a < (bayer[index] + 0.5) / 16.0) discard;
    fragColor = vec4(color.rgb, 1.0);
}
