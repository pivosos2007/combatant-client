#version 330 core

/*
 * Dedicated aiming decal for Predictions.
 * The marker is intentionally line-dominant: concentric rings, directional sectors and broken calibration arcs.
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

float rayMask(vec2 p, float angle, float minRadius, float maxRadius, float width, float aa) {
    vec2 dir = vec2(cos(angle), sin(angle));
    float along = dot(p, dir);
    float side = abs(p.x * dir.y - p.y * dir.x);
    float line = 1.0 - smoothstep(width - aa, width + aa, side);
    float alongMask = smoothstep(minRadius - aa, minRadius + aa, along)
            * (1.0 - smoothstep(maxRadius - aa, maxRadius + aa, along));
    return line * alongMask;
}

void main() {
    vec2 local = v_TexCoord * 2.0 - 1.0;
    float r = length(local);
    float angle = atan(local.y, local.x);

    float radius = max(v_Params.x, 0.001);
    float softness = max(v_Params.y, 0.0005);
    float lineWidth = max(v_Params.z, 0.001);
    float pulse = clamp(v_Params.w, 0.0, 1.0);

    float primaryAlpha = clamp(v_Params2.x, 0.0, 1.0);
    float secondaryAlpha = clamp(v_Params2.y, 0.0, 1.0);
    float fillAlpha = clamp(v_Params2.z, 0.0, 1.0);

    float aa = max(fwidth(r), softness);

    float outer = ringMask(r, radius * 0.94, lineWidth * 0.92, aa);
    float outer2Base = ringMask(r, radius * 0.79, lineWidth * 0.70, aa);
    float outer2Break = smoothstep(0.08, 0.66, 0.5 + 0.5 * cos(angle * 5.0 - 0.35));
    float outer2 = outer2Base * outer2Break;
    float center = ringMask(r, radius * (0.215 + pulse * 0.012), lineWidth * 0.92, aa);

    // Directional construction sectors. UV +X is aligned to the incoming trajectory on the CPU side.
    float ray0 = rayMask(local, 0.0, radius * 0.29, radius * 0.82, lineWidth * 0.66, aa);
    float ray1 = rayMask(local, 2.08, radius * 0.29, radius * 0.78, lineWidth * 0.58, aa);
    float ray2 = rayMask(local, -2.16, radius * 0.29, radius * 0.74, lineWidth * 0.58, aa);
    float sectors = max(ray0, max(ray1, ray2));

    // Calibrated micro-ticks concentrated on two arcs rather than around the whole circumference.
    float tickRing = ringMask(r, radius * 0.865, lineWidth * 0.48, aa);
    float tickPattern = smoothstep(0.68, 0.94, 0.5 + 0.5 * cos(angle * 28.0 + 0.3));
    float tickWindows = max(
            arcWindow(angle, 2.60, 0.76, 0.045),
            arcWindow(angle, -0.52, 0.50, 0.045)
    );
    float ticks = tickRing * tickPattern * tickWindows;

    // A few stronger broken arc accents, matching the reference's asymmetrical technical language.
    float accentBand = ringMask(r, radius * 0.91, lineWidth * 1.25, aa);
    float accents = accentBand * max(
            max(arcWindow(angle, 1.16, 0.17, 0.035), arcWindow(angle, 0.58, 0.11, 0.030)),
            max(arcWindow(angle, -2.72, 0.20, 0.035), arcWindow(angle, -1.72, 0.10, 0.030))
    );

    // Tiny contact core only; there is deliberately no opaque circular plate.
    float contact = 1.0 - smoothstep(radius * 0.09, radius * (0.28 + pulse * 0.018), r);

    float primary = max(outer, max(center * 0.90, accents));
    float secondary = max(outer2 * 0.76, max(sectors * 0.62, ticks * 0.82));
    float alpha = max(primary * primaryAlpha, secondary * secondaryAlpha);
    alpha = max(alpha, contact * fillAlpha * (0.82 + pulse * 0.18));

    float clipMask = 1.0 - smoothstep(radius + lineWidth * 3.0, radius + lineWidth * 5.0, r);
    alpha *= clipMask;

    if (alpha <= 0.001) discard;
    fragColor = vec4(v_Color.rgb, v_Color.a * alpha);
}
