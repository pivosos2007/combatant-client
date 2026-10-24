/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import combatant.client.render.engine.text.BuiltinFontCatalog;

import combatant.client.features.theme.Theme;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.SettingsGlassMaterial;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.engine.text.TextRenderer;

enum UnifiedSettingsSkin {
    ;
    static final float ROW_GAP = 3f;

    static int TEXT_PRIMARY = 0xFFE8ECF7;
    static int TEXT_MUTED = 0xFFADB5C6;
    static int TEXT_FAINT = 0x88ADB5C6;

    static int SURFACE = 0x301A1F27;
    static int SURFACE_HOVER = 0x40222932;
    static int SURFACE_SOFT = 0x28161B23;
    static int SURFACE_GRADIENT_START = SURFACE_SOFT;
    static int SURFACE_GRADIENT_END = SURFACE_HOVER;
    static float SURFACE_GRADIENT_ANGLE = 90f;
    static boolean SURFACE_GRADIENT_ENABLED = false;
    static int CARD_GRADIENT_START = SURFACE_SOFT;
    static int CARD_GRADIENT_END = SURFACE_HOVER;
    static float CARD_GRADIENT_ANGLE = 90f;
    static boolean CARD_GRADIENT_ENABLED = false;
    static int STROKE_GRADIENT_START = TEXT_FAINT;
    static int STROKE_GRADIENT_END = TEXT_FAINT;
    static float STROKE_GRADIENT_ANGLE = 90f;
    static boolean STROKE_GRADIENT_ENABLED = false;

    static int ACCENT = 0xFF9747FF;
    static int ACCENT_SOFT = 0x629747FF;
    static int ACCENT_GRADIENT_START = ACCENT;
    static int ACCENT_GRADIENT_END = ACCENT_SOFT;
    static float ACCENT_GRADIENT_ANGLE = 45f;
    static int CHECK_COLOR = 0xFFEFF3FF;

    // Expensive-style controls keep the geometry, while the material is authored from the
    // active Combatant theme.  These are deliberately four-corner friendly so LayoutRender2D
    // can keep the same gradient grammar used by the rest of the client.
    static int MODERN_CONTROL_TOP = 0xE61A1B1D;
    static int MODERN_CONTROL_BOTTOM = 0xF017181A;
    static int MODERN_CONTROL_HOVER_TOP = 0xF01E1F22;
    static int MODERN_CONTROL_HOVER_BOTTOM = 0xF01A1B1E;
    static int MODERN_BORDER_START = 0x702A2C31;
    static int MODERN_BORDER_END = 0xA0383B42;
    static int MODERN_TRACK_TOP = 0xD20F1011;
    static int MODERN_TRACK_BOTTOM = 0xE20B0C0E;

    private static Themes.Theme syncedTheme;
    private static Themes.ThemeEntry syncedEntry;
    private static SettingRenderSurface syncedSurface;
    private static boolean synced;

