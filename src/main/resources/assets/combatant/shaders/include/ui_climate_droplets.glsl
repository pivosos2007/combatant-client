/*
 * The Drive Home - window-rain optical layer, (c) Martijn Steinrucken
 * (BigWings / The Art of Code), 2017, CC BY-NC-SA 3.0 Unported.
 * Provided by the user for integration into Combatant Client.
 * Original project: https://www.shadertoy.com/ ("The Drive Home")
 * Making of: https://www.youtube.com/watch?v=eKtsY7hYTPg
 * Original N31 hash credits Dave Hoskins.
 *
 * The full GetDrops optical model, its three-scale composition, animated
 * wobble/slide, trailing microdrops and fine ripple are used here. The
 * unrelated procedural city, lights, camera ray and song are not HUD assets.
 * Integration: physical framebuffer UVs, prepared glass blur, one-shot
 * submerged-water GPU history. This work must retain the upstream credit.
 */
#ifdef COMBATANT_CLIMATE_FIELD
uniform sampler2D u_ClimateField;

// Original BigWings droplet shaping functions (namespaced for UI shader use).
float climateDriveSawTooth(float t) {
    return cos(t + cos(t)) + sin(2.0 * t) * 0.2 + sin(4.0 * t) * 0.02;
}
float climateDriveDeltaSawTooth(float t) {
    return 0.4 * cos(2.0 * t) + 0.08 * cos(4.0 * t)
         - (1.0 - sin(t)) * sin(t + cos(t));
}
vec3 climateDriveN31(float p) {
    vec3 p3 = fract(vec3(p) * vec3(0.1031, 0.11369, 0.13787));
    p3 += dot(p3, p3.yzx + 19.19);
    return fract(vec3((p3.x + p3.y) * p3.z,
                      (p3.x + p3.z) * p3.y,
                      (p3.y + p3.z) * p3.x));
}

// Direct adaptation of The Drive Home's GetDrops(): all of its displacement
// and trailing bead construction remains intact. An independent trail output
// is needed only for the UI glass treatment, not for constructing the shape.
vec2 climateDriveGetDrops(vec2 uv, float seed, float t, out float trail) {
    uv.y += t * 0.05;
    uv *= vec2(10.0, 2.5) * 2.0;
    vec2 id = floor(uv);
    vec3 n = climateDriveN31(id.x + (id.y + seed) * 546.3524);
    vec2 bd = fract(uv);
    bd -= 0.5;
    bd.y *= 4.0;
    bd.x += (n.x - 0.5) * 0.6;
    t += n.z * 6.28;
    float slide = climateDriveSawTooth(t);
    float ts = 1.5;
    vec2 trailPos = vec2(bd.x * ts,
                         (fract(bd.y * ts * 2.0 - t * 2.0) - 0.5) * 0.5);
    bd.y += slide * 2.0;
    float dropShape = bd.x * bd.x;
    dropShape *= climateDriveDeltaSawTooth(t);
    bd.y += dropShape;
    float d = length(bd);
    float trailMask = smoothstep(-0.2, 0.2, bd.y);
    trailMask *= bd.y;
    float td = length(trailPos * max(0.5, trailMask));
    // The reference uses reversed smoothstep edges; the equivalent forms
    // below avoid undefined GLSL behaviour across GL/Vulkan drivers.
    float mainDrop = 1.0 - smoothstep(0.1, 0.2, d);
    float dropTrail = 1.0 - smoothstep(0.02, 0.1, td);
    dropTrail *= trailMask;
    // A thin, tapering wet wake stays BEHIND the sliding head. It refracts
    // the backdrop through its lateral slope instead of painting a flat line.
    // The local bead's X position is static; only its Y moves downwards.
    float wakeY = smoothstep(0.10, 0.42, bd.y)
                * (1.0 - smoothstep(1.35, 2.10, bd.y));
    float wakeWidth = mix(0.075, 0.030, clamp((bd.y - 0.1) / 1.9, 0.0, 1.0));
    float side = bd.x / max(wakeWidth, 0.001);
    float wakeCore = (1.0 - smoothstep(0.25, 1.25, abs(side))) * wakeY;
    vec2 wakeRefraction = vec2(-side * wakeCore * 0.13,
                                wakeCore * 0.025);
    trail = clamp(max(dropTrail, wakeCore * 0.8), 0.0, 1.0);
    return mix(bd * mainDrop, trailPos, dropTrail) + wakeRefraction;
}

// CameraSetup()'s entire original raindrop offset stack, without the camera.
// It modifies *only the blurred backdrop sample coordinates*. In particular,
// none of the old circular SDF/reflective "bubble" rendering is retained.
vec2 climateDriveRain(vec2 uv, float t, out float wetTrail) {
    // CameraSetup's rotating and horizontally panning lens was meant for
    // a driving camera, not stationary HUD glass. It carried every droplet
    // sideways as a group. Keep fixed screen-space X, animate vertical fall.
    vec2 dropUv = uv;

    // Preserve the same droplet language from the reference, but use more
    // independently shifted layers so the UI does not receive only a few
    // clustered heads in one region of a large panel.
    float t0, t1, t2, t3, t4;
    vec2 offs = climateDriveGetDrops(dropUv, 1.0, t, t0);
    offs += climateDriveGetDrops(dropUv * 1.35 + vec2(0.37, -0.19), 10.0, t, t1);
    offs += climateDriveGetDrops(dropUv * 1.95 + vec2(-0.41, 0.28), 25.0, t, t2);
    offs += climateDriveGetDrops(dropUv * 2.85 + vec2(0.23, 0.46), 7.0, t, t3) * 0.85;
    offs += climateDriveGetDrops(dropUv * 3.65 + vec2(-0.18, -0.33), 15.0, t, t4) * 0.55;

    float ripple = sin(t + uv.y * 3.1415 * 30.0 + uv.x * 124.0) * 0.5 + 0.5;
    ripple *= 0.005;
    offs += vec2(0.0, ripple);
    wetTrail = clamp(max(max(max(t0, t1 * 0.82), max(t2 * 0.68, t3 * 0.52)), t4 * 0.36), 0.0, 1.0);
    return offs * 0.82;
}

