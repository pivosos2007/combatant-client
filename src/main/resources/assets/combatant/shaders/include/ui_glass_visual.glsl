/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
#ifndef COMBATANT_GLASS_VISUAL_API
#define COMBATANT_GLASS_VISUAL_API

struct CombatantGlassContext {
    vec2 localUv;
    vec2 sizePx;
    vec2 framebufferSize;
    vec2 logicalScale;
    vec2 sceneUv;
    vec2 normal;
    vec2 shapeNormal;
    float signedDistancePx;
    vec2 pointer;
    vec2 velocity;
    float time;
    float activity;
    float press;
    float open;
    float edge;
    float bevelPx;
    float spectralAberration;
    float rawSplitPx;
    float splitLimitPx;
};

// Displacements use framebuffer pixels, +Y upwards. UV/pointer coordinates use +Y downwards.
struct CombatantGlassField {
    vec2 offsetPx;
    vec2 edgeChromaPx;
    float edgeCoverage;
    vec2 bodyChromaPx;
    float bodyCoverage;
    float spectralPhase;
    float cleanReveal;
};

vec2 combatantGlassDirection(vec2 direction, vec2 fallback) {
    float magnitude = length(direction);
    return magnitude > 1e-6 ? direction / magnitude : fallback;
}

// controls: speed, spatial frequency, dispersion amplitude, coverage.
void combatantGlassFlow(CombatantGlassContext c, vec4 controls, inout CombatantGlassField f) {
    float time = c.time * controls.x;
    vec2 uv = c.localUv * controls.y;
    float activity = smoothstep(0.02, 0.28, clamp(max(c.activity, c.press), 0.0, 1.0));
    float primaryWave = sin(time * 1.36 + uv.x * 8.0 - uv.y * 4.5);
    float secondaryWave = sin(time * 0.83 - uv.x * 3.5 + uv.y * 6.0 + 1.2);
    vec2 direction = combatantGlassDirection(vec2(
            cos(time * 0.91 + uv.y * 5.2 + primaryWave * 0.42),
            sin(time * 1.07 - uv.x * 4.6 + secondaryWave * 0.38)), c.normal);
    f.spectralPhase = sin(time * 1.08 + uv.x * 9.0 - uv.y * 5.0 + secondaryWave * 0.65);
    float waveScale = mix(0.68, 1.32, 0.5 + 0.5 * f.spectralPhase);
    float limitPx = min(c.splitLimitPx, 5.5 / max(min(c.logicalScale.x, c.logicalScale.y), 1e-6));
    float splitPx = min(c.bevelPx * c.spectralAberration * waveScale * max(controls.z, 0.0), limitPx);
    f.bodyChromaPx = direction * splitPx;
    f.bodyCoverage = mix(0.82, 0.35, smoothstep(0.16, 0.96, c.edge)) * activity * controls.w;
}

// controls: response, dispersion amplitude, coverage, reserved.
void combatantGlassImpact(CombatantGlassContext c, vec4 controls, inout CombatantGlassField f) {
    float activity = clamp(max(c.activity, c.press) * controls.x, 0.0, 1.0);
    float limitPx = min(c.bevelPx * mix(0.5, 1.0, activity), c.splitLimitPx);
    float splitPx = limitPx * tanh(c.rawSplitPx * max(controls.y, 0.0) / max(limitPx, 0.001));
    f.edgeChromaPx = c.normal * splitPx;
    f.edgeCoverage = smoothstep(0.0, 0.24, c.edge) * mix(0.72, 1.0, activity) * controls.z;
}

#endif
