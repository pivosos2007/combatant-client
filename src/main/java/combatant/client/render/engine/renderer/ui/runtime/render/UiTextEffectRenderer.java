/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.render;

import combatant.client.render.engine.text.Font;
import combatant.client.render.engine.text.TextEffectSpec;
import combatant.client.render.engine.text.TextRenderer;

enum UiTextEffectRenderer {
    ;

    static boolean render(TextRenderer renderer,
                          String text,
                          float x,
                          float y,
                          int baseColor,
                          String effect,
                          int speed,
                          float timeSec,
                          boolean shadow) {
        float speedFactor = Math.max(0.1f, speed * 0.08f);
        return render(renderer, text, x, y, baseColor, TextEffectSpec.of(effect, speedFactor), timeSec, shadow);
    }

    static boolean render(TextRenderer renderer,
                          String text,
                          float x,
                          float y,
                          int baseColor,
                          TextEffectSpec effect,
                          float timeSec,
                          boolean shadow) {
        if (renderer == null) return false;
        if (text == null || text.isEmpty()) return false;
        if (effect == null || !effect.enabled()) return false;

        int alpha = (baseColor >>> 24) & 0xFF;
        if (alpha <= 0) return false;

        int darkColor = mixColor(baseColor, alpha << 24, 0.45f);
        int lightColor = mixColor(baseColor, (alpha << 24) | 0x00FFFFFF, 0.22f);
        renderer.renderGradient(text, x, y, gradient(effect, darkColor, lightColor, timeSec), shadow);
        return true;
    }

    private static Font.GlyphGradient gradient(TextEffectSpec effect, int darkColor, int lightColor, float timeSec) {
        return (idx, cp, glyphX, out) -> {
            float p = effect.glyphPhase(timeSec, idx, 0.55f);
            float t0;
            float t1;
            switch (effect.kind()) {
                case "flow" -> {
                    float flow = effect.glyphPhase(timeSec, idx, 0.35f);
                    t0 = wave(flow);
                    t1 = wave(flow + 1.6f);
                }
                case "pulse" -> {
                    float pulse = wave(effect.timeBase(timeSec) * 1.6f);
                    t0 = pulse;
                    t1 = pulse;
                }
                case "stripe" -> {
                    float stripe = wave(p * 2.4f);
                    t0 = stripe;
                    t1 = 1.0f - stripe;
                }
                default -> {
                    t0 = wave(p);
                    t1 = wave(p + 0.85f);
                }
            }
            out[0] = mixColor(darkColor, lightColor, t0);
            out[1] = mixColor(darkColor, lightColor, t1);
        };
    }

    private static float wave(float value) {
        return 0.5f + 0.5f * (float) Math.sin(value);
    }

    private static int mixColor(int from, int to, float t) {
        float k = Math.max(0.0f, Math.min(1.0f, t));
        int a1 = (from >>> 24) & 0xFF;
        int r1 = (from >>> 16) & 0xFF;
        int g1 = (from >>> 8) & 0xFF;
        int b1 = from & 0xFF;
        int a2 = (to >>> 24) & 0xFF;
        int r2 = (to >>> 16) & 0xFF;
        int g2 = (to >>> 8) & 0xFF;
        int b2 = to & 0xFF;
        int a = Math.round(a1 + (a2 - a1) * k);
        int r = Math.round(r1 + (r2 - r1) * k);
        int g = Math.round(g1 + (g2 - g1) * k);
        int b = Math.round(b1 + (b2 - b1) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
