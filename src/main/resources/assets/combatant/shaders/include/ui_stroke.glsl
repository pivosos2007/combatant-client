/* Shared UI stroke coverage. Distances, thickness and softness are in logical pixels. */

/*
 * Stroke AA deliberately shares the exact same footprint function as shape fills.
 * This keeps the 50% contour of a stroke on the source SDF boundary instead of
 * producing a harder, slightly expanded outer rim at rounded corners and flat edges.
 */
float strokeAnalyticAa(float d, vec2 logicalScale, float softness) {
    return analyticAa(d, logicalScale, softness);
}

/*
 * Box-filter a signed-distance interval [lower, upper].  Subtracting the two
 * half-plane coverages preserves the authored optical weight even when the
 * interval is narrower than one framebuffer pixel.  In particular a 0.5 px
 * hairline resolves to 0.5 coverage instead of alternating between a nearly
 * opaque row and a missed row as its contour moves through subpixel phases.
 */
float strokeIntervalCoverage(float d, float lower, float upper, float aa) {
    float upperHalfPlane = coverage(d - upper, aa);
    float lowerHalfPlane = coverage(d - lower, aa);
    return clamp(upperHalfPlane - lowerHalfPlane, 0.0, 1.0);
}

/*
 * An inner stroke uses the source silhouette as its outer edge. Both boundaries are
 * filtered from the same SDF, so the stroke terminates on the exact same contour as
 * the corresponding fill. Softness is applied symmetrically to the two filtered edges;
 * this prevents a soft rounded fill from being capped by a visibly sharper border.
 */
float innerStrokeCoverage(float d, float thickness, vec2 logicalScale, float softness) {
    float t = max(thickness, 0.0);
    if (t <= 0.000001) return 0.0;

    float aa = strokeAnalyticAa(d, logicalScale, softness);
    return strokeIntervalCoverage(d, -t, 0.0, aa);
}

/* Centered strokes retain symmetric edge semantics with the same orientation-stable AA width. */
float centeredStrokeCoverage(float d, float thickness, vec2 logicalScale, float softness) {
    float t = max(thickness, 0.0);
    if (t <= 0.000001) return 0.0;
    float aa = strokeAnalyticAa(d, logicalScale, softness);
    return strokeIntervalCoverage(d, -t * 0.5, t * 0.5, aa);
}
