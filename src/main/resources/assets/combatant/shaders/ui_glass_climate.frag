#version 330 core
#extension GL_ARB_separate_shader_objects : require
/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 * Original implementation based on public SDF/drop refraction techniques.
 * Reference: https://github.com/frmlinn/raindrops-v2 (MIT).
 */
#define COMBATANT_GLASS_CUSTOM_VISUAL 1
#define COMBATANT_GLASS_CUSTOM_SHADE 1
#include <combatant:ui_glass_surface.glsl>
#include <combatant:ui_climate_droplets.glsl>

float combatantClimateFrost(vec2 px, float seed, float density) {
    vec2 cell = floor(px / 10.0);
    vec2 fractUv = fract(px / 10.0) - 0.5;
    vec2 rand = combatantClimateHash(cell, seed);
    float arm = min(abs(fractUv.x), abs(fractUv.y));
    float diagonal = min(abs(fractUv.x + fractUv.y), abs(fractUv.x - fractUv.y));
    float growth = 1.0 - smoothstep(0.01, 0.065, min(arm, diagonal * 0.65));
    return growth * smoothstep(0.35, 0.95, rand.x + density * 0.40);
}

// Per-fragment illumination from the SAME droplet evaluation that supplies
// the optical normal. No duplicate 18-cell search in combatantGlassShade.
float climateGlint = 0.0;
float climateShadow = 0.0;

void combatantGlassVisual(CombatantGlassContext c, inout CombatantGlassField f) {
    float rain = clamp(uGlassVisualParams[0].x, 0.0, 1.0);
    float water = clamp(uGlassVisualParams[0].y, 0.0, 1.0);
    float cold = clamp(uGlassVisualParams[0].z, 0.0, 1.0);
    float snow = clamp(uGlassVisualParams[0].w, 0.0, 1.0);
    float condensation = clamp(uGlassVisualParams[1].y, 0.0, 1.0);
    float heat = clamp(uGlassVisualParams[1].z, 0.0, 1.0);
    vec2 px = c.localUv * c.sizePx;

    vec2 rainNormal = vec2(0.0), waterNormal = vec2(0.0);
    float rainTrail = 0.0, rainShine = 0.0;
    float waterTrail = 0.0, waterShine = 0.0;
    float rainBody = 0.0, waterBody = 0.0;
    if (rain > 0.002) {
        rainBody = combatantClimateDroplets(px, c.time, rain,
                25.0, 52.0, 7.31, rainNormal, rainTrail, rainShine);
    }
    if (water > 0.002) {
        waterBody = combatantClimateDroplets(px, c.time, water,
                42.0, 26.0, 31.73, waterNormal, waterTrail, waterShine);
    }

    float rimDistance = max(-c.signedDistancePx, 0.0);
    float frostBand = cold * (1.0 - smoothstep(0.0, mix(3.0, 25.0, cold), rimDistance))
            * (0.35 + 0.65 * combatantClimateFrost(px, 13.7, cold));
    float iceCrystals = snow * combatantClimateFrost(px + vec2(19.1, 7.7), 17.1, snow) * 0.32;
    float shimmer = sin(px.y * 0.073 + sin(px.x * 0.057 + c.time * 0.37)
                              + c.time * 0.62) * heat * 0.16;

    // Optical lens strength independent of the whitening/highlight model.
    // Rain is sparse; exit-water has larger, more convex lenses.
    vec2 displacement = rainNormal * rain * 5.4 + waterNormal * water * 7.2;
    displacement.x += (rainTrail * rain + waterTrail * water) * 0.65 + shimmer;
    f.offsetPx = clamp(displacement, vec2(-8.5), vec2(8.5));
    f.cleanReveal = clamp((rainBody * 0.36 + rainTrail * 0.30) * rain
                        + (waterBody * 0.42 + waterTrail * 0.34) * water, 0.0, 0.42);
    // bodyCoverage drives CHROMATIC mixing in the common glass shader; don't
    // misuse it as an opaque droplet coverage/color mask.
    f.bodyCoverage = 0.0;
    climateGlint = clamp(rainShine * rain + waterShine * water, 0.0, 1.0);
    climateShadow = clamp((rainBody * 0.19 + rainTrail * 0.13) * rain
                          + (waterBody * 0.22 + waterTrail * 0.15) * water,
                          0.0, 0.25);
    // Fractional icy coverage affects only the surface; there is no snow-image
    // overlay over the backdrop and no new full-screen capture.
    climateShadow += clamp((frostBand * 0.05 + iceCrystals * 0.03), 0.0, 0.07);
}

vec3 combatantGlassShade(CombatantGlassContext c, vec3 color) {
    float cold = clamp(uGlassVisualParams[0].z, 0.0, 1.0);
    float snow = clamp(uGlassVisualParams[0].w, 0.0, 1.0);
    vec2 px = c.localUv * c.sizePx;
    // Small one-sided highlights and a subdued contact edge, rather than a
    // circular white stroke. The actual body is visible by refraction.
    color *= 1.0 - climateShadow;
    color = mix(color, vec3(0.83, 0.92, 1.0), climateGlint * 0.26);
    if (cold + snow > 0.005) {
        float rim = cold * (1.0 - smoothstep(0.0, mix(2.0, 22.0, cold),
                                                  max(-c.signedDistancePx, 0.0)));
        float frost = combatantClimateFrost(px, 13.7, cold);
        float crystal = snow * combatantClimateFrost(px + vec2(19.1, 7.7), 17.1, snow);
        float scatter = min(0.20, rim * frost * 0.16 + crystal * 0.06);
        color = mix(color, vec3(0.76, 0.87, 0.96), scatter);
    }
    return color;
}

void main() { combatantDrawGlass(); }
