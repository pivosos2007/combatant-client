/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 *
 * Camera-local wet-surface model, independently implemented for Combatant.
 * Design references (conceptual, no third-party shader code copied):
 * - frmlinn/raindrops-v2 (MIT): static attachment, moving water and refraction.
 * - jeantimex/raindrop (Heartfelt-derived, CC BY-NC-SA; NO CODE COPIED):
 *   separate adhered beads, intermittent runners and fading wet tracks.
 *
 * No simulation texture or per-surface SSBO. The persistent lattice seeds stay
 * fixed; all lifetime, travel and drying envelopes are continuous at wrap.
 */

vec2 combatantClimateHash(vec2 p, float seed) {
    // Stateless PCG-style integer mix: no transcendental sin() per cell.
    uvec2 q = uvec2(ivec2(floor(p)));
    uint h = (q.x * 1664525u + q.y * 1013904223u) ^ floatBitsToUint(seed);
    h ^= h >> 16u;
    h *= 2246822519u;
    h ^= h >> 13u;
    h *= 3266489917u;
    h ^= h >> 16u;
    uint k = h ^ (h >> 15u) ^ 0x9e3779b9u;
    k *= 2246822519u;
    k ^= k >> 13u;
    return vec2(float(h & 0x00ffffffu), float(k & 0x00ffffffu)) / 16777216.0;
}

