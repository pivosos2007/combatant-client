#version 330 core

#moj_import <combatant:ui_aa.glsl>
#moj_import <combatant:ui_stroke.glsl>

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

layout (std140) uniform UIBatch {
    vec4 uScreen; // xy = framebuffer size, zw = logical size
    vec4 uLayer;
};

float circleSDF(vec2 p, float r) {
    return length(p) - r;
}

float normalizeAngle(float angleDeg) {
    float outAngle = mod(angleDeg, 360.0);
    return outAngle < 0.0 ? outAngle + 360.0 : outAngle;
}

vec2 arcPoint(vec2 center, float radius, float angleDeg) {
    float rad = radians(angleDeg);
    return center + vec2(sin(rad), -cos(rad)) * radius;
}


vec2 warpedLocal(vec4 local) {
    float invW = abs(local.z) > 0.000001 ? local.z : 1.0;
    return local.xy / invW;
}

void main() {
    vec2 logicalScale = uScreen.zw / uScreen.xy;
    vec2 frag = warpedLocal(v_Local);

    vec2 halfSize = v_Rect.zw * 0.5;
    vec2 center = v_Rect.xy + halfSize;

    float radius = v_Params.x;
    float softness = v_Params.y;
    float thickness = v_Params.z;
    float startDeg = normalizeAngle(v_Params.w);
    float endDeg = normalizeAngle(v_Params2.x);
    float capsEnabled = v_Params2.y;

    vec2 local = frag - center;
    float dist = length(local);
    float radialAlpha = centeredStrokeCoverage(dist - radius, thickness, logicalScale, softness);

    float sweep = endDeg - startDeg;
    if (sweep <= 0.0) {
        sweep += 360.0;
    }

    float angularAlpha = 1.0;
    if (sweep < 359.99) {
        float endUnwrapped = startDeg + sweep;
        float angleDeg = normalizeAngle(degrees(atan(local.x, -local.y)));
        if (angleDeg < startDeg) {
            angleDeg += 360.0;
        }

        float angularSoftDeg = max(fwidth(angleDeg), degrees(softness / max(radius, 0.0001)));
        float startMask;
        float endMask;
        if (capsEnabled > 0.5) {
            // Keep the arc body inside the logical sweep; round dots are added separately below.
            startMask = smoothstep(startDeg - angularSoftDeg, startDeg, angleDeg);
            endMask = 1.0 - smoothstep(endUnwrapped, endUnwrapped + angularSoftDeg, angleDeg);
        } else {
            // Flat arcs must not bleed past the logical start/end angles.
            startMask = smoothstep(startDeg - angularSoftDeg, startDeg, angleDeg);
            endMask = 1.0 - smoothstep(endUnwrapped, endUnwrapped + angularSoftDeg, angleDeg);
        }
        angularAlpha = startMask * endMask;

        float alpha = angularAlpha * radialAlpha;
        if (capsEnabled > 0.5) {
            float capRadius = thickness * 0.5;
            float capAa = max(max(softness, fwidth(capRadius)), 0.0001);
            float startCapD = circleSDF(frag - arcPoint(center, radius, startDeg), capRadius);
            float endCapD = circleSDF(frag - arcPoint(center, radius, endDeg), capRadius);
            float startCapAlpha = 1.0 - smoothstep(0.0, capAa, startCapD);
            float endCapAlpha = 1.0 - smoothstep(0.0, capAa, endCapD);
            alpha = max(alpha, max(startCapAlpha, endCapAlpha));
        }
        fragColor = vec4(v_Color.rgb, v_Color.a * alpha);
        return;
    }

    fragColor = vec4(v_Color.rgb, v_Color.a * radialAlpha);
}
