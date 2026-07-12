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

out vec4 fragColor;

uniform sampler2D u_Texture;

layout (std140) uniform UIBatch {
    vec4 uScreen; // xy = framebuffer size, zw = logical size
};

float roundedBoxSDF(vec2 center, vec2 size, float radius) {
    vec2 q = abs(center) - size + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

float chamferedBoxSDF(vec2 p, vec2 halfSize, float chamfer) {
    vec2 q = abs(p);
    float box = max(q.x - halfSize.x, q.y - halfSize.y);
    float cut = (q.x + q.y - halfSize.x - halfSize.y + chamfer) * 0.70710678;
    return max(box, cut);
}

float pixelAa(vec2 logicalScale) {
    return max(max(logicalScale.x, logicalScale.y), 0.0001);
}

float analyticAa(float d, vec2 logicalScale) {
    return max(pixelAa(logicalScale), max(fwidth(d) * 0.75, 0.0001));
}

float crispCoverage(float d, float aa) {
    return clamp(0.5 - d / max(aa, 0.0001), 0.0, 1.0);
}

vec4 blur(vec2 uv) {
    vec4 color = texture(u_Texture, uv);
    return vec4(color.rgb * v_Params.z, 1.0);
}

vec2 warpedLocal(vec4 local) {
    float invW = abs(local.z) > 0.000001 ? local.z : 1.0;
    return local.xy / invW;
}

void main() {
    vec2 logicalScale = uScreen.zw / uScreen.xy;
    vec2 frag = warpedLocal(v_Local);
    vec2 halfSize = v_Rect.zw * 0.5;
    float shape = v_Params.x;
    float shapeSize = clamp(abs(shape), 0.0, min(halfSize.x, halfSize.y));
    float d = shape < 0.0
            ? chamferedBoxSDF(frag - (v_Rect.xy + halfSize), halfSize, shapeSize)
            : roundedBoxSDF(frag - v_Rect.xy - halfSize, halfSize, shapeSize);
    float aa = analyticAa(d, logicalScale);
    float smoothedAlpha = crispCoverage(d, aa);
    if (smoothedAlpha <= 0.001) {
        discard;
    }

    vec2 invScreen = 1.0 / uScreen.xy;
    vec2 uv = gl_FragCoord.xy * invScreen;
    vec4 blurred = blur(uv);
    fragColor = vec4(blurred.rgb, smoothedAlpha * v_Params.w);
}
