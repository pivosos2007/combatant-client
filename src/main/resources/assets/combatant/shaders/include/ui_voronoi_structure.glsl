/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 *
 * The 3x3 nearest-site / 5x5 perpendicular-bisector approach was adapted
 * independently from the MIT-licensed Voronoi edge reference by Alin (2019):
 * https://github.com/tuxalin/procedural-tileable-shaders/blob/master/voronoi.glsl
 * See tools/materials/README.md for source and license provenance.
 */
#ifndef COMBATANT_UI_VORONOI_STRUCTURE
#define COMBATANT_UI_VORONOI_STRUCTURE

struct UiVoronoiCell {
    float border;   // nearest true bisector distance in CELL units
    float distance; // F1 in CELL units
    vec2 edgeNormal;
    float id;
};

vec2 uiVoronoiHash(vec2 cell, float seed) {
    vec2 p = cell + vec2(seed * 0.127, seed * 0.313);
    return fract(sin(vec2(dot(p, vec2(127.1, 311.7)),
                          dot(p, vec2(269.5, 183.3)))) * 43758.5453);
}

UiVoronoiCell uiVoronoi(vec2 grid, float jitter, float seed) {
    vec2 base = floor(grid);
    vec2 f = fract(grid);
    vec2 nearest = vec2(0.5);
    vec2 nearestCell = base;
    float nearestSq = 1e12;
    // Each lattice square contains one site, so its nearest site is inside
    // the current square or one of its eight immediate neighbours.
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 offset = vec2(float(x), float(y));
            vec2 site = offset + vec2(0.5) +
                    (uiVoronoiHash(base + offset, seed) - 0.5) * jitter;
            vec2 delta = site - f;
            float sq = dot(delta, delta);
            if (sq < nearestSq) {
                nearestSq = sq;
                nearest = site;
                nearestCell = base + offset;
            }
        }
    }
    float border = 1e12;
    vec2 edgeNormal = vec2(0.0, -1.0);
    // F2-F1 is NOT the Euclidean distance to the cell boundary. Compare
    // perpendicular distances to the separating bisectors instead.
    for (int y = -2; y <= 2; y++) {
        for (int x = -2; x <= 2; x++) {
            vec2 offset = vec2(float(x), float(y));
            vec2 site = offset + vec2(0.5) +
                    (uiVoronoiHash(base + offset, seed) - 0.5) * jitter;
            vec2 difference = site - nearest;
            float lengthSq = dot(difference, difference);
            if (lengthSq < 1e-5) continue;
            vec2 n = difference * inversesqrt(lengthSq);
            float edgeDistance = dot((site + nearest) * 0.5 - f, n);
            if (edgeDistance < border) {
                border = edgeDistance;
                edgeNormal = n;
            }
        }
    }
    UiVoronoiCell result;
    result.border = max(border, 0.0);
    result.distance = sqrt(max(nearestSq, 0.0));
    result.edgeNormal = edgeNormal;
    result.id = uiVoronoiHash(nearestCell, seed + 9.17).x;
    return result;
}

/** Returns straight tint RGB and alpha for a *separate* structure overlay. */
vec4 uiVoronoiStructure(vec2 physicalLocal, float sizePx, float jitter,
                        float seamWidthPx, float depth, float seed,
                        int profile, vec3 tint) {
    vec2 grid = physicalLocal / max(sizePx, 8.0);
    UiVoronoiCell cell = uiVoronoi(grid, jitter, seed);
    float distancePx = cell.border * sizePx;
    float aa = max(fwidth(distancePx), 0.75);
    float seam = 1.0 - smoothstep(max(seamWidthPx - aa, 0.0), seamWidthPx + aa, distancePx);
    float flank = 1.0 - smoothstep(seamWidthPx, seamWidthPx + 3.0 + depth * 3.0, distancePx);
    float directional = dot(cell.edgeNormal, normalize(vec2(-0.38, -0.92)));
    float light = 0.5 + 0.5 * directional;
    float facet = cell.id * 2.0 - 1.0;
    float opacity;
    vec3 color = tint;
    if (profile == 1) { // CRACKS, no filled cell colour
        opacity = seam * (0.66 + 0.24 * depth);
        color *= 0.50 + 0.30 * light;
    } else if (profile == 2) { // CRYSTALLINE
        opacity = seam * (0.43 + 0.28 * depth) +
                  (1.0 - flank) * (0.10 + 0.13 * depth) * abs(facet);
        color *= 0.78 + 0.18 * light + 0.09 * facet;
    } else if (profile == 3) { // RELIEF
        opacity = seam * (0.53 + 0.27 * depth) + flank * depth * 0.17;
        color *= 0.73 + 0.36 * light;
    } else { // CELLS
        opacity = seam * (0.54 + 0.19 * depth) + flank * 0.12 * depth;
        color *= 0.75 + 0.24 * light;
    }
    // Beyond Nyquist: suppress structure rather than resolve a moire pattern.
    float spatialFilter = 1.0 - smoothstep(0.35, 0.75,
                                   max(length(dFdx(grid)), length(dFdy(grid))));
    return vec4(clamp(color, 0.0, 1.0), clamp(opacity * spatialFilter, 0.0, 1.0));
}
#endif
