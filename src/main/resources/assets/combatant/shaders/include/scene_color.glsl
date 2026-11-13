/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
#ifndef COMBATANT_SCENE_COLOR_GLSL
#define COMBATANT_SCENE_COLOR_GLSL
vec3 combatantSrgbToLinear(vec3 value) {
    return mix(value / 12.92, pow(max((value + .055) / 1.055, vec3(0.)), vec3(2.4)),
               step(vec3(.04045), value));
}
vec3 combatantLegacyToSceneLinear(vec3 encoded) {
    return combatantSrgbToLinear(clamp(encoded, vec3(0.0), vec3(1.0)));
}
// Material inputs are authored display colors; lighting and coverage stay numeric.
vec3 combatantMaterialColor(vec3 encoded) {
#ifdef COMBATANT_SCENE_LINEAR_COLOR
    return combatantSrgbToLinear(max(encoded, vec3(0.0)));
#else
    return encoded;
#endif
}
vec3 combatantMaterialOutput(vec3 radiance) {
#ifdef COMBATANT_SCENE_LINEAR_COLOR
    return max(radiance, vec3(0.0));
#else
    return clamp(radiance, 0.0, 1.0);
#endif
}
// Display RGBA in [0, 1], before coverage or lighting. Preserve the black-background
// RGB contribution and white-background luminance with a scalar blend alpha.
vec4 combatantMaterial(vec4 authored) {
#ifdef COMBATANT_HDR_DISPLAY_OPACITY
    vec3 contribution = combatantSrgbToLinear(authored.rgb * authored.a);
    vec3 overWhite = combatantSrgbToLinear(vec3(1.0 - authored.a) + authored.rgb * authored.a);
    float opacity = 1.0 - dot(overWhite - contribution, vec3(0.2126, 0.7152, 0.0722));
    return vec4(contribution / max(opacity, 1e-8), opacity);
#else
    return vec4(combatantMaterialColor(authored.rgb), authored.a);
#endif
}
vec3 combatantLinearToSrgb(vec3 value) {
    return mix(value * 12.92, 1.055 * pow(max(value, vec3(0.)), vec3(1. / 2.4)) - .055,
               step(vec3(.0031308), value));
}
vec3 combatantOutputTransform(vec3 radiance) {
    radiance = max(radiance, vec3(0.));
    // A common scale preserves RGB ratios, including saturated highlights.
    float peak = max(radiance.r, max(radiance.g, radiance.b));
    float mapped = peak <= .82 ? peak : 1. - .18 * exp(- (peak - .82) / .18);
    return combatantLinearToSrgb(radiance * (mapped / max(peak, 1e-8)));
}
#endif
