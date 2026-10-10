/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

/** Material data consumed by Combatant's single shared glass surface pipeline. */
public record UiLiquidGlassMaterial(UiGlassOpticsSpec optics,
                                    float frostedJitterPx,
                                    float innerGlowStrength,
                                    float innerGlowSizePx,
                                    int innerGlowArgb, UiGlassInteraction interaction) implements UiMaterialSpec {
    @Override public Family family() { return Family.GLASS; }

    public UiLiquidGlassMaterial(UiGlassOpticsSpec optics, float frostedJitterPx,
                                 float innerGlowStrength, float innerGlowSizePx, int innerGlowArgb) {
        this(optics, frostedJitterPx, innerGlowStrength, innerGlowSizePx, innerGlowArgb,
                UiGlassInteraction.FRAME_POINTER);
    }

    public UiLiquidGlassMaterial withInteraction(UiGlassInteraction state) {
        return new UiLiquidGlassMaterial(optics, frostedJitterPx, innerGlowStrength,
                innerGlowSizePx, innerGlowArgb, state);
    }

    public static final UiLiquidGlassMaterial DEFAULT = new UiLiquidGlassMaterial(
            UiGlassOpticsSpec.LIQUID, 0f, 0f, 0f, 0x00FFFFFF);
    public static final UiLiquidGlassMaterial FRESNEL_GLASS = new UiLiquidGlassMaterial(
            UiGlassOpticsSpec.FRESNEL_GLASS, 0f, 0f, 0f, 0x00FFFFFF);

    public UiLiquidGlassMaterial(float frostedJitterPx,
                                 float innerGlowStrength,
                                 float innerGlowSizePx,
                                 int innerGlowArgb) {
        this(UiGlassOpticsSpec.LIQUID, frostedJitterPx, innerGlowStrength, innerGlowSizePx, innerGlowArgb);
    }

    public UiLiquidGlassMaterial {
        optics = optics != null ? optics : UiGlassOpticsSpec.LIQUID;
        interaction = interaction != null ? interaction : UiGlassInteraction.FRAME_POINTER;
        frostedJitterPx = finiteClamp(frostedJitterPx, 0f, 4f);
        innerGlowStrength = finiteClamp(innerGlowStrength, 0f, 1f);
        innerGlowSizePx = finiteClamp(innerGlowSizePx, 0f, 64f);
    }

    public static UiLiquidGlassMaterial frosted(float jitterPx) {
        return new UiLiquidGlassMaterial(UiGlassOpticsSpec.LIQUID, jitterPx, 0f, 0f, 0x00FFFFFF);
    }

    public static UiLiquidGlassMaterial innerGlow(float strength, float sizePx, int argb) {
        return new UiLiquidGlassMaterial(UiGlassOpticsSpec.LIQUID, 0f, strength, sizePx, argb);
    }

    public UiLiquidGlassMaterial withFrostedJitter(float jitterPx) {
        return new UiLiquidGlassMaterial(optics, jitterPx, innerGlowStrength, innerGlowSizePx, innerGlowArgb, interaction);
    }

    public UiLiquidGlassMaterial withInnerGlow(float strength, float sizePx, int argb) {
        return new UiLiquidGlassMaterial(optics, frostedJitterPx, strength, sizePx, argb, interaction);
    }

    public UiLiquidGlassMaterial withOptics(UiGlassOpticsSpec value) {
        return new UiLiquidGlassMaterial(value, frostedJitterPx, innerGlowStrength, innerGlowSizePx, innerGlowArgb, interaction);
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
