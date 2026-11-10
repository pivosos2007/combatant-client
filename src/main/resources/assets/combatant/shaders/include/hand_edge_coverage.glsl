/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

// The hand target contains the hand's *color*, not a white silhouette. Its RGB
// channels (including item glint) are not coverage. Using max(RGBA) promotes
// bright translucent fragments into solid edges.
float handMaskCoverage(vec2 uv) {
    return clamp(texture(u_Mask, uv).a, 0.0, 1.0);
}

// A coverage difference, not 1 - min(neighbors): the latter turns an almost
// transparent edge fragment into a full-strength metallic specular highlight.
float handMaskInnerContour(vec2 uv, vec2 texel, float center) {
    float left  = handMaskCoverage(uv - vec2(texel.x, 0.0));
    float right = handMaskCoverage(uv + vec2(texel.x, 0.0));
    float up    = handMaskCoverage(uv - vec2(0.0, texel.y));
    float down  = handMaskCoverage(uv + vec2(0.0, texel.y));
    float eroded = min(min(left, right), min(up, down));
    float contour = clamp(center - eroded, 0.0, 1.0);
    // One-off glint pixels do not have any connected silhouette around them.
    float support = max(max(left, right), max(up, down));
    return pow(contour, 0.78)
            * smoothstep(0.035, 0.75, center)
            * smoothstep(0.025, 0.40, support);
}

const vec2 HAND_EDGE_START_8[4] = vec2[](
    vec2(0.958072899, 0.286524553),
    vec2(0.835807361, 0.549022818),
    vec2(0.643455865, 0.765483213),
    vec2(0.397147891, 0.917754626)
);
const vec2 HAND_EDGE_START_12[4] = vec2[](
    vec2(0.981292664, 0.192521967),
    vec2(0.925870585, 0.377840787),
    vec2(0.835807361, 0.549022818),
    vec2(0.714472680, 0.699663341)
);
const vec2 HAND_EDGE_ROTATE_8 = vec2(0.707106781, 0.707106781);
const vec2 HAND_EDGE_ROTATE_12 = vec2(0.866025404, 0.5);

// Integrate coverage instead of picking the first sample above a tiny threshold.
// A binary first-hit distance produces concentric steps and bright isolated dots,
// especially with the stronger Metallic glow and specular rim.
float handMaskOuterHalo(vec2 uv, vec2 texel, float edgeWidth, int quality) {
    if (edgeWidth <= 0.001) return 0.0;
    int rings = clamp(quality, 2, 4);
    bool eight = quality <= 2;
    int dirs = eight ? 8 : 12;
    vec2 rotation = eight ? HAND_EDGE_ROTATE_8 : HAND_EDGE_ROTATE_12;
    float weightedCoverage = 0.0;
    float kernelWeight = 0.0;

    for (int r = 1; r <= 4; ++r) {
        if (r > rings) break;
        float radial = (float(r) - 0.5) / float(rings);
        float weight = (1.0 - radial) * (1.0 - radial);
        vec2 dir = eight ? HAND_EDGE_START_8[r - 1] : HAND_EDGE_START_12[r - 1];
        for (int i = 0; i < 12; ++i) {
            if (i >= dirs) break;
            weightedCoverage += handMaskCoverage(uv + dir * texel * (edgeWidth * radial)) * weight;
            kernelWeight += weight;
            dir = vec2(dir.x * rotation.x - dir.y * rotation.y,
                       dir.x * rotation.y + dir.y * rotation.x);
        }
    }
    // Smooth the sampled coverage, not the inferred binary distance. This keeps
    // a real broad halo while attenuating solitary bright mask fragments.
    return smoothstep(0.02, 0.52, weightedCoverage / max(kernelWeight, 1.0e-5));
}
