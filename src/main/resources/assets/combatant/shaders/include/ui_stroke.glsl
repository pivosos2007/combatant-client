/* Shared UI stroke coverage. Distances, thickness and softness are in logical pixels. */

/*
 * Stroke AA uses the Euclidean screen-space gradient of the SDF instead of fwidth(d)'s
 * Manhattan sum. That keeps the coverage width orientation-independent: long horizontal /
 * vertical edges, 45-degree portions and rounded corners all resolve to the same pixel footprint.
 * pixelAa() remains the minimum footprint for stable low/fractional GUI scales.
 */
float strokeAnalyticAa(float d, vec2 logicalScale, float softness) {
    vec2 derivative = vec2(dFdx(d), dFdy(d));
    float footprint = max(pixelAa(logicalScale), max(length(derivative), 0.0001));
    return footprint + max(0.0, softness);
}

/*
 * Inner-stroke outer coverage is one-sided, but it must not double the normal AA support.
 * A centered analytic edge reaches zero at +0.5 * aa. Keep that same outer footprint while
 * making the source silhouette itself fully covered, so a stroke drawn over a filled card does
 * not leave a weak final-pixel rim and also does not grow a visible one-pixel halo outside it.
 *
 * The renderer still supplies a small raster guard band; it only gives the shader room for this
 * half-footprint fringe and never changes the source SDF bounds or logical stroke placement.
 */
float innerStrokeOuterCoverage(float d, vec2 logicalScale) {
    float aa = strokeAnalyticAa(d, logicalScale, 0.0);
    float outerFringe = max(aa * 0.5, 0.0001);
    return clamp(1.0 - max(d, 0.0) / outerFringe, 0.0, 1.0);
}

/*
 * Inner strokes keep their geometric band inside the source shape. Softness belongs only to the
 * inner boundary. The outer boundary therefore stays tied to the exact card silhouette while the
 * inner edge may still be feathered as requested by the API.
 */
float innerStrokeCoverage(float d, float thickness, vec2 logicalScale, float softness) {
    float t = max(thickness, 0.0);
    if (t <= 0.000001) return 0.0;

    float outer = innerStrokeOuterCoverage(d, logicalScale);
    float innerD = d + t;
    float inner = coverage(innerD, strokeAnalyticAa(innerD, logicalScale, softness));
    return clamp(outer * (1.0 - inner), 0.0, 1.0);
}

/* Centered strokes retain symmetric edge semantics, with the same orientation-stable AA width. */
float centeredStrokeCoverage(float d, float thickness, vec2 logicalScale, float softness) {
    float t = max(thickness, 0.0);
    if (t <= 0.000001) return 0.0;
    float bandD = abs(d) - t * 0.5;
    return coverage(bandD, strokeAnalyticAa(bandD, logicalScale, softness));
}
