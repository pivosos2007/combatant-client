/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

#ifndef COMBATANT_UI_BACKDROP_BLEND_GLSL
#define COMBATANT_UI_BACKDROP_BLEND_GLSL

float combatantBlendLuma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

vec3 combatantBlendOverlay(vec3 backdrop, vec3 source) {
    vec3 low = 2.0 * backdrop * source;
    vec3 high = 1.0 - 2.0 * (1.0 - backdrop) * (1.0 - source);
    return mix(low, high, step(vec3(0.5), backdrop));
}

vec3 combatantBlendMode(vec3 backdrop,
                        vec3 source,
                        vec3 accent,
                        float modeValue,
                        vec3 tone0,
                        vec3 tone1,
                        float pivot,
                        float softness) {
    int mode = int(floor(modeValue + 0.5));
    backdrop = clamp(backdrop, 0.0, 1.0);
    source = clamp(source, 0.0, 1.0);
    accent = clamp(accent, 0.0, 1.0);

    if (mode == 1) { // MULTIPLY
        return backdrop * source;
    }
    if (mode == 2) { // SCREEN
        return 1.0 - (1.0 - backdrop) * (1.0 - source);
    }
    if (mode == 3) { // OVERLAY
        return combatantBlendOverlay(backdrop, source);
    }
    if (mode == 4) { // DIFFERENCE
        return abs(backdrop - source);
    }
    if (mode == 5) { // EXCLUSION
        return backdrop + source - 2.0 * backdrop * source;
    }
    if (mode == 6) { // NEGATIVE
        // Invert the optical source itself. Do not mix source with its complement: that collapses
        // toward neutral gray. Blur/refraction remain visible because they are already encoded in
        // source before the inversion is applied.
        return vec3(1.0) - source;
    }
    if (mode == 7) { // MONOCHROME
        vec3 opticalBackdrop = mix(backdrop, source, 0.12);
        float l = combatantBlendLuma(opticalBackdrop);
        return vec3(l);
    }
    if (mode == 8) { // MONO_NEGATIVE
        vec3 opticalBackdrop = mix(backdrop, source, 0.12);
        float l = 1.0 - combatantBlendLuma(opticalBackdrop);
        return vec3(l);
    }
    if (mode == 9) { // DUOTONE
        vec3 opticalBackdrop = mix(backdrop, source, 0.12);
        float l = combatantBlendLuma(opticalBackdrop);
        float width = max(softness, 0.001);
        float t = smoothstep(pivot - width, pivot + width, l);
        vec3 lowTone = mix(tone0, tone0 * (0.84 + accent * 0.16), 0.20);
        vec3 highTone = mix(tone1, tone1 * (0.90 + accent * 0.10), 0.18);
        return mix(lowTone, highTone, t);
    }
    if (mode == 10) { // SOLARIZE
        float width = max(softness, 0.001);
        vec3 opticalBackdrop = mix(backdrop, source, 0.10);
        vec3 gate = smoothstep(vec3(pivot - width), vec3(pivot + width), opticalBackdrop);
        vec3 solarized = mix(opticalBackdrop, vec3(1.0) - opticalBackdrop, gate);
        return mix(solarized, solarized + (accent - 0.5) * 0.10, 0.28);
    }

    return source; // NORMAL
}

vec3 combatantResolveBackdropBlend(vec3 backdrop,
                                   vec3 source,
                                   vec3 accent,
                                   vec4 params,
                                   vec4 tone0,
                                   vec4 tone1) {
    float strength = clamp(params.y, 0.0, 1.0);
    vec3 blended = combatantBlendMode(
            backdrop,
            source,
            accent,
            params.x,
            clamp(tone0.rgb, 0.0, 1.0),
            clamp(tone1.rgb, 0.0, 1.0),
            clamp(params.z, 0.0, 1.0),
            clamp(params.w, 0.001, 0.5)
    );
    return mix(source, clamp(blended, 0.0, 1.0), strength);
}

#endif
