#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout(location = 0) in vec4 v_Color;
layout(location = 1) in vec2 v_UV;
layout(location = 2) in vec3 v_ViewNormal;
layout(location = 3) in vec3 v_ViewPosition;
layout(location = 4) in vec4 v_Fx;
layout(location = 5) in float v_Rim;
layout(location = 0) out vec4 fragColor;

// The stable effect API. Custom visualizations can override the included GLSL with a resource pack.
#include <combatant:world_glow_animations.glsl>

void main() {
    float fade = clamp(v_Fx.x, 0.0, 1.0);
    float seconds = v_Fx.y;
    float phaseAndPreset = v_Fx.z;
    int preset = int(floor(phaseAndPreset));
    float phase = fract(phaseAndPreset);
    float accentWeight = clamp(v_Fx.w, 0.0, 1.0);

    CombatantGlowVisual visual = combatantGlowAnimation(preset, v_UV, v_ViewPosition, seconds, phase);
    float body = clamp(visual.body, 0.0, 2.0);
    float accent = clamp(visual.accent * accentWeight, 0.0, 2.0);
    vec3 base = clamp(v_Color.rgb * visual.tint, 0.0, 1.0);
    vec3 hot = mix(base, vec3(1.0), 0.55);

    if (v_Rim < -0.5) {
        // Ribbon edges use the same batch/pipeline as faces but an independent glow intensity.
        float lineGlow = clamp(-v_Rim - 1.0, 0.0, 4.0);
        vec2 width = max(fwidth(v_UV), vec2(1.0e-5));
        float centerPx = abs(v_UV.y - 0.5) / width.y;
        float core = 1.0 - smoothstep(0.45, 1.85, centerPx);
        float halo = exp(-centerPx * 0.30);
        float energy = lineGlow * body;
        vec3 rgb = base * (0.78 + 0.22 * body) + hot * (core * 0.55 + halo * 0.25 + accent * 0.18) * energy;
        float alpha = v_Color.a * (core * 0.85 + halo * 0.40) * clamp(0.75 + lineGlow * 0.3, 0.0, 2.0);
        alpha += core * energy * 0.22 + halo * energy * 0.12 + accent * energy * 0.08;
        if (alpha <= 0.003) discard;
        fragColor = vec4(min(rgb, vec3(1.0)), clamp(alpha, 0.0, 1.0));
        return;
    }

    // Original world_glow_surface shading: pixel-space edge core and halo + view-dependent Fresnel.
    vec3 normal = normalize(v_ViewNormal);
    vec3 viewDir = normalize(-v_ViewPosition);
    float fresnel = pow(1.0 - abs(dot(normal, viewDir)), 2.2);
    vec2 fw = max(fwidth(v_UV), vec2(1.0e-5));
    vec2 edgePx = min(v_UV, 1.0 - v_UV) / fw;
    float edge = min(edgePx.x, edgePx.y);
    float core = 1.0 - smoothstep(0.7, 1.9, edge);
    float halo = exp(-edge * 0.26);

    float rimWeight = max(v_Rim, 0.0) * (0.12 + 0.88 * fade);
    float rim = core * 0.92 + halo * 0.34 + fresnel * 0.22;
    vec3 rgb = base * (0.86 + 0.14 * body)
            + hot * (core * 0.85 + accent * 0.55) * clamp(rimWeight * body * 2.0, 0.0, 2.0);
    float alpha = v_Color.a * (0.90 + 0.22 * body)
            + rimWeight * rim * body + max(v_Rim, 0.0) * accent * fade * 0.35;
    if (alpha <= 0.003) discard;
    fragColor = vec4(min(rgb, vec3(1.0)), clamp(alpha, 0.0, 1.0));
}
