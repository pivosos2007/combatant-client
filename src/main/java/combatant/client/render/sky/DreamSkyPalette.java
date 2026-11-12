/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.sky;

import combatant.client.features.theme.Themes;
import combatant.client.features.module.modules.visuals.WorldTweaks;

/** The full sky and cloud palette in opaque sRGB8 values. */
public record DreamSkyPalette(
        int dayZenith, int dayHorizon, int nightZenith, int nightHorizon,
        int glow, int stars, int cloudLight, int cloudShadow,
        int auroraPrimary, int auroraSecondary) {

    public static DreamSkyPalette fromTheme() {
        Themes.GradientSpec accent = Themes.hudAccentGradient();
        int a = accent.start();
        int b = accent.end();
        return new DreamSkyPalette(
                mix(0xFF427BB5, a, .23f), mix(0xFFAAC3E3, b, .16f),
                mix(0xFF071429, a, .12f), mix(0xFF263451, b, .22f),
                mix(0xFFE4A6A7, a, .47f), mix(0xFFF4F7FF, b, .14f),
                mix(0xFFF1F5FC, a, .11f), mix(0xFF8099B9, b, .27f),
                mix(0xFF62DCC5, a, .58f), mix(0xFF9379EA, b, .70f));
    }

    /**
     * Resolves active WorldTweaks sky and fog overrides into the unified Dream sky palette.
     * Absent or disabled overrides retain the current UI theme colors. The night palette
     * stays dark even when the configured daytime fog color is bright.
     */
    public static DreamSkyPalette fromWorldTweaks(WorldTweaks tweaks) {
        DreamSkyPalette base = fromTheme();
        if (tweaks == null || !tweaks.isEnabled()) return base;
        return withWorldTweaksOverrides(base,
                tweaks.hasSkyColorOverride(), tweaks.getSkyColorArgb(),
                tweaks.hasSkyGradientOverride(), tweaks.getSkyGradientColorArgb(),
                tweaks.isFogModifyEnabled(), tweaks.getFogColorArgb());
    }

    static DreamSkyPalette withWorldTweaksOverrides(DreamSkyPalette base,
            boolean skyEnabled, int skyColor,
            boolean gradientEnabled, int gradientColor,
            boolean fogEnabled, int fogColor) {
        if (!skyEnabled && !gradientEnabled && !fogEnabled) return base;
        int sky = skyEnabled ? skyColor : base.dayZenith();
        int gradient = gradientEnabled ? gradientColor : base.glow();
        int fog = fogEnabled ? fogColor :
                gradientEnabled ? gradient : base.dayHorizon();
        int dayZenith = skyEnabled ? sky : base.dayZenith();
        int dayHorizon = fogEnabled ? fog : gradientEnabled
                ? mix(base.dayHorizon(), gradient, .68f)
                : skyEnabled ? mix(base.dayHorizon(), sky, .60f) : base.dayHorizon();
        int nightZenith = skyEnabled ? mix(base.nightZenith(), sky, .07f) : base.nightZenith();
        int nightHorizon = fogEnabled ? mix(base.nightHorizon(), fog, .13f)
                : gradientEnabled ? mix(base.nightHorizon(), gradient, .15f)
                : skyEnabled ? mix(base.nightHorizon(), sky, .10f) : base.nightHorizon();
        int glow = gradientEnabled ? gradient : skyEnabled
                ? mix(base.glow(), sky, .30f) : base.glow();
        int cloudLight = mix(base.cloudLight(), dayZenith, .17f);
        int cloudShadow = mix(base.cloudShadow(), dayHorizon, .40f);
        int auroraPrimary = mix(base.auroraPrimary(), dayHorizon, .34f);
        int auroraSecondary = mix(base.auroraSecondary(), glow, .38f);
        return new DreamSkyPalette(dayZenith, dayHorizon, nightZenith, nightHorizon,
                glow, base.stars(), cloudLight, cloudShadow, auroraPrimary, auroraSecondary);
    }

    /** Convert configured/theme sRGB components to scene-linear light before HDR shading. */
    public static float linearRed(int color) { return linear((color >>> 16) & 255); }
    public static float linearGreen(int color) { return linear((color >>> 8) & 255); }
    public static float linearBlue(int color) { return linear(color & 255); }
    private static float linear(int channel) {
        float value = channel / 255f;
        return combatant.client.render.engine.hdr.SceneColor.srgbToLinear(value);
    }

    private static int mix(int base, int accent, float factor) {
        int r = Math.round(((base >> 16) & 255) * (1 - factor) + ((accent >> 16) & 255) * factor);
        int g = Math.round(((base >> 8) & 255) * (1 - factor) + ((accent >> 8) & 255) * factor);
        int b = Math.round((base & 255) * (1 - factor) + (accent & 255) * factor);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
