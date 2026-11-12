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
