#version 330 core
#extension GL_ARB_separate_shader_objects : require
/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

#define COMBATANT_GLASS_CUSTOM_VISUAL 1

#include <combatant:ui_glass_surface.glsl>

void combatantGlassVisual(CombatantGlassContext context, inout CombatantGlassField field) {
    combatantGlassImpact(context, uGlassVisualParams[0], field);
}

void main() {
    combatantDrawGlass();
}
