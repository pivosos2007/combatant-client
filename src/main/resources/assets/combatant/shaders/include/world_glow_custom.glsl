/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/*
 * Optional world glow shader customization hook.
 * Override this file through a resource pack to add your own visual effect to ANY preset.
 * Called for every face and ribbon fragment; no extra render pass or preset slot is needed.
 * Keep the function signature. See docs/world-glow-customization.md.
 */
CombatantGlowVisual combatantGlowCustomize(int preset, vec2 uv, vec3 viewPosition,
                                            float t, float phase, CombatantGlowVisual visual) {
    return visual;
}
