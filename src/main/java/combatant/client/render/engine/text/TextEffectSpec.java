/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text;

import java.util.Locale;

/**
 * Deterministic, explicit text animation/effect description used by Java and JS UI paths.
 */
public record TextEffectSpec(String kind,
                             float speed,
                             float phase,
                             float intensity,
                             boolean perGlyph) {
    public static final TextEffectSpec NONE = new TextEffectSpec("none", 0.0f, 0.0f, 0.0f, false);

    public static TextEffectSpec of(String kind, float speed) {
        String normalized = normalize(kind);
        if ("none".equals(normalized)) return NONE;
        return new TextEffectSpec(normalized, Math.max(0.01f, speed), 0.0f, 1.0f, true);
    }

    private static String normalize(String kind) {
        if (kind == null || kind.isBlank()) return "none";
        String out = kind.trim().toLowerCase(Locale.ROOT);
        return out.equals("off") || out.equals("false") ? "none" : out;
    }

    public TextEffectSpec withPhase(float value) {
        return new TextEffectSpec(kind, speed, value, intensity, perGlyph);
    }

    public boolean enabled() {
        return !"none".equals(kind) && intensity > 0.0f;
    }

    public float timeBase(float seconds) {
        return seconds * speed + phase;
    }

    public float glyphPhase(float seconds, int glyphIndex, float step) {
        return timeBase(seconds) + (perGlyph ? glyphIndex * step : 0.0f);
    }
}
