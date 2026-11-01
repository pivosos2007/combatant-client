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
#define COMBATANT_CLIMATE_FIELD 1
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
vec2 climateDisplacementPx = vec2(0.0);
float climateOpticalCoverage = 0.0;
float climateOpticalRelief = 0.0;

void combatantGlassVisual(CombatantGlassContext c, inout CombatantGlassField f) {
    float rain = clamp(uGlassVisualParams[0].x, 0.0, 1.0);
    float water = clamp(uGlassVisualParams[0].y, 0.0, 1.0);
    float cold = clamp(uGlassVisualParams[0].z, 0.0, 1.0);
    float snow = clamp(uGlassVisualParams[0].w, 0.0, 1.0);
    float condensation = clamp(uGlassVisualParams[1].y, 0.0, 1.0);
    float heat = clamp(uGlassVisualParams[1].z, 0.0, 1.0);
    vec2 px = c.localUv * c.sizePx;

    // The reference rain distortion and one-shot exit-water GPU history are separate.
    vec2 waterNormal = vec2(0.0);
    float waterTrail = 0.0, waterShine = 0.0;
    // Sample one global framebuffer-space field. Sampling by localUv caused
    // identical drop patterns to repeat on every similarly sized HUD card.
    vec2 screenPx = warpedLocal(v_Local) / max(c.logicalScale, vec2(1e-6));
    float wetBody = combatantClimateDroplets(screenPx, c.sizePx,
            rain, water, c.time, waterNormal, waterTrail, waterShine);

    float rimDistance = max(-c.signedDistancePx, 0.0);
    float frostBand = cold * (1.0 - smoothstep(0.0, mix(3.0, 25.0, cold), rimDistance))
            * (0.35 + 0.65 * combatantClimateFrost(px, 13.7, cold));
    float iceCrystals = snow * combatantClimateFrost(px + vec2(19.1, 7.7), 17.1, snow) * 0.32;
    float shimmer = sin(px.y * 0.073 + sin(px.x * 0.057 + c.time * 0.37)
                              + c.time * 0.62) * heat * 0.16;

    // The glass pass already samples u_BlurTexture. Shift those SAME blurred
    // pixels through the combined liquid height field; do not switch the
    // water body to the clean scene, which removes the glass blur entirely.
    // Existing water still refracts after precipitation stops. Weather only
    // supplies new drops to compute; it does not toggle visibility of history.
    // Refract the shared prepared blur by a real droplet normal. The reference
    // offsets are dimensionless; the former 7px cap made them almost invisible
    // after the Kawase blur removed high-frequency scene detail.
    vec2 displacement = waterNormal * 18.0;
    displacement.x += shimmer;
    f.offsetPx = clamp(displacement, vec2(-15.0), vec2(15.0));
    climateDisplacementPx = f.offsetPx;
    climateOpticalCoverage = smoothstep(0.012, 0.13, length(waterNormal));
    climateOpticalRelief = climateDirectionalRelief(waterNormal,
            climateHudDetailFactor(c.sizePx));
    // Wet runnels are narrow lenses. Their signed relief follows the existing
    // surface gradient, not a circle or an independent white contour.
    climateOpticalRelief += waterTrail * 0.016 * clamp(waterNormal.x * 5.0, -1.0, 1.0);
    // TargetHUD has a custom glass visual even when dry. Keep its original
    // impact-edge chromatic response while displaying the climate field.
    if (uGlassVisualParams[2].x > 0.5) {
        combatantGlassImpact(c, vec4(1.0), f);
    }
    // Keep both clean and stacked scenes in the established prepared blur
    // pipeline. Water modifies coordinates, NEVER the blur source selection.
    f.cleanReveal = 0.0;
    f.bodyCoverage = 0.0;
    // Optical contrast comes from the actual backdrop, not a bright contour
    // or a fake dark sphere outline.
    climateGlint = 0.0;
    climateShadow = 0.0;
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
    // Angular wet-surface lighting adds contrast on the nearly uniform fills
    // of compact HUD panels, without a circular glint or replacing glass blur.
    color = clamp(color + vec3(climateOpticalRelief), 0.0, 1.0);
    // The glass body intentionally remains blurred. A bounded *difference*
    // of clean scene samples restores readable refraction at droplet slopes
    // without replacing the prepared blur (or its stacked-lens result) with
    // an unblurred background. Flat pixels contribute exactly zero.
    if (climateOpticalCoverage > 0.0) {
        vec2 baseUv = clamp(c.sceneUv - climateDisplacementPx
                / max(c.framebufferSize, vec2(1.0)), 0.001, 0.999);
        vec3 refracted = texture(u_Texture, c.sceneUv).rgb;
        vec3 original = texture(u_Texture, baseUv).rgb;
        vec3 detail = refracted - original;
        // An upper stacked lens must refract the lower lens, not introduce
        // clean WORLD pixels in place of its already composed surface.
        float stacked = stackRegionMask(c.sceneUv, max(uScreen.zw, vec2(1.0)));
        if (stacked > 0.001) {
            vec3 refractedStack = sampleLiquidScene(c.sceneUv, uScreen.zw).rgb;
            vec3 originalStack = sampleLiquidScene(baseUv, uScreen.zw).rgb;
            detail = mix(detail, refractedStack - originalStack, stacked);
        }
        color = clamp(color + detail * (climateOpticalCoverage * 0.28),
                      0.0, 1.0);
    }
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
