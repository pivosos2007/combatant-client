/*
 * Combatant world glow preset ABI (GLSL 330; shared by OpenGL and Vulkan compilation).
 * IDs are kept in sync with GlowSurfaces.Animation; do not change existing IDs.
 * For custom slots, override world_glow_custom.glsl in a higher-priority resource pack.
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
    if (preset == 1) { // PULSE: restrained intensity pulse, no translating stripe.
        float wave = 0.5 + 0.5 * sin(t * 1.8 + p);
        return combatantGlowVisual(vec3(1.0), 0.73 + 0.42 * wave, 0.12 + 0.30 * wave);
    }
    if (preset == 2) { // BREATHE: slower easing with stronger surface variation.
        float wave = 0.5 + 0.5 * sin(t * 0.92 + p);
        wave = wave * wave * (3.0 - 2.0 * wave);
        return combatantGlowVisual(vec3(1.0), 0.70 + 0.50 * wave, 0.08 + 0.28 * wave);
    }
    if (preset == 3) { // FLOW: smooth travelling interference, not a hard scanning band.
        float wave = 0.5 + 0.5 * sin((uv.x + uv.y * 0.82) * TAU * 1.4 - t * 1.65 + p);
        return combatantGlowVisual(vec3(1.0), 0.82 + 0.26 * wave, 0.25 + 0.50 * wave);
    }
    if (preset == 4) { // RIPPLE: concentric motion from the face center.
        float radius = length((uv - vec2(0.5)) * vec2(1.0, 1.0));
        float wave = 0.5 + 0.5 * cos(radius * 20.0 - t * 2.1 + p);
        return combatantGlowVisual(vec3(1.0), 0.82 + 0.24 * wave, 0.65 * wave * wave);
    }
    if (preset == 5) { // SWEEP: opt-in legacy-style moving band, no longer hardcoded.
        float sweep = fract(uv.y * 0.7 + t * 0.38 + phase);
        float band = smoothstep(0.0, 0.05, sweep) * (1.0 - smoothstep(0.05, 0.30, sweep));
        float sheen = 0.5 + 0.5 * sin((uv.x + uv.y) * 17.0 - t * 2.1 + p);
        return combatantGlowVisual(vec3(1.0), 0.93 + 0.14 * sheen, band);
    }
    if (preset == 8) return combatantGlowCustom1(uv, viewPosition, t, phase);
    if (preset == 9) return combatantGlowCustom2(uv, viewPosition, t, phase);
    if (preset == 10) return combatantGlowCustom3(uv, viewPosition, t, phase);
    if (preset == 11) return combatantGlowCustom4(uv, viewPosition, t, phase);
    return combatantGlowVisual(vec3(1.0), 1.0, 0.0); // STATIC, or unknown ID
}
