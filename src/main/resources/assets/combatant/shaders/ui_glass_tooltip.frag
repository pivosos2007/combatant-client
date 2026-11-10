#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

#define COMBATANT_GLASS_CUSTOM_VISUAL 1
#define COMBATANT_GLASS_CUSTOM_SHADE 1

#include <combatant:ui_glass_surface.glsl>

void combatantGlassVisual(CombatantGlassContext context, inout CombatantGlassField field) {
    field.cleanReveal = clamp(uGlassVisualParams[2].x, 0.0, 1.0);
}

vec3 combatantGlassShade(CombatantGlassContext c, vec3 color) {
    vec4 geometry = uGlassVisualParams[0];
    vec4 rarity = uGlassVisualParams[1];

    float darkness = clamp(geometry.w, 0.0, 1.0);
    vec3 glass = mix(color, vec3(0.025, 0.031, 0.045), darkness);

    float headerFraction = clamp(geometry.x, 0.0, 1.0);
    if (headerFraction <= 0.001 || rarity.w <= 0.001) return glass;

    float headerMask = 1.0 - smoothstep(headerFraction - 0.035,
                                       headerFraction + 0.012, c.localUv.y);
    float leftMask = smoothstep(geometry.y - 0.040, geometry.y + 0.016, c.localUv.x);
    float rightMask = 1.0 - smoothstep(geometry.z - 0.016, geometry.z + 0.040, c.localUv.x);
    float headerCoverage = headerMask * leftMask * rightMask;

    float inset = max(-c.signedDistancePx, 0.0);
    float bevel = max(c.bevelPx, 1.0);
    float curvature = 1.0 - smoothstep(0.0, bevel, inset);
    vec3 surfaceNormal = normalize(vec3(c.shapeNormal * curvature * 4.2, 1.0));
    vec3 viewDirection = normalize(vec3(-0.14, -0.20, 1.0));
    float viewDotNormal = clamp(dot(surfaceNormal, viewDirection), 0.0, 1.0);
    float f0 = 0.035;
    float fresnel = f0 + (1.0 - f0) * pow(1.0 - viewDotNormal, 5.0);
    float edge = 1.0 - smoothstep(0.0, bevel * 1.65, inset);
    float reflected = fresnel * edge * (0.54 + 0.46 * curvature);
    vec3 rarityLight = clamp(rarity.rgb, vec3(0.0), vec3(1.0));
    float intensity = clamp(rarity.w, 0.0, 1.0);
    glass += rarityLight * headerCoverage * intensity * (reflected * 1.65 + 0.020);
    return min(glass, vec3(1.0));
}

void main() {
    combatantDrawGlass();
}
