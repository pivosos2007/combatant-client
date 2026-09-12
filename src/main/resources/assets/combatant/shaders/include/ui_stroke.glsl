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
 * An inner stroke uses the source silhouette as its outer edge. Both boundaries are
 * filtered from the same SDF, so the stroke terminates on the exact same contour as
 * the corresponding fill. Softness is applied symmetrically to the two filtered edges;
 * this prevents a soft rounded fill from being capped by a visibly sharper border.
 */
float innerStrokeCoverage(float d, float thickness, vec2 logicalScale, float softness) {
    float t = max(thickness, 0.0);
    if (t <= 0.000001) return 0.0;

    float outer = coverage(d, strokeAnalyticAa(d, logicalScale, softness));
    float innerD = d + t;
    float inner = coverage(innerD, strokeAnalyticAa(innerD, logicalScale, softness));
    return clamp(outer * (1.0 - inner), 0.0, 1.0);
}

/* Centered strokes retain symmetric edge semantics with the same orientation-stable AA width. */
float centeredStrokeCoverage(float d, float thickness, vec2 logicalScale, float softness) {
    float t = max(thickness, 0.0);
    if (t <= 0.000001) return 0.0;
    float bandD = abs(d) - t * 0.5;
    return coverage(bandD, strokeAnalyticAa(bandD, logicalScale, softness));
}