    static void syncTheme() {
        Themes.Theme theme = Theme.theme();
        if (theme == null) return;
        // Theme/ThemeEntry are immutable records and the animated variants are identity-cached, so
        // the derived palette only changes when either instance or the render surface changes.
        Themes.ThemeEntry currentEntry = Theme.currentEntry();
        SettingRenderSurface currentSurface = SettingRenderContext.current();
        if (synced && theme == syncedTheme && currentEntry == syncedEntry && currentSurface == syncedSurface) return;
        syncedTheme = theme;
        syncedEntry = currentEntry;
        syncedSurface = currentSurface;
        synced = true;

        int accent = forceAlpha(theme.accent(), 255);
        int accentSoft = forceAlpha(theme.accentSoft(), 255);
        int surface = forceAlpha(theme.surface(), 255);
        int hover = forceAlpha(theme.surfaceHover(), 255);

        TEXT_PRIMARY = forceAlpha(theme.textPrimary(), 255);
        TEXT_MUTED = withAlpha(theme.textMuted(), 210);
        TEXT_FAINT = withAlpha(theme.textMuted(), 135);

        ACCENT = accent;
        ACCENT_SOFT = withAlpha(accentSoft, 135);
        CHECK_COLOR = ClickGuiRenderer.mixColor(TEXT_PRIMARY, accent, 0.10f);

        SURFACE = withAlpha(ClickGuiRenderer.mixColor(surface, accent, 0.055f), 82);
        SURFACE_HOVER = withAlpha(ClickGuiRenderer.mixColor(hover, accent, 0.095f), 104);
        SURFACE_SOFT = withAlpha(ClickGuiRenderer.mixColor(surface, accent, 0.040f), 56);

        Themes.ThemeEntry entry = Theme.currentEntry();
        Themes.GradientSpec surfaceGradient = entry == null ? null : entry.surfaceGradient();
        Themes.GradientSpec cardGradient = entry == null ? null : entry.cardGradient();
        Themes.GradientSpec strokeGradient = entry == null ? null : entry.strokeGradient();

        SURFACE_GRADIENT_ENABLED = surfaceGradient != null && surfaceGradient.enabled();
        SURFACE_GRADIENT_START = SURFACE_GRADIENT_ENABLED
                ? withAlpha(ClickGuiRenderer.mixColor(forceAlpha(surfaceGradient.start(), 255), accent, 0.045f), 62)
                : SURFACE_SOFT;
        SURFACE_GRADIENT_END = SURFACE_GRADIENT_ENABLED
                ? withAlpha(ClickGuiRenderer.mixColor(forceAlpha(surfaceGradient.end(), 255), accent, 0.075f), 98)
                : SURFACE_HOVER;
        SURFACE_GRADIENT_ANGLE = surfaceGradient == null ? 90f : surfaceGradient.angleDeg();

        CARD_GRADIENT_ENABLED = cardGradient != null && cardGradient.enabled();
        CARD_GRADIENT_START = CARD_GRADIENT_ENABLED
                ? withAlpha(forceAlpha(cardGradient.start(), 255), 86)
                : SURFACE_SOFT;
        CARD_GRADIENT_END = CARD_GRADIENT_ENABLED
                ? withAlpha(forceAlpha(cardGradient.end(), 255), 118)
                : SURFACE_HOVER;
        CARD_GRADIENT_ANGLE = cardGradient == null ? 90f : cardGradient.angleDeg();

        STROKE_GRADIENT_ENABLED = strokeGradient != null && strokeGradient.enabled();
        STROKE_GRADIENT_START = STROKE_GRADIENT_ENABLED ? forceAlpha(strokeGradient.start(), 255) : TEXT_FAINT;
        STROKE_GRADIENT_END = STROKE_GRADIENT_ENABLED ? forceAlpha(strokeGradient.end(), 255) : TEXT_FAINT;
        STROKE_GRADIENT_ANGLE = strokeGradient == null ? 90f : strokeGradient.angleDeg();

        ACCENT_GRADIENT_START = CARD_GRADIENT_ENABLED ? forceAlpha(cardGradient.start(), 255) : ACCENT;
        ACCENT_GRADIENT_END = CARD_GRADIENT_ENABLED ? forceAlpha(cardGradient.end(), 255) : forceAlpha(accentSoft, 255);
        ACCENT_GRADIENT_ANGLE = CARD_GRADIENT_ENABLED ? cardGradient.angleDeg() : 45f;

        if (modernSettings()) {
            // MAIN/MAP controls intentionally keep the theme on a short leash. Richness comes
            // from hierarchy, edge lighting, inset wells and state geometry -- not from tinting
            // every rectangle with the active accent/card gradient.
            TEXT_PRIMARY = forceAlpha(theme.textPrimary(), 255);
            TEXT_MUTED = forceAlpha(theme.textMuted(), 218);
            TEXT_FAINT = forceAlpha(theme.textMuted(), 138);
            CHECK_COLOR = forceAlpha(ClickGuiRenderer.mixColor(theme.textPrimary(), accent, 0.06f), 255);

            int neutralSurface = neutralize(surface, 0.10f);
            int neutralHover = neutralize(hover, 0.12f);
            int neutralWindow = neutralize(forceAlpha(theme.windowBg(), 255), 0.07f);
            int neutralStrokeSoft = neutralize(forceAlpha(theme.strokeSoft(), 255), 0.08f);
            int neutralStroke = neutralize(forceAlpha(theme.windowStroke(), 255), 0.08f);

            int authoredTop = CARD_GRADIENT_ENABLED
                    ? neutralize(forceAlpha(cardGradient.start(), 255), 0.10f)
                    : neutralSurface;
            int authoredBottom = CARD_GRADIENT_ENABLED
                    ? neutralize(forceAlpha(cardGradient.end(), 255), 0.10f)
                    : neutralWindow;
            int authoredStrokeA = STROKE_GRADIENT_ENABLED
                    ? neutralize(forceAlpha(strokeGradient.start(), 255), 0.08f)
                    : neutralStrokeSoft;
            int authoredStrokeB = STROKE_GRADIENT_ENABLED
                    ? neutralize(forceAlpha(strokeGradient.end(), 255), 0.08f)
                    : neutralStroke;

            MODERN_CONTROL_TOP = withAlpha(ClickGuiRenderer.mixColor(neutralSurface, authoredTop, 0.34f), 220);
            MODERN_CONTROL_BOTTOM = withAlpha(ClickGuiRenderer.mixColor(neutralSurface, authoredBottom, 0.27f), 234);
            MODERN_CONTROL_HOVER_TOP = withAlpha(ClickGuiRenderer.mixColor(neutralHover, authoredTop, 0.28f), 238);
            MODERN_CONTROL_HOVER_BOTTOM = withAlpha(ClickGuiRenderer.mixColor(neutralHover, authoredBottom, 0.22f), 244);
            MODERN_BORDER_START = withAlpha(ClickGuiRenderer.mixColor(authoredStrokeA, 0xFFFFFFFF, 0.05f), 108);
            MODERN_BORDER_END = withAlpha(ClickGuiRenderer.mixColor(authoredStrokeB, 0xFFFFFFFF, 0.07f), 158);
            MODERN_TRACK_TOP = withAlpha(ClickGuiRenderer.mixColor(neutralWindow, neutralSurface, 0.20f), 222);
            MODERN_TRACK_BOTTOM = withAlpha(ClickGuiRenderer.mixColor(neutralWindow, 0xFF000000, 0.25f), 234);

            SURFACE = MODERN_CONTROL_TOP;
            SURFACE_HOVER = MODERN_CONTROL_HOVER_TOP;
            SURFACE_SOFT = MODERN_CONTROL_BOTTOM;
            SURFACE_GRADIENT_ENABLED = true;
            CARD_GRADIENT_ENABLED = true;
            STROKE_GRADIENT_ENABLED = true;
            STROKE_GRADIENT_START = MODERN_BORDER_START;
            STROKE_GRADIENT_END = MODERN_BORDER_END;

            // Accent is still theme-synced, but remains a semantic micro-accent: slider fill,
            // tiny selected indicators and focus response. It is no longer the material itself.
            ACCENT_GRADIENT_START = forceAlpha(ClickGuiRenderer.mixColor(accent, neutralSurface, 0.34f), 255);
            ACCENT_GRADIENT_END = forceAlpha(ClickGuiRenderer.mixColor(accentSoft, neutralWindow, 0.44f), 255);
            ACCENT_GRADIENT_ANGLE = cardGradient == null ? 45f : cardGradient.angleDeg();
        } else if (!modules()) {
            SettingsGuiPalette palette = SettingsGuiPalette.current();
            SURFACE = palette.controlSurfaceHover();
            SURFACE_HOVER = palette.controlSurfaceHover();
            SURFACE_SOFT = palette.controlSurface();
            SURFACE_GRADIENT_ENABLED = false;
            CARD_GRADIENT_ENABLED = false;
            STROKE_GRADIENT_ENABLED = false;
            STROKE_GRADIENT_START = palette.glassEdgeStrong();
            STROKE_GRADIENT_END = palette.glassEdgeSoft();
            ACCENT_GRADIENT_START = forceAlpha(accent, 255);
            ACCENT_GRADIENT_END = forceAlpha(ClickGuiRenderer.mixColor(accentSoft, surface, 0.28f), 255);
        }
    }

