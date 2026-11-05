/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/*
 * Combatant world glow preset ABI (GLSL 330; shared by OpenGL and Vulkan compilation).
 * IDs are kept in sync with GlowSurfaces.Animation; do not change existing IDs.
 * Custom visualizations extend any preset via world_glow_custom.glsl.
 * This file deliberately does not own the Fresnel, halo or edge shading.
 */
struct CombatantGlowVisual {
    vec3 tint;      // Multiplies the caller's RGB color, e.g. vec3(1.0) preserves the tint.
    float body;     // Body luminance/alpha modulation [0, 2]. 1 = neutral.
    float accent;   // Additional highlight/energy mask [0, 2]. 0 = no highlight.
};

CombatantGlowVisual combatantGlowVisual(vec3 tint, float body, float accent) {
    return CombatantGlowVisual(tint, body, accent);
}

#include <combatant:world_glow_custom.glsl>

CombatantGlowVisual combatantGlowAnimation(int preset, vec2 uv, vec3 viewPosition, float t, float phase) {
    const float TAU = 6.28318530718;
    float p = phase * TAU;
    CombatantGlowVisual result = combatantGlowVisual(vec3(1.0), 1.0, 0.0);
    if (preset == 1) { // PULSE
        float wave = 0.5 + 0.5 * sin(t * 1.8 + p);
        result = combatantGlowVisual(vec3(1.0), 0.55 + 0.80 * wave, 0.15 + 0.75 * wave);
    } else if (preset == 2) { // BREATHE
        float wave = 0.5 + 0.5 * sin(t * 0.92 + p);
        wave = wave * wave * (3.0 - 2.0 * wave);
        result = combatantGlowVisual(vec3(1.0), 0.60 + 0.70 * wave, 0.10 + 0.65 * wave);
    } else if (preset == 3) { // FLOW
        float wave = 0.5 + 0.5 * sin((uv.x + uv.y * 0.82) * TAU * 1.4 - t * 1.65 + p);
        result = combatantGlowVisual(vec3(1.0), 0.68 + 0.62 * wave, 0.10 + 1.20 * wave);
    } else if (preset == 4) { // RIPPLE
        float radius = length(uv - vec2(0.5));
        float wave = 0.5 + 0.5 * cos(radius * 20.0 - t * 2.1 + p);
        result = combatantGlowVisual(vec3(1.0), 0.70 + 0.60 * wave, 1.30 * wave * wave);
    } else if (preset == 5) { // SWEEP: explicit legacy preset
        float sweep = fract(uv.y * 0.7 + t * 0.38 + phase);
        float band = smoothstep(0.0, 0.05, sweep) * (1.0 - smoothstep(0.05, 0.30, sweep));
        float sheen = 0.5 + 0.5 * sin((uv.x + uv.y) * 17.0 - t * 2.1 + p);
        result = combatantGlowVisual(vec3(1.0), 0.75 + 0.45 * sheen, band);
    }
    return combatantGlowCustomize(preset, uv, viewPosition, t, phase, result);
}
