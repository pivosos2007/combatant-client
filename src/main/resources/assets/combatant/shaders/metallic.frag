#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

out vec4 color;

uniform sampler2D u_Src;
uniform sampler2D u_Mask;

layout (std140) uniform Metallic {
    vec4 u_Base;
    vec4 u_Highlight;
    vec4 u_Params0; // intensity, sharpness, edgeStrength, fillAlpha
    vec4 u_Params1; // time, sweepSpeed, sweepScale, padding
};

in vec2 v_TexCoord;

void main() {
    vec4 base = texture(u_Src, v_TexCoord);
    vec4 mask = texture(u_Mask, v_TexCoord);

    if (mask.a <= 0.0) {
        color = vec4(base.rgb, 1.0);
        return;
    }

    ivec2 sz = textureSize(u_Mask, 0);
    vec2 oneTexel = 1.0 / max(vec2(sz), vec2(1.0));

    float mL = texture(u_Mask, v_TexCoord + vec2(-oneTexel.x, 0.0)).a;
    float mR = texture(u_Mask, v_TexCoord + vec2(oneTexel.x, 0.0)).a;
    float mU = texture(u_Mask, v_TexCoord + vec2(0.0, -oneTexel.y)).a;
    float mD = texture(u_Mask, v_TexCoord + vec2(0.0, oneTexel.y)).a;
    float minN = min(min(mL, mR), min(mU, mD));

    float edge = clamp(mask.a - minN, 0.0, 1.0);
    edge = clamp(edge * 4.0, 0.0, 1.0);

    float intensity = max(u_Params0.x, 0.0);
    float sharpness = max(u_Params0.y, 0.1);
    float edgeStrength = max(u_Params0.z, 0.0);
    float fillAlpha = clamp(u_Params0.w, 0.0, 1.0);

    float sweep = sin((v_TexCoord.x + v_TexCoord.y) * u_Params1.z + u_Params1.x * u_Params1.y);
    sweep = clamp(sweep * 0.5 + 0.5, 0.0, 1.0);
    sweep = pow(sweep, sharpness);

    float rim = pow(edge, sharpness);
    float highlight = clamp(sweep + rim * edgeStrength, 0.0, 1.0);

    vec3 outCol = mix(base.rgb, u_Base.rgb, fillAlpha);
    outCol += u_Highlight.rgb * intensity * highlight;
    outCol = clamp(outCol, 0.0, 1.0);

    color = vec4(outCol, 1.0);
}