    static boolean modules() {
        return SettingRenderContext.current() == SettingRenderSurface.MODULES;
    }

    static boolean mainSettings() {
        return SettingRenderContext.current() == SettingRenderSurface.MAIN_SETTINGS;
    }

    static boolean mapSettings() {
        return SettingRenderContext.current() == SettingRenderSurface.MAP_SETTINGS;
    }

    static boolean modernSettings() {
        SettingRenderSurface surface = SettingRenderContext.current();
        return surface == SettingRenderSurface.MAIN_SETTINGS || surface == SettingRenderSurface.MAP_SETTINGS;
    }

    static float modernMetric(float main, float map) {
        return (mapSettings() ? map : main) * scale();
    }

    // Expensive control grammar. Heights are intentionally type-specific in the renderer;
    // rowHeight is only the compact single-line baseline (checkbox/text/bind/action).
    static float rowHeight() { return modernMetric(27f, 28f); }
    static float rowGap() { return modernMetric(15f, 16f); }
    static float labelSize() { return modernMetric(14.8f, 15f); }
    static float descriptionSize() { return modernMetric(13.2f, 13.4f); }
    static float controlHeight() { return modernMetric(27f, 28f); }
    static float controlRadius() { return modernMetric(6.5f, 7f); }
    static float controlStroke() { return modernMetric(1.15f, 1.15f); }
    static float checkboxSize() { return modernMetric(19f, 20f); }
    static float selectWidth() { return modernMetric(154f, 184f); }
    static float switchWidth() { return checkboxSize(); }
    static float switchHeight() { return checkboxSize(); }
    static float sliderWidth() { return modernMetric(170f, 220f); }
    static float sliderTrackHeight() { return modernMetric(3f, 3f); }
    static float sliderThumbWidth() { return modernMetric(10f, 11f); }
    static float sliderThumbHeight() { return sliderThumbWidth(); }
    static float sliderThumbOuterRadius() { return modernMetric(5f, 5f); }
    static float sliderThumbInnerRadius() { return modernMetric(3.5f, 3.5f); }
    static float valueFieldWidth() { return modernMetric(48f, 52f); }
    static float colorSwatchSize() { return modernMetric(14f, 15f); }
    static float popupItemHeight() { return modernMetric(25f, 26f); }
    static float popupRadius() { return modernMetric(7f, 7f); }
    static float rowPadX() { return 0f; }
    static float blockSpacing() { return modernMetric(12f, 12f); }

