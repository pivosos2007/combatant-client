#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

#define COMBATANT_GLASS_CUSTOM_VISUAL 1
#define COMBATANT_GLASS_CUSTOM_BLEND 1

#include <combatant:ui_glass_surface.glsl>
#include <combatant:ui_voronoi_structure.glsl>
uniform sampler2D u_MaterialMicro;
uniform sampler2D u_MaterialColor;
#include <combatant:ui_ice_material.glsl>

// params[0]: seed, fracture density, frost, cracks.
// params[1]: blue absorption, displacement px, facet contrast, fracture clarity.
CombatantIceSurface ice;

void combatantGlassVisual(CombatantGlassContext c, inout CombatantGlassField f) {
    ice = combatantIceSurface(c.localUv, c.sizePx, c.signedDistancePx, uGlassVisualParams[0],
            uGlassVisualParams[2], uGlassVisualParams[3]);
    float displacement = clamp(uGlassVisualParams[1].y, 0.0, 12.0);
    // Only locally frozen regions bend light. Full-surface normal displacement
    // would turn the entire glass panel into a repeating printed texture.
    float frostMask = combatantIceGlassCoverage(ice);
    // Reduce the added ICE distortion; the base Liquid Glass refraction is
    // already evaluated separately and must remain the dominant optical effect.
    f.offsetPx = vec2(ice.facetNormal.x, -ice.facetNormal.y) * displacement
            * frostMask * 0.55;
    f.cleanReveal = ice.crack * clamp(uGlassVisualParams[1].w, 0.0, 0.25) * 0.25;
}

vec3 combatantGlassBlend(CombatantGlassContext c, vec3 backdrop, vec3 surface, vec3 tint) {
    // Keep the selected backdrop blend and the already-computed glass optics.
    // Overlay a low-opacity *material response*, never the opaque ICE albedo.
    vec3 glass = combatantResolveBackdropBlend(backdrop, clamp(surface, 0.0, 1.0), tint,
            uBlendParams, uBlendTone0, uBlendTone1);
    return combatantIceGlassColor(glass, ice, c.shapeNormal);
}

void main() {
    combatantDrawGlass();
}
