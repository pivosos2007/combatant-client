#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

in vec2 v_TexCoord;
in vec4 v_Color;
in vec4 v_Params;
in vec4 v_Params2;

out vec4 fragColor;

const float PI = 3.14159265358979323846;
const float TAU = 6.28318530717958647692;

float ringMask(float r, float target, float halfWidth, float aa) {
    return 1.0 - smoothstep(halfWidth - aa, halfWidth + aa, abs(r - target));
}

float radialGate(float r, float lo, float hi, float aa) {
    return smoothstep(lo - aa, lo + aa, r) * (1.0 - smoothstep(hi - aa, hi + aa, r));
}

float angleDistance(float a, float b) {
    return abs(atan(sin(a - b), cos(a - b)));
}

float arcWindow(float angle, float center, float halfWidth, float aa) {
    return 1.0 - smoothstep(halfWidth - aa, halfWidth + aa, angleDistance(angle, center));
}

void main() {
    vec2 local = v_TexCoord * 2.0 - 1.0;
    float r = length(local);
    float angle = atan(local.y, local.x);

    float radius = max(v_Params.x, 0.001);
    float softness = max(v_Params.y, 0.0005);
    float lineWidth = max(v_Params.z, 0.001);
    float tickCount = max(v_Params.w, 6.0);

    float fillAlpha = clamp(v_Params2.x, 0.0, 1.0);
    float primaryAlpha = clamp(v_Params2.y, 0.0, 1.0);
    float detailAlpha = clamp(v_Params2.z, 0.0, 1.0);

    float aa = max(max(fwidth(r) * 0.9, fwidth(angle) * 0.0015), softness);
    float radialAa = max(fwidth(r), softness * 0.7);

    // Sparse technical impact mark: no opaque plate, just construction lines.
    float outerRing = ringMask(r, radius * 0.94, lineWidth, aa);
    float innerRingBase = ringMask(r, radius * 0.70, lineWidth * 0.72, aa);
    float innerBreaks = smoothstep(0.18, 0.72, 0.5 + 0.5 * cos(angle * 6.0 + 0.45));
    float innerRing = innerRingBase * innerBreaks;
    float centerRing = ringMask(r, radius * 0.22, lineWidth * 0.86, aa);

    // Six thin sector guides, clipped away from the center and outer rim.
    float sectorDistance = abs(sin(angle * 3.0)) * r;
    float sectorLines = (1.0 - smoothstep(lineWidth * 0.75 - aa, lineWidth * 0.75 + aa, sectorDistance))
            * radialGate(r, radius * 0.28, radius * 0.79, radialAa);

    // Small calibrated ticks just inside the outer ring.
    float tickBand = ringMask(r, radius * 0.835, lineWidth * 0.55, aa);
    float tickWave = 0.5 + 0.5 * cos(angle * tickCount);
    float ticks = tickBand * smoothstep(0.72, 0.94, tickWave);

    // Two deliberately asymmetric broken arcs keep the marker from reading as a generic HUD circle.
    float accentRing = ringMask(r, radius * 0.885, lineWidth * 1.20, aa);
    float accentWindows = max(
            arcWindow(angle, 0.78, 0.34, 0.035),
            arcWindow(angle, -2.18, 0.24, 0.035)
    );
    float accents = accentRing * accentWindows;

    // Only a tiny contact haze at the center; the surface remains readable underneath.
    float centerFill = 1.0 - smoothstep(radius * 0.12, radius * 0.43, r);

    float structural = max(max(outerRing, innerRing * 0.72), max(centerRing * 0.86, sectorLines * 0.58));
    float detail = max(ticks * 0.78, accents);
    float alpha = max(structural * primaryAlpha, detail * detailAlpha);
    alpha = max(alpha, centerFill * fillAlpha);

    float clipMask = 1.0 - smoothstep(radius + lineWidth * 3.0, radius + lineWidth * 5.0, r);
    alpha *= clipMask;

    if (alpha <= 0.001) discard;
    fragColor = vec4(v_Color.rgb, v_Color.a * alpha);
}
