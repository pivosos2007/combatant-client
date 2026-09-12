/* Shared analytic UI antialiasing helpers. Distances and softness are in logical pixels. */
float pixelAa(vec2 logicalScale) {
    return max(max(logicalScale.x, logicalScale.y), 0.0001);
}

/*
 * Use the Euclidean screen-space SDF gradient instead of fwidth(d)'s Manhattan norm.
 * The latter widens diagonal/rounded edges relative to horizontal and vertical ones,
 * which makes otherwise identical silhouettes look subtly different at corners.
 * Keep one framebuffer-pixel as the minimum footprint for stable fractional GUI scales.
 */
float analyticAa(float d, vec2 logicalScale, float softness) {
    vec2 derivative = vec2(dFdx(d), dFdy(d));
    float footprint = max(pixelAa(logicalScale), max(length(derivative), 0.0001));
    return footprint + max(0.0, softness);
}

float analyticAa(float d, vec2 logicalScale) {
    return analyticAa(d, logicalScale, 0.0);
}

float coverage(float d, float aa) {
    return clamp(0.5 - d / max(aa, 0.0001), 0.0, 1.0);
}
