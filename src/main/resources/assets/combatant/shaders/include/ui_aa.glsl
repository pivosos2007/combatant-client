/* Shared analytic UI antialiasing helpers. Distances and softness are in logical pixels. */
float pixelAa(vec2 logicalScale) {
    return max(max(logicalScale.x, logicalScale.y), 0.0001);
}

float analyticAa(float d, vec2 logicalScale, float softness) {
    return max(pixelAa(logicalScale), max(fwidth(d) * 0.75, 0.0001)) + max(0.0, softness);
}

float coverage(float d, float aa) {
    return clamp(0.5 - d / max(aa, 0.0001), 0.0, 1.0);
}
