/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/*
 * Faceted crystal-glass hover material for ClickGUI module rows.
 *
 * The material is intentionally analytic and low-cost: one triangular lattice,
 * one pseudo-normal per facet, broad travelling growth energy and a sparse
 * specular/chip response. No iterative noise field or texture dependency.
 */
vec3 crystalTriangleCell(vec2 p, float cellSize, out vec2 cellId, out float orientation) {
    const float INV_SQRT3 = 0.57735026919;
    vec2 lattice = vec2(
        p.x - p.y * INV_SQRT3,
        p.y * (2.0 * INV_SQRT3)
    ) / cellSize;

    cellId = floor(lattice);
    vec2 f = fract(lattice);
    orientation = step(1.0, f.x + f.y);

    vec3 lower = vec3(1.0 - f.x - f.y, f.x, f.y);
    vec3 upper = vec3(f.x + f.y - 1.0, 1.0 - f.y, 1.0 - f.x);
    return mix(lower, upper, orientation);
}

float crystalPulse(float phase) {
    float d = abs(fract(phase + 0.5) - 0.5);
    float p = 1.0 - smoothstep(0.045, 0.245, d);
    return p * p;
}

float crystalBand(float phase, float width) {
    float d = abs(fract(phase + 0.5) - 0.5);
    return 1.0 - smoothstep(width * 0.42, width, d);
}

float crystalPow16(float x) {
    float x2 = x * x;
    float x4 = x2 * x2;
    float x8 = x4 * x4;
    return x8 * x8;
}

vec4 crystalSurface(vec2 p, float time, float reveal, float seed, vec3 c0, vec3 c1, vec3 hi) {
    vec2 q = p;
    q.x += q.y * 0.31 + seed * 2.73;
    q.y += seed * 1.41;

    vec2 cellId;
    float orientation;
    vec3 bary = crystalTriangleCell(q, 0.78, cellId, orientation);

    vec2 rnd = hash22(cellId + vec2(orientation * 17.0, orientation * 31.0) + seed * 53.0);
    float rndZ = hash11(cellId.x * 19.7 + cellId.y * 7.3 + orientation * 13.0 + seed * 71.0);

    float edgeCoord = min(bary.x, min(bary.y, bary.z));
    float edgeAa = max(fwidth(edgeCoord), 0.0015);
    float seam = 1.0 - smoothstep(edgeAa * 0.78, edgeAa * 2.70, edgeCoord);

    float centerCoord = 1.0 - max(bary.x, max(bary.y, bary.z));
    float innerCore = smoothstep(0.105, 0.285, centerCoord);
    float innerShell = smoothstep(0.030, 0.175, centerCoord);

    vec2 tilt = vec2(rnd.x * 2.0 - 1.0, rnd.y * 2.0 - 1.0);
    vec3 normal = normalize(vec3(tilt * vec2(0.48, 0.36), 0.86 + rndZ * 0.34));
    vec3 lightDir = normalize(vec3(-0.52, -0.31, 0.94));
    vec3 viewDir = vec3(0.0, 0.0, 1.0);
    vec3 halfDir = normalize(lightDir + viewDir);

    float ndl = saturate(dot(normal, lightDir));
    float ndv = saturate(dot(normal, viewDir));
    float specBase = saturate(dot(normal, halfDir));
    float specular = crystalPow16(specBase);
    float fresnelBase = 1.0 - ndv;
    float fresnel = fresnelBase * fresnelBase;
    fresnel *= fresnel;

    // Broad energy front: several neighboring facets illuminate together, so
    // the crystal looks like it is growing/charging through the row.
    float growthFront = crystalBand(
        p.x * 0.060 - p.y * 0.42 - time * 0.040 + seed * 0.83,
        0.205
    );
    growthFront *= growthFront;

    float cellPhase = time * 0.090
                    + rnd.y * 0.73
                    + cellId.x * 0.071
                    - cellId.y * 0.117
                    + seed * 1.37;
    float facetPulse = crystalPulse(cellPhase);

    // Internal refracted light. It is detached from the outer
    // seams and strongest inside the facet core to create a second optical layer.
    float refractedBand = crystalBand(
        p.x * 0.052 + p.y * 0.63 - time * 0.021 + rndZ * 0.24 + seed * 0.47,
        0.135
    );
    float internalLight = refractedBand * innerShell * (0.28 + growthFront * 0.72);

    // Sparse chips flash harder than the general specular term. Only a small
    // subset of facets receive them, which avoids turning the material into glitter.
    float chipGate = smoothstep(0.86, 0.985, rndZ);
    float chipPulse = crystalPulse(time * 0.055 + rnd.x * 1.71 + seed * 0.39);
    float chipGlint = chipGate * chipPulse * (0.30 + specular * 0.70);

    // Two mineral veins only live on facet boundaries. They reinforce structure
    // without drawing a permanent wireframe across the whole row.
    float veinA = crystalBand(p.x * 0.089 + p.y * 0.73 + seed * 0.31, 0.073);
    float veinB = crystalBand(p.x * 0.067 - p.y * 0.97 + seed * 0.67, 0.061);
    float vein = max(veinA, veinB) * seam;
    float veinEnergy = vein * (0.18 + growthFront * 0.82) * (0.55 + facetPulse * 0.45);

    float depthGrade = 0.24 + rnd.y * 0.76;
    float lightGrade = 0.28 + ndl * 0.72;

    vec3 deepColor = mix(c0 * 0.12, c1 * 0.40, depthGrade * 0.42);
    vec3 facetColor = mix(c0 * 0.26, c1 * 0.86, lightGrade);
    facetColor = mix(facetColor, hi * 1.04, fresnel * 0.18 + ndl * 0.08);

    vec3 color = mix(deepColor, facetColor, 0.34 + innerShell * 0.44);

    // Inner volume bends toward the highlight color while the external plane
    // keeps the category palette. This separation is what makes it read as glass/crystal.
    vec3 innerColor = mix(c1 * 0.72, hi * 1.10, 0.34 + ndl * 0.34);
    color = mix(color, innerColor, internalLight * 0.34 + innerCore * growthFront * 0.08);

    float growthEnergy = growthFront * (0.22 + facetPulse * 0.78);
    color = mix(color, mix(c1, hi, 0.58) * 1.06, innerCore * growthEnergy * 0.17);

    // Boundary refraction + physical-ish facet highlights.
    color += mix(c1, hi, 0.66) * seam * (0.050 + fresnel * 0.10 + growthEnergy * 0.13);
    color += hi * specular * (0.12 + growthFront * 0.13);
    color += hi * chipGlint * 0.22;
    color += mix(c1, hi, 0.78) * veinEnergy * 0.21;

    // Very slow highlight sweep ties isolated facets together while remaining
    // subordinate to the actual facet lighting.
    float sweep = crystalBand(p.x * 0.054 - p.y * 0.30 - time * 0.018 + seed * 0.73, 0.115);
    sweep *= 0.22 + ndl * 0.78;
    color = mix(color, hi * 1.08, sweep * innerShell * 0.075);

    float alpha = 0.018 + depthGrade * 0.014 + innerShell * 0.010;
    alpha += seam * (0.040 + fresnel * 0.030 + growthEnergy * 0.036);
    alpha += internalLight * 0.034;
    alpha += specular * 0.085;
    alpha += chipGlint * 0.075;
    alpha += veinEnergy * 0.035;
    alpha += sweep * innerShell * 0.010;
    alpha *= easeOutCubic(reveal);

    return vec4(color, saturate(alpha));
}
