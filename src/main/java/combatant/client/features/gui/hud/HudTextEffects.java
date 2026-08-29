/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud;

import combatant.client.config.values.EnumValue;
import combatant.client.render.engine.text.TextRenderer;

/**
 * Lightweight animated gradient text effects for HUD labels/icons.
 */
public enum HudTextEffects {
    ;

    public static boolean render(TextRenderer renderer,
                                 String text,
                                 float x,
                                 float y,
                                 int baseColor,
                                 Effect effect,
                                 int speed,
                                 float timeSec,
                                 boolean shadow) {
        if (renderer == null || text == null || text.isEmpty()) return false;
        if (effect == null || effect == Effect.NONE) return false;

        int alpha = (baseColor >>> 24) & 0xFF;
        if (alpha <= 0) return false;

        int black = (alpha << 24);
        int white = (alpha << 24) | 0x00FFFFFF;
        int darkColor = HudRenderUtil.mixColor(baseColor, black, 0.45f);
        int lightColor = HudRenderUtil.mixColor(baseColor, white, 0.22f);

        float phaseStep = 0.55f;
        float phaseShift = 0.85f;
        float speedFactor = Math.max(0.1f, speed * 0.08f);
        float tBase = timeSec * speedFactor;

        renderer.renderGradient(text, x, y, (idx, cp, gx, out) -> {
            float p = tBase + idx * phaseStep;
            float t0;
            float t1;
            if (effect == Effect.FLOW) {
                float flow = tBase + idx * 0.35f;
                t0 = 0.5f + 0.5f * (float) Math.sin(flow);
                t1 = 0.5f + 0.5f * (float) Math.sin(flow + 1.6f);
            } else if (effect == Effect.PULSE) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(tBase * 1.6f);
                t0 = pulse;
                t1 = pulse;
            } else if (effect == Effect.STRIPE) {
                float stripe = 0.5f + 0.5f * (float) Math.sin(p * 2.4f);
                t0 = stripe;
                t1 = 1.0f - stripe;
            } else {
                t0 = 0.5f + 0.5f * (float) Math.sin(p);
                t1 = 0.5f + 0.5f * (float) Math.sin(p + phaseShift);
            }
            out[0] = HudRenderUtil.mixColor(darkColor, lightColor, t0);
            out[1] = HudRenderUtil.mixColor(darkColor, lightColor, t1);
        }, shadow);
        return true;
    }

    public static int animatedColor(int baseColor,
                                    Effect effect,
                                    int speed,
                                    float timeSec,
                                    float phase) {
        if (effect == null || effect == Effect.NONE) return baseColor;
        int alpha = (baseColor >>> 24) & 0xFF;
        if (alpha <= 0) return baseColor;

        int black = (alpha << 24);
        int white = (alpha << 24) | 0x00FFFFFF;
        int darkColor = HudRenderUtil.mixColor(baseColor, black, 0.45f);
        int lightColor = HudRenderUtil.mixColor(baseColor, white, 0.22f);

        float speedFactor = Math.max(0.1f, speed * 0.08f);
        float tBase = timeSec * speedFactor + phase;
        float t;
        if (effect == Effect.FLOW) {
            t = 0.5f + 0.5f * (float) Math.sin(tBase);
        } else if (effect == Effect.PULSE) {
            t = 0.5f + 0.5f * (float) Math.sin(tBase * 1.6f);
        } else if (effect == Effect.STRIPE) {
            t = 0.5f + 0.5f * (float) Math.sin(tBase * 2.4f);
        } else {
            t = 0.5f + 0.5f * (float) Math.sin(tBase);
        }
        return HudRenderUtil.mixColor(darkColor, lightColor, t);
    }

    public static HudRenderUtil.ThemeGradient animatedGradient(HudRenderUtil.ThemeGradient gradient,
                                                                Effect effect,
                                                                int speed,
                                                                float timeSec,
                                                                float phase,
                                                                float intensity) {
        if (gradient == null) return null;
        float mix = Math.max(0.0f, Math.min(1.0f, intensity));
        if (effect == null || effect == Effect.NONE || mix <= 0.0f) return gradient;
        int start = HudRenderUtil.mixColor(
                gradient.start(),
                animatedColor(gradient.start(), effect, speed, timeSec, phase),
                mix
        );
        int end = HudRenderUtil.mixColor(
                gradient.end(),
                animatedColor(gradient.end(), effect, speed, timeSec, phase),
                mix
        );
        return new HudRenderUtil.ThemeGradient(start, end, gradient.angleDeg());
    }

    public enum Effect implements EnumValue.IdProvider {
        NONE("None"),
        MIX("Mix"),
        FLOW("Flow"),
        PULSE("Pulse"),
        STRIPE("Stripe");

        private final String id;

        Effect(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }
}
