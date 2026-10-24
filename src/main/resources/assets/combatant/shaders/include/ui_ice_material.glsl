/*
 * Combatant Client, Copyright (c) 2026 pivosos2007. GPL-3.0-only.
 */
#ifndef COMBATANT_ICE_MATERIAL
#define COMBATANT_ICE_MATERIAL

struct CombatantIceSurface {
    vec2 facetNormal;
    float facet;
    float crack;
    float crackShadow;
    float frost;
    float rim;
    float coverage;
};

vec2 combatantIceHash(vec2 p) {
    vec3 h = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    h += dot(h, h.yzx + 33.33);
    return fract((h.xx + h.yz) * h.zy);
}

float combatantIceNoise(vec2 p) {
    vec2 cell = floor(p);
    vec2 t = fract(p);
    t = t * t * (3.0 - 2.0 * t);
    return mix(mix(combatantIceHash(cell).x, combatantIceHash(cell + vec2(1.0, 0.0)).x, t.x),
               mix(combatantIceHash(cell + vec2(0.0, 1.0)).x,
                   combatantIceHash(cell + vec2(1.0)).x, t.x), t.y);
}

// Pattern coordinates depend on local aspect ratio and seed, never screen position or time.
CombatantIceSurface combatantIceSurface(vec2 uv, vec2 sizePx, float signedDistancePx, vec4 controls) {
    float shortSide = max(min(sizePx.x, sizePx.y), 1.0);
    vec2 aspect = sizePx / shortSide;
    vec2 seed = vec2(controls.x * 13.71, controls.x * 7.93);
    vec2 local = uv * aspect;
    vec2 p = local * clamp(controls.y, 0.5, 6.0) + seed;
    p += 0.025 * sin(p.yx * 4.0 + seed);
    float aa = max(length(fwidth(p)), 0.001);
    float freezing = clamp(controls.z, 0.0, 1.0);
    float cloud = combatantIceNoise(local * 3.0 + seed);
    float rimWidthPx = clamp(shortSide * mix(0.035, 0.11, freezing), 2.0, 48.0);
    float rim = 1.0 - smoothstep(0.0, rimWidthPx, max(-signedDistancePx, 0.0));
    float frozenCoverage = freezing * rim * (0.58 + cloud * 0.42);
    if (rim < 0.001 || max(freezing, controls.w) < 0.001) {
        return CombatantIceSurface(vec2(0.0), 0.5, 0.0, 0.0, 0.0, 0.0, 0.0);
    }
    vec2 cell = floor(p);
    vec2 relative = fract(p);
    float nearest = 100.0;
    vec2 nearestVector = vec2(0.0);
    vec2 nearestCell = vec2(0.0);
    vec2 facetHash = vec2(0.5);
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            vec2 offset = vec2(float(x), float(y));
            vec2 hash = combatantIceHash(cell + offset);
            vec2 delta = offset + 0.18 + hash * 0.64 - relative;
            float distanceSquared = dot(delta, delta);
            if (distanceSquared < nearest) {
                nearest = distanceSquared;
                nearestVector = delta;
                nearestCell = offset;
                facetHash = hash;
            }
        }
    }
    // Nearest bisector, not F2-F1: the second closest site need not own the closest facet edge.
    float boundary = 100.0;
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            vec2 offset = nearestCell + vec2(float(x), float(y));
            vec2 delta = offset + 0.18 + combatantIceHash(cell + offset) * 0.64 - relative;
            vec2 between = delta - nearestVector;
            float separation = length(between);
            if (separation > 0.001) {
                boundary = min(boundary, dot((nearestVector + delta) * 0.5, between / separation));
            }
        }
    }
    float crackStrength = clamp(controls.w, 0.0, 1.0)
            * rim * (0.5 + cloud * 0.5);
    float crack = (1.0 - smoothstep(0.0, aa * 1.25, boundary)) * crackStrength;
    float shadow = (1.0 - smoothstep(0.003, 0.003 + aa * 1.5, boundary)) * crackStrength;
    float frost = frozenCoverage * (0.10 + cloud * 0.08);
    return CombatantIceSurface(facetHash * 2.0 - 1.0, facetHash.y,
            crack, shadow, frost, rim, frozenCoverage);
}

vec3 combatantIceColor(vec3 color, CombatantIceSurface ice, vec2 normal, vec4 optics) {
    float blue = clamp(optics.x, 0.0, 1.0) * ice.coverage;
    vec3 transmission = color * mix(vec3(1.0), vec3(0.76, 0.91, 1.0), blue);
    transmission *= 1.0 + (ice.facet - 0.5) * 0.07 * clamp(optics.z, 0.0, 1.0) * ice.coverage;
    transmission *= 1.0 - ice.crackShadow * 0.12;
    transmission = mix(transmission, vec3(0.78, 0.89, 0.95), ice.frost);
    transmission = mix(transmission, vec3(0.85, 0.95, 1.0), ice.crack * 0.25);
    float light = 0.35 + 0.65 * max(dot(normal, normalize(vec2(-0.6, 0.8))), 0.0);
    return mix(transmission, vec3(0.71, 0.86, 0.94), ice.coverage * ice.rim * light * 0.08);
}

#endif
