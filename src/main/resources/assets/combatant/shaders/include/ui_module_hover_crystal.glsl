/*
 * Crystalline infection glaze for ClickGUI module hover rows.
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
    float p = 1.0 - smoothstep(0.055, 0.285, d);
    return p * p;
}

float crystalBand(float phase, float width) {
    float d = abs(fract(phase + 0.5) - 0.5);
    return 1.0 - smoothstep(width * 0.45, width, d);
}

vec4 crystalSurface(vec2 p, float time, float reveal, float seed, vec3 c0, vec3 c1, vec3 hi) {
    // Oblique lattice: it reads as a grown mineral skin instead of a UI grid.
    vec2 q = p;
    q.x += q.y * 0.34 + seed * 2.73;
    q.y += seed * 1.41;

    vec2 cellId;
    float orientation;
    vec3 bary = crystalTriangleCell(q, 0.72, cellId, orientation);

    vec2 rnd = hash22(cellId + vec2(orientation * 17.0, orientation * 31.0) + seed * 53.0);

    // Flat-facet depth/facing. The large range is deliberate: visible grade
    // separation is more important here than physically plausible lighting.
    float facing = 0.20 + rnd.x * 0.80;
    float depth = rnd.y;
    float faceCurve = facing * facing;

    float edgeCoord = min(bary.x, min(bary.y, bary.z));
    float edgeAa = max(fwidth(edgeCoord), 0.0015);
    float seam = 1.0 - smoothstep(edgeAa * 0.72, edgeAa * 2.55, edgeCoord);
    float facetCore = smoothstep(0.055, 0.235, edgeCoord);

    // Local infection pulse: neighbouring facets do not breathe in lock-step.
    float cellPhase = time * 0.125
                    + rnd.y * 0.71
                    + cellId.x * 0.087
                    - cellId.y * 0.133
                    + seed * 1.37;
    float cellPulse = crystalPulse(cellPhase);

    // Slow travelling infection front across the row. This is deliberately a
    // broad band so several adjacent facets activate as one spreading region.
    float front = crystalBand(
        p.x * 0.070 - p.y * 0.46 - time * 0.052 + seed * 0.83,
        0.19
    );
    front *= front;

    // Two sparse crystal-growth directions. They only become visible on facet
    // seams and flare when the infection front reaches them.
    float veinA = crystalBand(p.x * 0.095 + p.y * 0.69 + seed * 0.31, 0.082);
    float veinB = crystalBand(p.x * 0.073 - p.y * 0.91 + seed * 0.67, 0.070);
    float veinMask = max(veinA, veinB) * seam;
    float veinGlow = veinMask * (0.22 + front * 0.78) * (0.42 + cellPulse * 0.58);

    // Broad grade variation keeps the entire coating from sitting at one
    // luminance even when no pulse is active.
    float macroGrade = 1.0 - abs(fract(p.x * 0.041 + p.y * 0.17 + seed * 0.47) * 2.0 - 1.0);
    macroGrade = smoothstep(0.08, 0.92, macroGrade);
    float verticalGrade = saturate(0.62 - p.y * 0.52);

    float infection = saturate(cellPulse * 0.56 + front * 0.72 + veinGlow * 0.92);

    vec3 deepColor = mix(c0 * 0.16, c1 * 0.42, depth * 0.42 + macroGrade * 0.18);
    vec3 facetColor = mix(c0 * 0.30, c1 * 0.83, 0.18 + facing * 0.68);
    facetColor = mix(facetColor, hi * 1.04, faceCurve * 0.24 + verticalGrade * 0.08);

    vec3 color = mix(deepColor, facetColor, 0.38 + facing * 0.44);

    // Pulse begins inside the facet, then pushes energy into the seams. That
    // gives a living/infected read rather than simply blinking the whole tile.
    float corePulse = facetCore * cellPulse * (0.30 + front * 0.70);
    vec3 infectedColor = mix(c1 * 0.96, hi * 1.20, 0.28 + facing * 0.36);
    color = mix(color, infectedColor, corePulse * 0.34 + front * 0.11);

    float seamEnergy = seam * (0.12 + facing * 0.10 + infection * 0.30);
    color += mix(c1, hi, 0.62) * seamEnergy;
    color += hi * veinGlow * 0.34;

    // A very slow specular sweep is retained, but it is subordinate to the
    // infection animation instead of being the only motion in the material.
    float sweep = crystalBand(p.x * 0.060 - p.y * 0.25 - time * 0.024 + seed * 0.73, 0.12);
    sweep *= 0.30 + facing * 0.70;
    color = mix(color, hi * 1.08, sweep * 0.10);

    float alpha = 0.040 + facing * 0.030 + depth * 0.010 + macroGrade * 0.008;
    alpha += seam * (0.060 + infection * 0.060);
    alpha += corePulse * 0.040;
    alpha += veinGlow * 0.055;
    alpha += sweep * 0.018;
    alpha *= easeOutCubic(reveal);

    return vec4(color, saturate(alpha));
}
