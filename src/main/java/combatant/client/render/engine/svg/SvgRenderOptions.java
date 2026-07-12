/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.svg;

public record SvgRenderOptions(SvgColorMode colorMode, int overrideArgb, float alpha, float curveFlatness,
                               boolean textureCache, int rasterScale, int gradientStartArgb,
                               int gradientEndArgb, float gradientAngleDegrees) {
    public static final SvgRenderOptions DEFAULT = new SvgRenderOptions(
            SvgColorMode.FROM_FILE,
            0xFFFFFFFF,
            1.0f,
            0.65f,
            true,
            3,
            0xFFFFFFFF,
            0xFFFFFFFF,
            90f
    );

    public SvgRenderOptions(SvgColorMode colorMode, int overrideArgb, float alpha, float curveFlatness) {
        this(colorMode, overrideArgb, alpha, curveFlatness, true, 3);
    }

    public SvgRenderOptions(SvgColorMode colorMode,
                            int overrideArgb,
                            float alpha,
                            float curveFlatness,
                            boolean textureCache,
                            int rasterScale) {
        this(colorMode, overrideArgb, alpha, curveFlatness, textureCache, rasterScale,
                overrideArgb, overrideArgb, 90f);
    }

    public SvgRenderOptions(SvgColorMode colorMode,
                            int overrideArgb,
                            float alpha,
                            float curveFlatness,
                            boolean textureCache,
                            int rasterScale,
                            int gradientStartArgb,
                            int gradientEndArgb,
                            float gradientAngleDegrees) {
        this.colorMode = colorMode == null ? SvgColorMode.FROM_FILE : colorMode;
        this.overrideArgb = overrideArgb;
        this.alpha = clamp01(alpha);
        this.curveFlatness = Math.max(0.05f, curveFlatness);
        this.textureCache = textureCache;
        this.rasterScale = clampRasterScale(rasterScale);
        this.gradientStartArgb = gradientStartArgb;
        this.gradientEndArgb = gradientEndArgb;
        this.gradientAngleDegrees = gradientAngleDegrees;
    }

    public static SvgRenderOptions fromFile() {
        return DEFAULT;
    }

    public static SvgRenderOptions overrideColor(int argb) {
        return new SvgRenderOptions(SvgColorMode.OVERRIDE, argb, 1.0f, 0.65f, true, 3);
    }

    public static SvgRenderOptions linearGradient(int startArgb, int endArgb, float angleDegrees) {
        return new SvgRenderOptions(SvgColorMode.GRADIENT_LINEAR, 0xFFFFFFFF, 1.0f, 0.25f, true, 4,
                startArgb, endArgb, angleDegrees);
    }

    public static SvgRenderOptions mesh() {
        return DEFAULT.withTextureCache(false);
    }

    public static SvgRenderOptions highQuality() {
        return new SvgRenderOptions(SvgColorMode.FROM_FILE, 0xFFFFFFFF, 1.0f, 0.25f, true, 4);
    }

    private static float clamp01(float value) {
        if (value < 0.0f) return 0.0f;
        return Math.min(value, 1.0f);
    }

    private static int clampRasterScale(int value) {
        if (value < 1) return 1;
        return Math.min(value, 4);
    }

    public SvgRenderOptions withAlpha(float alpha) {
        return new SvgRenderOptions(colorMode, overrideArgb, alpha, curveFlatness, textureCache, rasterScale,
                gradientStartArgb, gradientEndArgb, gradientAngleDegrees);
    }

    public SvgRenderOptions withCurveFlatness(float flatness) {
        return new SvgRenderOptions(colorMode, overrideArgb, alpha, flatness, textureCache, rasterScale,
                gradientStartArgb, gradientEndArgb, gradientAngleDegrees);
    }

    public SvgRenderOptions withTextureCache(boolean textureCache) {
        return new SvgRenderOptions(colorMode, overrideArgb, alpha, curveFlatness, textureCache, rasterScale,
                gradientStartArgb, gradientEndArgb, gradientAngleDegrees);
    }

    public SvgRenderOptions withRasterScale(int rasterScale) {
        return new SvgRenderOptions(colorMode, overrideArgb, alpha, curveFlatness, textureCache, rasterScale,
                gradientStartArgb, gradientEndArgb, gradientAngleDegrees);
    }
}
