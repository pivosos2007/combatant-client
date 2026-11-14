/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.blend;

import net.minecraft.util.Mth;

/**
 * Blend material parameters for text and glass UI surfaces.
 * {@code strength} interpolates between the source material and backdrop result.
 * {@code tone0Argb} and {@code tone1Argb} provide the duotone colors.
 */
public record UiBackdropBlendSpec(
        UiBackdropBlendMode mode,
        float strength,
        int tone0Argb,
        int tone1Argb,
        float pivot,
        float softness
) {
    public static final UiBackdropBlendSpec NORMAL = new UiBackdropBlendSpec(
            UiBackdropBlendMode.NORMAL, 1.0f, 0xFF000000, 0xFFFFFFFF, 0.5f, 0.08f
    );

    public UiBackdropBlendSpec {
        mode = mode != null ? mode : UiBackdropBlendMode.NORMAL;
        strength = finiteClamp01(strength);
        pivot = finiteClamp01(pivot);
        softness = Float.isFinite(softness) ? Mth.clamp(softness, 0.001f, 0.5f) : 0.08f;
    }

    public static UiBackdropBlendSpec of(UiBackdropBlendMode mode, float strength) {
        return new UiBackdropBlendSpec(mode, strength, 0xFF000000, 0xFFFFFFFF, 0.5f, 0.08f);
    }

    public static UiBackdropBlendSpec multiply(float strength) {
        return of(UiBackdropBlendMode.MULTIPLY, strength);
    }

    public static UiBackdropBlendSpec screen(float strength) {
        return of(UiBackdropBlendMode.SCREEN, strength);
    }

    public static UiBackdropBlendSpec overlay(float strength) {
        return of(UiBackdropBlendMode.OVERLAY, strength);
    }

    public static UiBackdropBlendSpec difference(float strength) {
        return of(UiBackdropBlendMode.DIFFERENCE, strength);
    }

    public static UiBackdropBlendSpec exclusion(float strength) {
        return of(UiBackdropBlendMode.EXCLUSION, strength);
    }

    public static UiBackdropBlendSpec negative(float strength) {
        return of(UiBackdropBlendMode.NEGATIVE, strength);
    }

    public static UiBackdropBlendSpec monochrome(float strength) {
        return of(UiBackdropBlendMode.MONOCHROME, strength);
    }

    public static UiBackdropBlendSpec monoNegative(float strength) {
        return of(UiBackdropBlendMode.MONO_NEGATIVE, strength);
    }

    public static UiBackdropBlendSpec duotone(int shadowArgb, int lightArgb, float strength) {
        return new UiBackdropBlendSpec(
                UiBackdropBlendMode.DUOTONE,
                strength,
                shadowArgb,
                lightArgb,
                0.5f,
                0.10f
        );
    }

    public static UiBackdropBlendSpec solarize(float pivot, float softness, float strength) {
        return new UiBackdropBlendSpec(
                UiBackdropBlendMode.SOLARIZE,
                strength,
                0xFF000000,
                0xFFFFFFFF,
                pivot,
                softness
        );
    }

    public UiBackdropBlendSpec withStrength(float value) {
        return new UiBackdropBlendSpec(mode, value, tone0Argb, tone1Argb, pivot, softness);
    }

    public UiBackdropBlendSpec withTones(int shadowArgb, int lightArgb) {
        return new UiBackdropBlendSpec(mode, strength, shadowArgb, lightArgb, pivot, softness);
    }

    public UiBackdropBlendSpec withCurve(float newPivot, float newSoftness) {
        return new UiBackdropBlendSpec(mode, strength, tone0Argb, tone1Argb, newPivot, newSoftness);
    }

    public boolean isNormal() {
        return mode == UiBackdropBlendMode.NORMAL || strength <= 0.0001f;
    }

    private static float finiteClamp01(float value) {
        return Float.isFinite(value) ? Mth.clamp(value, 0.0f, 1.0f) : 0.0f;
    }
}