    static int modernControlBg() { return MODERN_CONTROL_TOP; }
    static int modernControlHoverBg() { return MODERN_CONTROL_HOVER_TOP; }
    static int modernControlBorder() { return MODERN_BORDER_START; }
    static int modernControlBorderHover() { return MODERN_BORDER_END; }
    static int modernTrackBg() { return MODERN_TRACK_TOP; }
    static int modernPopupBg() { return MODERN_CONTROL_BOTTOM; }
    static int modernWorkspaceBg() { return withAlpha(MODERN_CONTROL_BOTTOM, 150); }
    static int modernTextBright() { return CHECK_COLOR; }
    static int modernTextSecondary() { return mix(TEXT_MUTED, TEXT_PRIMARY, 0.62f); }
    static int modernIconMuted() { return TEXT_MUTED; }
    static int modernInsetBg() { return mix(MODERN_CONTROL_BOTTOM, 0xFF000000, 0.16f); }
    static int modernInsetHoverBg() { return mix(MODERN_CONTROL_HOVER_BOTTOM, 0xFF000000, 0.11f); }
    static int modernDivider() { return withAlpha(mix(MODERN_BORDER_END, TEXT_FAINT, 0.24f), 118); }

    static void drawModernControlSurface(float x, float y, float w, float h, float radius,
                                         float hoverAnim, float focusAnim) {
        float response = clamp01(Math.max(hoverAnim, focusAnim * 0.78f));
        int top = mix(MODERN_CONTROL_TOP, MODERN_CONTROL_HOVER_TOP, response);
        int bottom = mix(MODERN_CONTROL_BOTTOM, MODERN_CONTROL_HOVER_BOTTOM, response);
        int accentTop = mix(top, accentGradientStart(1f), focusAnim * 0.025f);
        int accentBottom = mix(bottom, accentGradientEnd(1f), focusAnim * 0.035f);
        LayoutRender2D.roundedQuad(x, y, w, h, radius, accentTop, top, bottom, accentBottom);

        int strokeA = mix(MODERN_BORDER_START, accentGradientStart(1f), focusAnim * 0.14f);
        int strokeB = mix(MODERN_BORDER_END, accentGradientEnd(1f), focusAnim * 0.18f);
        LayoutRender2D.roundedStrokeQuad(x, y, w, h, radius, controlStroke(),
                withAlpha(strokeA, 0.76f + response * 0.22f),
                withAlpha(strokeB, 0.82f + response * 0.18f),
                withAlpha(strokeB, 0.72f + response * 0.20f),
                withAlpha(strokeA, 0.66f + response * 0.18f));

        // Neutral inner bevel: this is what gives the control material depth without spending
        // more accent color.  It remains subtle enough to disappear on very small controls.
        float inset = modernMetric(1f, 1f);
        if (w > inset * 4f && h > inset * 4f) {
            float innerRadius = Math.max(1f, radius - inset);
            LayoutRender2D.roundedStrokeQuad(x + inset, y + inset, w - inset * 2f, h - inset * 2f,
                    innerRadius, Math.max(0.45f, modernMetric(0.55f, 0.55f)),
                    withAlpha(0xFFFFFFFF, 0.045f + response * 0.025f),
                    withAlpha(0xFFFFFFFF, 0.025f + response * 0.018f),
                    withAlpha(0xFF000000, 0.12f + response * 0.03f),
                    withAlpha(0xFF000000, 0.10f + response * 0.025f));
        }
    }

