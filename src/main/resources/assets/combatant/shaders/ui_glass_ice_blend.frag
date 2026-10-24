#version 330 core
#extension GL_ARB_separate_shader_objects : require
#define COMBATANT_GLASS_CUSTOM_VISUAL 1
#define COMBATANT_GLASS_CUSTOM_BLEND 1

#include <combatant:ui_glass_surface.glsl>
#include <combatant:ui_ice_material.glsl>

// params[0]: seed, fracture density, frost, cracks.
// params[1]: blue absorption, displacement px, facet contrast, fracture clarity.
CombatantIceSurface ice;

void combatantGlassVisual(CombatantGlassContext c, inout CombatantGlassField f) {
    ice = combatantIceSurface(c.localUv, c.sizePx, c.signedDistancePx, uGlassVisualParams[0]);
    float displacement = clamp(uGlassVisualParams[1].y, 0.0, 12.0);
    f.offsetPx = vec2(ice.facetNormal.x, -ice.facetNormal.y) * displacement
            * ice.coverage;
    f.cleanReveal = ice.crack * clamp(uGlassVisualParams[1].w, 0.0, 0.25);
}

vec3 combatantGlassBlend(CombatantGlassContext c, vec3 backdrop, vec3 surface, vec3 tint) {
    vec3 iced = combatantIceColor(surface, ice, c.shapeNormal, uGlassVisualParams[1]);
    return combatantResolveBackdropBlend(backdrop, clamp(iced, 0.0, 1.0), tint,
            uBlendParams, uBlendTone0, uBlendTone1);
}

void main() {
    combatantDrawGlass();
}
