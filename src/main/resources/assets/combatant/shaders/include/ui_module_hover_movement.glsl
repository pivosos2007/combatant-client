/*
 * Directional movement/slipstream material for ClickGUI module hover rows.
 *
 * Movement must not resemble the Player pearlescent/plasma field. This effect
 * is intentionally geometric and directional: long speed streaks, pressure
 * bands and a bright leading edge. No FBM/noise loops or volumetric body.
 */

float movementStreakLayer(vec2 p,
                          float time,
                          float seed,
                          float lanes,
                          float xScale,
                          float speed,
                          float thickness,
                          float shear) {
    vec2 q = vec2(p.x + p.y * shear, p.y);

    float laneCoord = (q.y + 0.5) * lanes;
    float laneId = floor(laneCoord);
    float laneRnd = hash11(laneId + seed * 47.0 + lanes * 3.1);
    float laneY = fract(laneCoord) - 0.5;
    laneY -= (laneRnd - 0.5) * 0.34;

    float line = 1.0 - smoothstep(thickness, thickness * 2.15, abs(laneY));

    float phase = fract(q.x * xScale - time * speed + laneRnd * 1.71 + seed * 2.93);
    float head = smoothstep(0.015, 0.075, phase);
    float tail = 1.0 - smoothstep(0.22, 0.82, phase);
    float segment = head * tail;

    // Thin bright front inside the longer translucent streak.
    float lead = 1.0 - smoothstep(0.045, 0.105, abs(phase - 0.105));
    return line * (segment * 0.72 + lead * 0.52);
}

vec4 movementSurface(vec2 p, float time, float reveal, float seed, vec3 c0, vec3 c1, vec3 hi) {
    float farStreak = movementStreakLayer(p, time, seed + 0.7, 4.0, 0.22, 0.54, 0.105, 0.54);
    float midStreak = movementStreakLayer(p, time, seed + 2.1, 6.0, 0.31, 0.86, 0.080, 0.66);
    float nearStreak = movementStreakLayer(p, time, seed + 5.4, 9.0, 0.43, 1.25, 0.060, 0.78);

    float streaks = farStreak * 0.34 + midStreak * 0.58 + nearStreak * 0.78;

    // Broad pressure sheet travelling in the same direction. It gives the row
    // a coherent velocity cue without filling it with plasma-like noise.
    float pressure = 1.0 - smoothstep(
        0.050,
        0.155,
        abs(fract((p.x + p.y * 0.72) * 0.115 - time * 0.205 + seed * 0.41) - 0.5)
    );
    pressure *= pressure;

    // A secondary compressed band makes the motion feel layered rather than a
    // single scrolling texture.
    float compression = 1.0 - smoothstep(
        0.018,
        0.070,
        abs(fract((p.x - p.y * 0.38) * 0.175 - time * 0.315 + seed * 0.79) - 0.5)
    );

    float verticalFade = 1.0 - smoothstep(0.34, 0.54, abs(p.y));
    float kinetic = saturate(streaks + pressure * 0.42 + compression * 0.28);

    vec3 color = mix(c0 * 0.34, c1 * 0.92, saturate(streaks * 0.64 + pressure * 0.24));
    color = mix(color, hi * 1.12, saturate(nearStreak * 0.48 + compression * 0.34));
    color += c1 * pressure * 0.08;

    // No opaque body: most of the row remains clear glass and the velocity is
    // communicated by moving lines/bands only.
    float alpha = farStreak * 0.055
                + midStreak * 0.092
                + nearStreak * 0.145
                + pressure * 0.040
                + compression * 0.052;
    alpha *= verticalFade * easeOutCubic(reveal);

    return vec4(color, saturate(alpha));
}