// Scaled for GUI pixels. A bounded sphere-cap derivative changes the scene
// sampling direction inside a bead instead of painting an opaque white circle.
// All returned channels are PRE-multiplied by temporal presence, not by rain;
// the caller applies the shared smoothed wetness envelope only once.
float combatantClimateDroplets(vec2 pixel, float seconds, float amount,
                               float cellPx, float velocity, float seed,
                               out vec2 lensNormal, out float trail, out float shine) {
    lensNormal = vec2(0.0);
    trail = 0.0;
    shine = 0.0;
    if (amount <= 0.002) return 0.0;

    float waterLevel = clamp(amount, 0.0, 1.0);
    float coverage = 0.0;
    vec2 initial = pixel;
    // Aged adhered beads, spatially fixed until their fade-out completes.
    // Separate cell spacing from bead radii to avoid a continuous water sheet.
    float beadCell = max(cellPx * 0.56, 12.0);
    vec2 beadTile = floor(pixel / beadCell);
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            vec2 id = beadTile + vec2(float(x), float(y));
            vec2 h = combatantClimateHash(id, seed + 4.13);
            vec2 h2 = combatantClimateHash(id + 19.61, seed + 8.29);
            // Hard ceiling of ~35% occupied cells even in thunderstorms.
            // A smooth occupancy ramp means changing weather cannot pop seeds.
            float density = mix(0.15, 0.32, sqrt(waterLevel));
            float presence = 1.0 - smoothstep(density - 0.10, density + 0.12, h.x);
            if (presence <= 0.001) continue;

            // One deterministic life per bead. Its center DOES NOT move,
            // so no discontinuous reset appears when the clock wraps.
            float period = mix(13.0, 23.0, h2.x);
            float age = fract(seconds / period + h2.y);
            float alive = smoothstep(0.015, 0.19, age)
                        * (1.0 - smoothstep(0.76, 0.985, age));
            float opacity = presence * alive;
            if (opacity <= 0.001) continue;

            vec2 center = (id + vec2(0.19) + h2 * 0.62) * beadCell;
            vec2 displacement = initial - center;
            float radius = beadCell * mix(0.165, 0.405, h.y * h.y);
            vec2 shape = displacement / vec2(radius, radius * mix(0.98, 1.29, h2.x));
            // Asymmetric lower bulge: adhered drops are not identical disks.
            shape.x += 0.075 * shape.y * shape.y * (h.x - 0.5);
            float dist = length(shape);
            float aa = max(0.55 / max(radius, 1.0), 0.045);
            float body = 1.0 - smoothstep(0.83 - aa, 1.04 + aa, dist);
            float crest = smoothstep(0.35, 0.72, dist)
                        * (1.0 - smoothstep(0.93, 1.08 + aa, dist));
            float bead = body * opacity;
            coverage = max(coverage, bead);

            float slope = crest * opacity;
            // Gradient of the lens, not a uniformly white contour.
            lensNormal += shape * slope * vec2(0.84, 0.66);
            // Asymmetric specular is a small glint, not an all-round ring.
            float specularSide = smoothstep(-0.2, 0.78, -shape.x * 0.69 - shape.y * 0.72);
            shine = max(shine, crest * specularSide * opacity * 0.62);
        }
    }

    // Sparse runners: independent, slower release followed by a brief slide.
    // Most beads never move in a given cycle; the runner count is limited.
    float runCell = max(cellPx * 1.22, 28.0);
    vec2 runTile = floor(pixel / runCell);
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            vec2 id = runTile + vec2(float(x), float(y));
            vec2 h = combatantClimateHash(id, seed + 15.07);
            vec2 h2 = combatantClimateHash(id + 31.97, seed + 25.13);
            // Rare streams, never a curtain of falling vertical lines.
            float density = mix(0.085, 0.22, sqrt(waterLevel));
            float presence = 1.0 - smoothstep(density - 0.04, density + 0.085, h.x);
            if (presence <= 0.001) continue;

            // Distinct periods and offsets remove synchronized cell motion.
            float period = runCell / max(velocity * mix(0.67, 1.27, h.y), 2.0) * 2.6;
            float phase = fract(seconds / period + h2.x);
            // Motion sticks first, then descends; the head fades before reset.
            float release = smoothstep(0.25, 0.91, phase);
            float travel = 0.08 * phase + 0.92 * release;
            float alive = smoothstep(0.055, 0.21, phase)
                        * (1.0 - smoothstep(0.82, 0.975, phase));
            float opacity = presence * alive;
            if (opacity <= 0.001) continue;

            float sway = sin(phase * 6.28318 + h2.y * 6.28318) * runCell * 0.028;
            vec2 origin = (id + vec2(0.13 + 0.74 * h2.y, 0.07)) * runCell;
            vec2 center = origin + vec2(sway, runCell * travel * 1.26);
            vec2 delta = initial - center;
            float radius = runCell * mix(0.080, 0.170, h.y);
            vec2 pos = delta / vec2(radius, radius * mix(1.32, 1.97, release));
            float dist = length(pos);
            float aa = max(0.55 / max(radius, 1.0), 0.04);
            float body = (1.0 - smoothstep(0.84 - aa, 1.04 + aa, dist)) * opacity;
            coverage = max(coverage, body);
            float crest = smoothstep(0.34, 0.78, dist)
                        * (1.0 - smoothstep(0.93, 1.10 + aa, dist));
            lensNormal += pos * crest * opacity * vec2(0.75, 0.85);
            shine = max(shine, crest * opacity * 0.65
                              * smoothstep(-0.1, 0.85, -pos.x - pos.y));

            // Only a released drop forms a tapering trail BEHIND its head.
            float distanceAbove = max(-delta.y, 0.0);
            float lengthPx = max(1.0, runCell * release * 1.20);
            float meander = sin((center.y - distanceAbove) * 0.043 + h2.y * 6.28318)
                           * radius * 0.20;
            float streakWidth = mix(radius * 0.46, radius * 0.20,
                                   smoothstep(0.0, lengthPx, distanceAbove));
            float stripe = 1.0 - smoothstep(streakWidth * 0.45,
                                    streakWidth * 1.40 + 0.7,
                                    abs(delta.x + meander));
            float tailMask = stripe * (1.0 - smoothstep(0.63, 1.00,
                                                  distanceAbove / lengthPx))
                          * step(delta.y, 0.0) * opacity * release;
            // A shallow damp track is not an opaque bright cylinder.
            trail = max(trail, tailMask * 0.72);
            lensNormal.x += sign(delta.x + meander + 0.0001) * tailMask * 0.18;
        }
    }
    return clamp(coverage, 0.0, 1.0);
}
