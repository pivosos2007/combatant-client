/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.modules;


import combatant.client.features.theme.Theme;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.animation.AnimationUtility;

enum ModulesMenuStyle {
    ;
    static final int MODULE_LIST_ON = 0xFF5DE878;
    static final int MODULE_LIST_OFF = 0xFFFF5E66;

    private static int text = 0xFFFFFFFF;
    private static int textMuted = 0xBFFFFFFF;
    private static int textFaint = 0x80FFFFFF;
    private static int panelBgGlassDark = 0x660C0C0C;
    private static int panelStroke = 0x2EFFFFFF;
    private static int split = 0x0DFFFFFF;
    private static int rowHover = 0x12FFFFFF;
    private static int shadow = 0x42000000;
    private static int themeAccent = 0xFFFFFFFF;
    private static int themeAccentSoft = 0xFFFFFFFF;

    static void syncTheme() {
        Themes.Theme theme = Theme.theme();
        if (theme == null) return;

        int accent = forceAlpha(theme.accent(), 255);
        int accentSoft = forceAlpha(theme.accentSoft(), 255);
        themeAccent = accent;
        themeAccentSoft = accentSoft;
        int surface = forceAlpha(theme.surface(), 255);
        int surfaceHover = forceAlpha(theme.surfaceHover(), 255);
        int stroke = forceAlpha(theme.strokeSoft(), 255);

        text = forceAlpha(theme.textPrimary(), 255);
        textMuted = withAlpha(theme.textMuted(), 210);
        textFaint = withAlpha(theme.textMuted(), 135);

        panelBgGlassDark = withAlpha(mix(surface, accent, 0.070f), 116);
        panelStroke = withAlpha(mix(stroke, accent, 0.155f), 76);
        split = withAlpha(mix(stroke, accent, 0.120f), 22);
        rowHover = withAlpha(mix(surfaceHover, accent, 0.125f), 34);
        shadow = withAlpha(0xFF000000, 72);
    }

    static int text() {
        return text;
    }

    static int textMuted() {
        return textMuted;
    }

    static int textFaint() {
        return textFaint;
    }

    static int panelBgGlassDark() {
        return panelBgGlassDark;
    }

    static int panelStroke() {
        return panelStroke;
    }

    static int split() {
        return split;
    }

    static int rowHover() {
        return rowHover;
    }

    static int shadow() {
        return shadow;
    }

    static int categoryFxPrimary(ModulesMenuCategory category, float alpha) {
        int semantic = switch (category) {
            case COMBAT -> 0xFF89170D;
            case MOVEMENT -> 0xFF174D70;
            case VISUALS -> 0xFF3D2378;
            case OTHER -> 0xFF073D66;
            case PLAYER -> 0xFF0B5848;
        };
        return withAlpha(mix(semantic, themeAccent, 0.18f), alpha * 0.94f);
    }

    static int categoryFxSecondary(ModulesMenuCategory category, float alpha) {
        int semantic = switch (category) {
            case COMBAT -> 0xFFED681A;
            case MOVEMENT -> 0xFF68C5EB;
            case VISUALS -> 0xFF815AD1;
            case OTHER -> 0xFF168CC4;
            case PLAYER -> 0xFF37B596;
        };
        return withAlpha(mix(semantic, themeAccentSoft, 0.12f), alpha * 0.96f);
    }

    static int categoryFxHighlight(ModulesMenuCategory category, float alpha) {
        int semantic = switch (category) {
            case COMBAT -> 0xFFFFF0C7;
            case MOVEMENT -> 0xFFE2F8FF;
            case VISUALS -> 0xFFE9E0FF;
            case OTHER -> 0xFFDDF6FF;
            case PLAYER -> 0xFFDFFFF5;
        };
        return withAlpha(mix(semantic, themeAccentSoft, 0.08f), alpha);
    }


    static int scrollTrackA(float alpha) {
        return withAlpha(SettingsGuiPalette.current().moduleScrollTrackA(), alpha);
    }

    static int scrollTrackB(float alpha) {
        return withAlpha(SettingsGuiPalette.current().moduleScrollTrackB(), alpha);
    }

    static int scrollHandleA(float alpha) {
        return withAlpha(SettingsGuiPalette.current().moduleScrollHandleA(), alpha);
    }

    static int scrollHandleB(float alpha) {
        return withAlpha(SettingsGuiPalette.current().moduleScrollHandleB(), alpha);
    }

    static int withAlpha(int color, float alpha) {
        float a = AnimationUtility.clamp(alpha, 0.0f, 1.0f);
        int ca = (color >>> 24) & 0xFF;
        int na = Math.round(ca * a);
        return (color & 0x00FFFFFF) | ((na & 0xFF) << 24);
    }

    static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    static int forceAlpha(int color, int alpha) {
        return withAlpha(color, alpha);
    }

    static int mix(int from, int to, float t) {
        t = AnimationUtility.clamp(t, 0.0f, 1.0f);

        int a = Math.round(((from >>> 24) & 0xFF) * (1.0f - t) + ((to >>> 24) & 0xFF) * t);
        int r = Math.round(((from >>> 16) & 0xFF) * (1.0f - t) + ((to >>> 16) & 0xFF) * t);
        int g = Math.round(((from >>> 8) & 0xFF) * (1.0f - t) + ((to >>> 8) & 0xFF) * t);
        int b = Math.round((from & 0xFF) * (1.0f - t) + (to & 0xFF) * t);

        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
}
