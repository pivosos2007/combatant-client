#version 330 core
#extension GL_ARB_separate_shader_objects : require
#define COMBATANT_GLASS_CUSTOM_VISUAL 1

#include <combatant:ui_glass_surface.glsl>

void combatantGlassVisual(CombatantGlassContext context, inout CombatantGlassField field) {
    combatantGlassFlow(context, uGlassVisualParams[0], field);
}

void main() {
    combatantDrawGlass();
}