    static void drawModernTrack(float x, float y, float w, float h, float hoverAnim) {
        int top = mix(MODERN_TRACK_TOP, MODERN_CONTROL_HOVER_BOTTOM, hoverAnim * 0.28f);
        int bottom = mix(MODERN_TRACK_BOTTOM, MODERN_CONTROL_BOTTOM, hoverAnim * 0.18f);
        LayoutRender2D.roundedQuad(x, y, w, h, h * 0.5f, top, top, bottom, bottom);
    }

    static void drawModernAccent(float x, float y, float w, float h, float radius, float alpha) {
        LayoutRender2D.roundedQuad(x, y, w, h, radius,
                accentGradientStart(alpha), accentGradientEnd(alpha),
                accentGradientEnd(alpha), accentGradientStart(alpha));
    }

    static float scale() {
        syncTheme();
        return Math.max(0.25f, Math.min(4.0f, SettingRenderContext.scale()));
    }

    static float metric(float settings, float modules) {
        return (modules() ? modules : settings) * scale();
    }

    // One family (Onest) for every settings surface; the Inter split is gone.
    static TextRenderer fontRegular() {
        return BuiltinFontCatalog.ONEST_REGULAR.renderer(ClickGuiRenderer.getInterRegular());
    }

    static TextRenderer fontMedium() {
        return BuiltinFontCatalog.ONEST_MEDIUM.renderer(fontRegular());
    }

    static TextRenderer fontSemibold() {
        return BuiltinFontCatalog.ONEST_BOLD.renderer(fontMedium());
    }

    static TextRenderer fontLight() {
        return BuiltinFontCatalog.ONEST_LIGHT.renderer(fontRegular());
    }

