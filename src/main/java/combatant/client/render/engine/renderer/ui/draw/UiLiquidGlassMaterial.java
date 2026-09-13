/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

/** Optional material modifiers layered onto the existing Combatant liquid-glass model. */
public record UiLiquidGlassMaterial(float frostedJitterPx,
                                    float innerGlowStrength,
                                    float innerGlowSizePx,
                                    int innerGlowArgb) {
    public static final UiLiquidGlassMaterial DEFAULT = new UiLiquidGlassMaterial(0f, 0f, 0f, 0x00FFFFFF);

    public UiLiquidGlassMaterial {
        frostedJitterPx = finiteClamp(frostedJitterPx, 0f, 4f);
        innerGlowStrength = finiteClamp(innerGlowStrength, 0f, 1f);
        innerGlowSizePx = finiteClamp(innerGlowSizePx, 0f, 64f);
    }

    public static UiLiquidGlassMaterial frosted(float jitterPx) {
        return new UiLiquidGlassMaterial(jitterPx, 0f, 0f, 0x00FFFFFF);
    }

    public static UiLiquidGlassMaterial innerGlow(float strength, float sizePx, int argb) {
        return new UiLiquidGlassMaterial(0f, strength, sizePx, argb);
    }

    public UiLiquidGlassMaterial withFrostedJitter(float jitterPx) {
        return new UiLiquidGlassMaterial(jitterPx, innerGlowStrength, innerGlowSizePx, innerGlowArgb);
    }

    public UiLiquidGlassMaterial withInnerGlow(float strength, float sizePx, int argb) {
        return new UiLiquidGlassMaterial(frostedJitterPx, strength, sizePx, argb);
    }

    public boolean hasFrostedJitter() {
        return frostedJitterPx > 0.001f;
    }

    public boolean hasInnerGlow() {
        return innerGlowStrength > 0.001f && innerGlowSizePx > 0.001f && ((innerGlowArgb >>> 24) & 0xFF) > 0;
    }

    private static float finiteClamp(float value, float min, float max) {
        if (!Float.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