// The *separate* GPU field contains only water deposited at the moment the
// player exits a liquid. It never runs the reference's endlessly repeating
// rainfall emitter. Field.rg = adhered/moving volume, b = actual wet trail.
float climateExitHeight(vec3 water) {
    water = clamp(water, vec3(0.0), vec3(3.0));
    return 1.0 - exp(-water.r * 0.90 - water.g * 1.25 - water.b * 0.13);
}

// Small HUD panels occupy a tiny fraction of the screen-wide rain pattern.
// Add finer independent reference droplets for those surfaces, while leaving
// the existing large-panel optical composition unchanged.
float climateHudDetailFactor(vec2 sizePx) {
    float areaSpan = sqrt(max(sizePx.x * sizePx.y, 1.0));
    return 1.0 - smoothstep(95.0, 245.0, areaSpan);
}

// Signed optical response follows the actual refraction normal, not an
// independently drawn circular outline. It remains legible on flat HUD fills
// where sampling nearby pixels cannot provide noticeable contrast on its own.
float climateDirectionalRelief(vec2 displacement, float factor) {
    float slope = length(displacement);
    float edge = smoothstep(0.018, 0.135, slope);
    vec2 direction = displacement / max(slope, 0.0001);
    float facing = dot(direction, normalize(vec2(-0.47, -0.88)));
    return edge * (max(facing, 0.0) * mix(0.032, 0.115, factor)
                 - max(-facing, 0.0) * mix(0.018, 0.067, factor));
}

float combatantClimateDroplets(vec2 pixel, vec2 surfaceSizePx,
                               float rain, float waterExit,
                               float seconds, out vec2 lensNormal,
                               out float trail, out float shine) {
    vec2 screen = max(uScreen.xy, vec2(1.0));
    vec2 uv = vec2(pixel.x - screen.x * 0.5,
                   screen.y * 0.5 - pixel.y) / screen.y;

    // The Drive Home is the complete visual replacement for precipitation.
    // The reference's clock is slowed uniformly; all its layers share time.
    float rainWeight = smoothstep(0.02, 0.30, clamp(rain, 0.0, 1.0));
    vec2 rainOffset = vec2(0.0);
    float rainTrail = 0.0;
    if (rainWeight > 0.00001) {
        float clock = seconds * 0.36;
        rainOffset = climateDriveRain(uv, clock, rainTrail);
        float compact = climateHudDetailFactor(surfaceSizePx);
        if (compact > 0.001) {
            // The two extra fine-scale passes retain the reference's droplet
            // shape, slip and microtrails. Global UVs avoid repeating the same
            // small pattern across separate HUD elements.
            float fineTrail0, fineTrail1;
            vec2 fine0 = climateDriveGetDrops(uv * 4.9 + vec2(0.27, -0.31),
                                              37.0, clock, fineTrail0);
            vec2 fine1 = climateDriveGetDrops(uv * 7.1 + vec2(-0.43, 0.17),
                                              69.0, clock, fineTrail1);
            rainOffset += compact * (fine0 * 0.68 + fine1 * 0.48);
            rainTrail = max(rainTrail, compact * max(fineTrail0 * 0.70,
                                                     fineTrail1 * 0.48));
        }
        rainOffset *= rainWeight;
        rainTrail *= rainWeight;
    }

    // Existing post-emergence water has spatial/temporal memory. Normal is
    // reconstructed from the combined adhered + running water + thin trail.
    vec2 fieldUv = clamp(vec2(pixel.x / screen.x, 1.0 - pixel.y / screen.y),
                         vec2(0.001), vec2(0.999));
    vec2 texel = 1.0 / vec2(textureSize(u_ClimateField, 0));
    vec3 layers = texture(u_ClimateField, fieldUv).rgb;
    float h = climateExitHeight(layers);
    float hx0 = climateExitHeight(texture(u_ClimateField, fieldUv - vec2(texel.x, 0)).rgb);
    float hx1 = climateExitHeight(texture(u_ClimateField, fieldUv + vec2(texel.x, 0)).rgb);
    float hy0 = climateExitHeight(texture(u_ClimateField, fieldUv - vec2(0, texel.y)).rgb);
    float hy1 = climateExitHeight(texture(u_ClimateField, fieldUv + vec2(0, texel.y)).rgb);
    vec2 leftoverOffset = vec2(hx0 - hx1, hy1 - hy0);

    // No Fresnel ring, bulged sphere highlight or dark contact outline.
    // Droplet refraction is the one authored by Steinrucken; emerging water
    // adds only gentle geometric displacement and a persistent narrow wake.
    lensNormal = clamp(rainOffset * 1.55 + leftoverOffset * 0.42,
                       vec2(-0.62), vec2(0.62));
    trail = clamp(max(rainTrail * 1.08, layers.b * 0.58), 0.0, 1.0);
    shine = clamp(rainTrail * rainWeight * 0.012
                  + length(leftoverOffset) * h * 0.018, 0.0, 0.04);
    return clamp(max(length(rainOffset) * 1.8, h), 0.0, 1.0);
}
#endif

// Frost is an independent climate mode, not part of the rain reference.
vec2 combatantClimateHash(vec2 p, float seed) {
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
