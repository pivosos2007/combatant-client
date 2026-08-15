/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings;


import combatant.client.features.theme.Theme;
import combatant.client.features.theme.Themes;

public final class SettingsGuiPalette {
    private final Themes.Theme theme;

    private SettingsGuiPalette(Themes.Theme theme) {
        this.theme = theme;
    }

    public static SettingsGuiPalette current() {
        return new SettingsGuiPalette(Theme.theme());
    }

    private static boolean isLegacy() {
        return Themes.THEME_LEGACY.equals(Theme.currentId());
    }

    public static int withAlpha(int color, int alpha) {
        int a = clamp(alpha);
        return (color & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }

    public static int mix(int from, int to, float t) {
        t = clamp01(t);
        int a = Math.round(((from >>> 24) & 0xFF) * (1f - t) + ((to >>> 24) & 0xFF) * t);
        int r = Math.round(((from >>> 16) & 0xFF) * (1f - t) + ((to >>> 16) & 0xFF) * t);
        int g = Math.round(((from >>> 8) & 0xFF) * (1f - t) + ((to >>> 8) & 0xFF) * t);
        int b = Math.round((from & 0xFF) * (1f - t) + (to & 0xFF) * t);
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int darken(int color, float t) {
        return mix(color, 0xFF000000, t);
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        return Math.min(v, 1f);
    }

    private static int clamp(int v) {
        if (v < 0) return 0;
        return Math.min(v, 255);
    }

    public int menuMaskLeft() {
        if (isLegacy()) return 0xF5030406;
        return withAlpha(darken(theme.windowBg(), 0.82f), 245);
    }

    public int menuMaskRight() {
        if (isLegacy()) return 0xF5050609;
        return withAlpha(darken(theme.windowBg(), 0.76f), 245);
    }

    public int menuWindowBgLeft() {
        if (isLegacy()) return 0xCD121314;
        return withAlpha(mix(theme.windowBg(), theme.surface(), 0.18f), 205);
    }

    public int menuWindowBgRight() {
        if (isLegacy()) return 0xCD000205;
        return withAlpha(darken(mix(theme.windowBg(), theme.windowHeader(), 0.30f), 0.18f), 205);
    }

    public int menuWindowStroke() {
        if (isLegacy()) return 0xE1121314;
        return withAlpha(mix(theme.windowStroke(), theme.surface(), 0.25f), 225);
    }

    public int workspaceGlassTint() {
        return mix(theme.windowBg(), theme.surface(), 0.28f);
    }

    public int workspaceWashLeft() {
        if (isLegacy()) return 0x68141619;
        return withAlpha(mix(theme.windowBg(), theme.surface(), 0.22f), 104);
    }

    public int workspaceWashRight() {
        if (isLegacy()) return 0x74101217;
        return withAlpha(darken(mix(theme.windowBg(), theme.windowHeader(), 0.32f), 0.10f), 116);
    }

    public int glassEdgeStrong() {
        if (isLegacy()) return 0x75969CAA;
        return withAlpha(mix(theme.textMuted(), theme.strokeSoft(), 0.52f), 117);
    }

    public int glassEdgeSoft() {
        if (isLegacy()) return 0x342F343D;
        return withAlpha(mix(theme.windowStroke(), theme.strokeSoft(), 0.42f), 52);
    }

    public int navigationPlaneTop() {
        if (isLegacy()) return 0xB20B0D11;
        return withAlpha(darken(mix(theme.windowBg(), theme.surface(), 0.18f), 0.22f), 178);
    }

    public int navigationPlaneBottom() {
        if (isLegacy()) return 0xC407090D;
        return withAlpha(darken(mix(theme.windowBg(), theme.windowHeader(), 0.20f), 0.32f), 196);
    }

    public int contentPlaneTop() {
        if (isLegacy()) return 0x8217191D;
        return withAlpha(mix(theme.windowBg(), theme.surface(), 0.26f), 130);
    }

    public int contentPlaneBottom() {
        if (isLegacy()) return 0x96101216;
        return withAlpha(darken(mix(theme.windowBg(), theme.surface(), 0.18f), 0.17f), 150);
    }

    public int controlSurface() {
        if (isLegacy()) return 0x7A202329;
        return withAlpha(mix(theme.surface(), theme.windowHeader(), 0.24f), 122);
    }

    public int controlSurfaceHover() {
        if (isLegacy()) return 0xA02A2E36;
        return withAlpha(mix(theme.surfaceHover(), theme.accentSoft(), 0.10f), 160);
    }

    public int menuLineLow() {
        if (isLegacy()) return 0x0F373746;
        return withAlpha(mix(theme.strokeSoft(), theme.windowStroke(), 0.55f), 15);
    }

    public int menuLineMid() {
        if (isLegacy()) return 0x32373746;
        return withAlpha(mix(theme.strokeSoft(), theme.windowStroke(), 0.55f), 50);
    }

    public int menuLineStrong() {
        if (isLegacy()) return 0xFA373746;
        return withAlpha(mix(theme.strokeSoft(), theme.windowStroke(), 0.55f), 250);
    }

    public int menuSweepStart() {
        if (isLegacy()) return 0x235C6276;
        return withAlpha(mix(theme.textMuted(), theme.strokeSoft(), 0.35f), 35);
    }

    public int menuSweepEnd() {
        if (isLegacy()) return 0x8C767C94;
        return withAlpha(mix(theme.textPrimary(), theme.strokeSoft(), 0.28f), 140);
    }

    public int menuHeaderText() {
        if (isLegacy()) return 0xFFF5F5FF;
        return withAlpha(theme.textPrimary(), 245);
    }

    public int menuCategoryHoverLeft() {
        if (isLegacy()) return 0x64373737;
        return withAlpha(mix(theme.surface(), theme.strokeSoft(), 0.32f), 100);
    }

    public int menuCategoryHoverRight() {
        if (isLegacy()) return 0x64555564;
        return withAlpha(mix(theme.surfaceHover(), theme.accentSoft(), 0.12f), 100);
    }

    public int menuCategorySelectedLeft() {
        if (isLegacy()) return 0x87151515;
        return withAlpha(mix(theme.surface(), theme.windowHeader(), 0.34f), 135);
    }

    public int menuCategorySelectedRight() {
        if (isLegacy()) return 0x873D3D3D;
        return withAlpha(mix(theme.surfaceHover(), theme.strokeSoft(), 0.22f), 135);
    }

    public int menuCategoryText() {
        if (isLegacy()) return 0xE0FFFFFF;
        return withAlpha(theme.textPrimary(), 224);
    }

    public int menuShadow() {
        if (isLegacy()) return 0x5F000000;
        return withAlpha(0xFF000000, 95);
    }

    public int panelBgLeft() {
        if (isLegacy()) return 0xAF121314;
        return withAlpha(mix(theme.windowBg(), theme.surface(), 0.30f), 175);
    }

    public int panelBgRight() {
        if (isLegacy()) return 0xAF000205;
        return withAlpha(darken(mix(theme.windowBg(), theme.windowHeader(), 0.42f), 0.25f), 175);
    }

    public int panelStroke() {
        if (isLegacy()) return 0xE1121314;
        return withAlpha(mix(theme.windowStroke(), theme.strokeSoft(), 0.40f), 225);
    }

    public int panelDivider() {
        if (isLegacy()) return 0xFF303030;
        return withAlpha(mix(theme.strokeSoft(), theme.windowStroke(), 0.50f), 255);
    }

    public int panelText() {
        if (isLegacy()) return 0xF5F5F5FF;
        return withAlpha(theme.textPrimary(), 245);
    }

    public int panelMuted() {
        if (isLegacy()) return 0xA0A5A5A5;
        return withAlpha(mix(theme.textMuted(), theme.textPrimary(), 0.12f), 160);
    }

    public int panelShadow() {
        if (isLegacy()) return 0x5F000000;
        return withAlpha(0xFF000000, 95);
    }

    public int panelBlurTint() {
        if (isLegacy()) return 0xFF000000;
        return darken(theme.windowBg(), 0.70f);
    }

    public int panelPillBase() {
        if (isLegacy()) return 0x4B1F1B23;
        return withAlpha(mix(theme.surface(), theme.windowHeader(), 0.45f), 75);
    }

    public int panelPillActive() {
        if (isLegacy()) return 0xFF414141;
        return withAlpha(mix(theme.surfaceHover(), theme.accentSoft(), 0.28f), 255);
    }

    public int panelScrollTrackA() {
        if (isLegacy()) return 0x4B232323;
        return withAlpha(mix(theme.surface(), theme.windowBg(), 0.35f), 75);
    }

    public int panelScrollTrackB() {
        if (isLegacy()) return 0x5F2D2D2D;
        return withAlpha(mix(theme.surfaceHover(), theme.windowBg(), 0.35f), 95);
    }

    public int panelScrollHandleA() {
        if (isLegacy()) return 0x4B2C2C2C;
        return withAlpha(mix(theme.strokeSoft(), theme.surfaceHover(), 0.20f), 75);
    }

    public int panelScrollHandleB() {
        if (isLegacy()) return 0x5F656565;
        return withAlpha(mix(theme.accentSoft(), theme.strokeSoft(), 0.45f), 95);
    }

    public int moduleCardTop() {
        if (isLegacy()) return 0x91171819;
        return withAlpha(mix(theme.surface(), theme.windowHeader(), 0.30f), 145);
    }

    public int moduleCardTopStrong() {
        if (isLegacy()) return 0xFF131315;
        return withAlpha(mix(theme.surface(), theme.surfaceHover(), 0.52f), 255);
    }

    public int moduleCardBottom() {
        if (isLegacy()) return 0x910A0C0F;
        return withAlpha(darken(mix(theme.windowBg(), theme.surface(), 0.20f), 0.40f), 145);
    }

    public int moduleCardBottomStrong() {
        if (isLegacy()) return 0xFF131315;
        return withAlpha(mix(theme.surface(), theme.windowHeader(), 0.46f), 255);
    }

    public int moduleDividerStart() {
        if (isLegacy()) return 0xB9191928;
        return withAlpha(mix(theme.windowStroke(), theme.strokeSoft(), 0.35f), 185);
    }

    public int moduleDividerEnd() {
        if (isLegacy()) return 0xB937373C;
        return withAlpha(mix(theme.strokeSoft(), theme.accentSoft(), 0.22f), 185);
    }

    public int moduleTitleText() {
        if (isLegacy()) return 0xFFFFFFFF;
        return withAlpha(theme.textPrimary(), 255);
    }

    public int moduleDescriptionText() {
        if (isLegacy()) return 0xBA808080;
        return withAlpha(theme.textMuted(), 186);
    }

    public int moduleDescriptionIcon() {
        if (isLegacy()) return 0xAA808080;
        return withAlpha(mix(theme.textMuted(), theme.textPrimary(), 0.10f), 170);
    }

    public int moduleBindBg0() {
        if (isLegacy()) return 0x503D4347;
        return withAlpha(mix(theme.surface(), theme.windowHeader(), 0.20f), 80);
    }

    public int moduleBindBg1() {
        if (isLegacy()) return 0x50474D51;
        return withAlpha(mix(theme.surfaceHover(), theme.windowHeader(), 0.22f), 80);
    }

    public int moduleBindBg2() {
        if (isLegacy()) return 0x5051575B;
        return withAlpha(mix(theme.surfaceHover(), theme.accentSoft(), 0.12f), 80);
    }

    public int moduleBindBg3() {
        if (isLegacy()) return 0x505B6165;
        return withAlpha(mix(theme.surface(), theme.accentSoft(), 0.18f), 80);
    }

    public int moduleBindStroke() {
        if (isLegacy()) return 0xFF9B9BA5;
        return withAlpha(mix(theme.strokeSoft(), theme.textMuted(), 0.42f), 255);
    }

    public int moduleBindText() {
        if (isLegacy()) return 0xFF878894;
        return withAlpha(mix(theme.textMuted(), theme.textPrimary(), 0.28f), 255);
    }

    public int moduleNoSettingsText() {
        if (isLegacy()) return 0x966E707A;
        return withAlpha(mix(theme.textMuted(), theme.textPrimary(), 0.15f), 150);
    }

    public int moduleScrollTrackA() {
        if (isLegacy()) return 0x4B232323;
        return panelScrollTrackA();
    }

    public int moduleScrollTrackB() {
        if (isLegacy()) return 0x5F2D2D2D;
        return panelScrollTrackB();
    }

    public int moduleScrollHandleA() {
        if (isLegacy()) return 0x4B2C2C2C;
        return panelScrollHandleA();
    }

    public int moduleScrollHandleB() {
        if (isLegacy()) return 0x5F656565;
        return panelScrollHandleB();
    }
}
