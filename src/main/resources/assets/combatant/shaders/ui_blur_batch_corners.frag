#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

in vec4 v_Local;
in vec4 v_Color;
in vec4 v_Rect;
in vec4 v_Params;
in vec4 v_Params2;

out vec4 fragColor;

uniform sampler2D u_Texture;

layout (std140) uniform UIBatch {
    vec4 uScreen; // xy = framebuffer size, zw = logical size
    vec4 uLayer;
};

#moj_import <combatant:ui_geometry.glsl>
#ifdef COMBATANT_ANALYTIC_CLIP
#moj_import <combatant:ui_clip.glsl>
#endif

float pixelAa(vec2 logicalScale) {
    return max(max(logicalScale.x, logicalScale.y), 0.0001);
}

float analyticAa(float d, vec2 logicalScale) {
    return max(pixelAa(logicalScale), max(fwidth(d) * 0.75, 0.0001));
}

float crispCoverage(float d, float aa) {
    return clamp(0.5 - d / max(aa, 0.0001), 0.0, 1.0);
}

vec4 blur(vec2 uv, float brightness) {
    vec4 color = texture(u_Texture, uv);
    return vec4(color.rgb * brightness, 1.0);
}

vec2 warpedLocal(vec4 local) {
    float invW = abs(local.z) > 0.000001 ? local.z : 1.0;
    return local.xy / invW;
}

void main() {
    vec2 logicalScale = uScreen.zw / uScreen.xy;
    vec2 frag = warpedLocal(v_Local);

    vec2 size = v_Rect.zw;
    vec2 center = size * 0.5;
    vec2 boxHalf = center;
    vec2 pos = (frag - v_Rect.xy) - center;
    vec4 radii = normalizeRadii(v_Params, size);
    float distance = roundedCornersSdf(pos, boxHalf, radii);
    float aa = analyticAa(distance, logicalScale);
    float alpha = crispCoverage(distance, aa);
#ifdef COMBATANT_ANALYTIC_CLIP
    alpha *= combatantClipCoverage(
        combatantClipDistance(combatantLogicalFragCoord()), logicalScale
    );
#endif
    if (alpha <= 0.001) {
        discard;
    }

    vec2 uv = gl_FragCoord.xy / uScreen.xy;
    vec4 blurred = blur(uv, v_Params2.y);
    fragColor = vec4(blurred.rgb, alpha * v_Color.a);
}