    static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    static int mix(int from, int to, float t) {
        t = clamp01(t);
        int a = Math.round(((from >>> 24) & 0xFF) * (1f - t) + ((to >>> 24) & 0xFF) * t);
        int r = Math.round(((from >>> 16) & 0xFF) * (1f - t) + ((to >>> 16) & 0xFF) * t);
        int g = Math.round(((from >>> 8) & 0xFF) * (1f - t) + ((to >>> 8) & 0xFF) * t);
        int b = Math.round((from & 0xFF) * (1f - t) + (to & 0xFF) * t);
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    static int neutralize(int color, float chromaKeep) {
        chromaKeep = clamp01(chromaKeep);
        int a = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        int luma = Math.round(r * 0.2126f + g * 0.7152f + b * 0.0722f);
        int nr = Math.round(luma * (1f - chromaKeep) + r * chromaKeep);
        int ng = Math.round(luma * (1f - chromaKeep) + g * chromaKeep);
        int nb = Math.round(luma * (1f - chromaKeep) + b * chromaKeep);
        return (a << 24) | ((nr & 0xFF) << 16) | ((ng & 0xFF) << 8) | (nb & 0xFF);
    }

    static int withAlpha(int color, float alpha01) {
        int base = (color >>> 24) & 0xFF;
        int a = Math.round(base * clamp01(alpha01));
        return (color & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }

    static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    static int forceAlpha(int color, int alpha) {
        return withAlpha(color, alpha);
    }

    static String fit(TextRenderer font, String text, float size, float width) {
        return ClickGuiRenderer.fitText(font, text, size, Math.max(1f, width));
    }

    static float textHeight(TextRenderer font, float size) {
        return ClickGuiRenderer.textHeight(font, size);
    }

    static float textWidth(TextRenderer font, String text, float size) {
        return ClickGuiRenderer.textWidth(font, text == null ? "" : text, size);
    }

    static void matte(float x, float y, float w, float h, float radius, int color) {
        ClickGuiRenderer.drawRoundedRect(x, y, w, h, radius, color);
    }

    static int surfaceGradientStart(float alpha) {
        return withAlpha(SURFACE_GRADIENT_START, alpha);
    }

    static int surfaceGradientEnd(float alpha) {
        return withAlpha(SURFACE_GRADIENT_END, alpha);
    }

    static int cardGradientStart(float alpha) {
        return withAlpha(CARD_GRADIENT_START, alpha);
    }

    static int cardGradientEnd(float alpha) {
        return withAlpha(CARD_GRADIENT_END, alpha);
    }

    static int strokeGradientStart(float alpha) {
        return withAlpha(STROKE_GRADIENT_START, alpha);
    }

    static int strokeGradientEnd(float alpha) {
        return withAlpha(STROKE_GRADIENT_END, alpha);
    }

    static int accentGradientStart(float alpha) {
        return withAlpha(ACCENT_GRADIENT_START, alpha);
    }

    static int accentGradientEnd(float alpha) {
        return withAlpha(ACCENT_GRADIENT_END, alpha);
    }

    static void drawSurface(float x, float y, float w, float h, float radius, float alpha) {
        if (modernSettings()) {
            ClickGuiRenderer.drawRoundedRect(x, y, w, h, radius, withAlpha(SURFACE_SOFT, alpha));
            return;
        }
        if (!modules()) {
            SettingsGuiPalette palette = SettingsGuiPalette.current();
            SettingsGlassMaterial.control(
                    x, y, w, h, radius,
                    withAlpha(mix(SURFACE_SOFT, SURFACE_HOVER, 0.28f), alpha),
                    withAlpha(palette.glassEdgeSoft(), Math.min(1f, alpha * 0.80f))
            );
            return;
        }
        if (SURFACE_GRADIENT_ENABLED) {
            ClickGuiRenderer.drawRoundedRectGradient(x, y, w, h, radius, surfaceGradientStart(alpha), surfaceGradientEnd(alpha), SURFACE_GRADIENT_ANGLE);
        } else {
            ClickGuiRenderer.drawRoundedRect(x, y, w, h, radius, withAlpha(SURFACE_SOFT, alpha));
        }
    }

    static void drawCard(float x, float y, float w, float h, float radius, float alpha) {
        if (modernSettings()) {
            ClickGuiRenderer.drawRoundedRect(x, y, w, h, radius, withAlpha(SURFACE_SOFT, alpha));
            return;
        }
        if (!modules()) {
            SettingsGlassMaterial.selection(
                    x, y, w, h, radius,
                    withAlpha(SURFACE_SOFT, alpha * 0.84f),
                    withAlpha(SURFACE_HOVER, alpha)
            );
            return;
        }
        if (CARD_GRADIENT_ENABLED) {
            ClickGuiRenderer.drawRoundedRectGradient(x, y, w, h, radius, cardGradientStart(alpha), cardGradientEnd(alpha), CARD_GRADIENT_ANGLE);
        } else {
            ClickGuiRenderer.drawRoundedRect(x, y, w, h, radius, withAlpha(SURFACE_SOFT, alpha));
        }
    }

    static void drawAccent(float x, float y, float w, float h, float radius, float alpha) {
        ClickGuiRenderer.drawRoundedRectGradient(x, y, w, h, radius, accentGradientStart(alpha), accentGradientEnd(alpha), ACCENT_GRADIENT_ANGLE);
    }

    static void drawStroke(float x, float y, float w, float h, float radius, float thickness, float alpha) {
        if (STROKE_GRADIENT_ENABLED) {
            ClickGuiRenderer.drawRoundedRectStrokeGradient(x, y, w, h, radius, thickness, strokeGradientStart(alpha), strokeGradientEnd(alpha), STROKE_GRADIENT_ANGLE);
        } else {
            ClickGuiRenderer.drawRoundedRectStroke(x, y, w, h, radius, thickness, withAlpha(TEXT_FAINT, alpha));
        }
    }

    static void checkIcon(float x, float y, float size, float activeAnim, float hoverAnim) {
        float active = clamp01(activeAnim);
        float hover = clamp01(hoverAnim);
        float alpha = 0.12f + active * 0.88f;
        int tint = mix(CHECK_COLOR, 0xFFFFFFFF, hover * 0.35f);
        int argb = withAlpha(tint, alpha);
        Renderer2D.COLOR.svg("check", x, y, size, size, SvgRenderOptions.overrideColor(argb));
    }
}
