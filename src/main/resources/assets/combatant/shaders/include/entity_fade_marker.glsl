/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
// COMBATANT_ENTITY_FADE_MARKER
int combatantFadeAlpha = int(floor(clamp(faceVertexColor.a, 0.0, 1.0) * 255.0 + 0.5));
int combatantFadeRed = int(floor(clamp(faceVertexColor.r, 0.0, 1.0) * 255.0 + 0.5));
int combatantFadeGreen = int(floor(clamp(faceVertexColor.g, 0.0, 1.0) * 255.0 + 0.5));
int combatantFadeBlue = int(floor(clamp(faceVertexColor.b, 0.0, 1.0) * 255.0 + 0.5));
ivec3 combatantFadeBits = ivec3(combatantFadeRed & 1, combatantFadeGreen & 1, combatantFadeBlue & 1);
if (combatantFadeAlpha < 255 &&
        (all(equal(combatantFadeBits, ivec3(1, 0, 1))) ||
         all(equal(combatantFadeBits, ivec3(1, 1, 0))))) {
    faceVertexColor.r = float(combatantFadeRed & 254) / 255.0;
    faceVertexColor.g = float(combatantFadeGreen & 254) / 255.0;
    faceVertexColor.b = float(combatantFadeBlue & 254) / 255.0;
    faceVertexColor.a = float(combatantFadeAlpha) / 255.0;
}
