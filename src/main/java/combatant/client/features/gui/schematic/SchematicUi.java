/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic;

import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.theme.Theme;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * Drawing primitives for the schematic menu. The look follows the ClickGUI Modules columns: separate frosted
 * liquid-glass panels with a soft veil, a titled header with a hairline split, filled pill buttons and pill tabs
 * tinted by the active theme, and the ClickGUI scrollbar colours.
 */
final class SchematicUi {

    static final int GOOD = 0xFF66BB6A;
    static final int BAD = 0xFFEF5350;
    static final int WARN = 0xFFFF9800;
    /** Redstone components in the materials list and the layer view. */
    static final int REDSTONE = 0xFFD8342B;

    // Re-read from the active theme every frame by syncTheme(); the values here are the dark default.
    static int TEXT = 0xFFEEF2F8;
    static int MUTED = 0xFF8A93A3;
    static int FAINT = 0xFF5C6474;
    static int ACCENT = 0xFF4FC3F7;
    static int SURFACE = 0xFF151922;
    static int SURFACE_HOVER = 0xFF1E2532;
    static int STROKE = 0xFF2A3140;
    static int PANEL_TOP = 0xFF12161E;
    static int PANEL_BOTTOM = 0xFF0B0E14;
    static int WELL = 0xFF0A0D12;
    /** Tint of the veil over the glass and of filled controls; same derivation as ClickGUI's panelBgGlassDark. */
    static int GLASS_RGB = 0xFF151922;
    /** Hover fill colour; same derivation as ClickGUI's rowHover. */
    static int HOVER_RGB = 0xFF1E2532;
    /** Hairline panel outline (ClickGUI panelStroke) and separators (ClickGUI split). */
    static int PANEL_STROKE = 0x4C2A3140;
    static int SPLIT = 0x282A3140;
    private static int scrollTrackA = 0x4B232323;
    private static int scrollTrackB = 0x5F2D2D2D;
    private static int scrollHandleA = 0x4B2C2C2C;
    private static int scrollHandleB = 0x5F656565;

    static final float TITLE = 1.0f;
    static final float BODY = 0.82f;
    static final float SMALL = 0.72f;
    static final float TINY = 0.62f;

    private final Map<String, String> ellipsized = new HashMap<>();
    private TextRenderer bold;
    private TextRenderer body;

    static void syncTheme() {
        Themes.Theme t = Theme.theme();
        if (t == null) return;
        TEXT = opaque(t.textPrimary());
        MUTED = mix(opaque(t.textMuted()), TEXT, 0.28f);
        ACCENT = opaque(t.accent());
        PANEL_BOTTOM = opaque(t.windowBg());
        PANEL_TOP = mix(opaque(t.windowHeader()), PANEL_BOTTOM, 0.35f);
        SURFACE = mix(opaque(t.surface()), PANEL_BOTTOM, 0.25f);
        SURFACE_HOVER = mix(opaque(t.surfaceHover()), ACCENT, 0.08f);
        STROKE = mix(opaque(t.strokeSoft()), ACCENT, 0.14f);
        FAINT = mix(MUTED, SURFACE, 0.22f);
        WELL = mix(PANEL_BOTTOM, 0xFF000000, 0.42f);
        GLASS_RGB = mix(opaque(t.surface()), PANEL_BOTTOM, 0.35f);
        HOVER_RGB = mix(opaque(t.surfaceHover()), ACCENT, 0.16f);
        PANEL_STROKE = withAlpha(mix(opaque(t.strokeSoft()), ACCENT, 0.22f), 112);
        SPLIT = withAlpha(mix(opaque(t.strokeSoft()), ACCENT, 0.16f), 68);
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        scrollTrackA = palette.moduleScrollTrackA();
        scrollTrackB = palette.moduleScrollTrackB();
        scrollHandleA = palette.moduleScrollHandleA();
        scrollHandleB = palette.moduleScrollHandleB();
    }

    private static int opaque(int argb) {
        return argb | 0xFF000000;
    }

    void init() {
        bold = BuiltinFontCatalog.INTER_BOLD.renderer(TextRenderer.get());
        body = BuiltinFontCatalog.INTER_MEDIUM.renderer(bold);
    }

    void clearCaches() {
        ellipsized.clear();
    }

    static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | (Mth.clamp(alpha, 0, 255) << 24);
    }

    static int scaleAlpha(int argb, float factor) {
        return withAlpha(argb, Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(factor, 0f, 1f)));
    }

    static int mix(int a, int b, float t) {
        return HudRenderUtil.mixColor(a, b, Mth.clamp(t, 0f, 1f));
    }

    static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    /** Frosted liquid-glass panel, drawn exactly like a ClickGUI Modules column (HUD_LARGE glass plus a light veil). */
    void panel(float x, float y, float w, float h, float radius, float alpha) {
        if (alpha <= 0.001f || w <= 1f || h <= 1f) return;
        float glassAlpha = Mth.clamp(alpha, 0f, 1f);
        Renderer2D.COLOR.roundedRectSoftShadow(x, y, w, h, radius, 20f, 0.022f, withAlpha(0x000000, Math.round(alpha * 96f)));
        Renderer2D.COLOR.withLiquidGlassBlurProfile(Renderer2D.BlurQuality.LIQUID_GLASS, 1.38f,
                () -> Renderer2D.COLOR.liquidGlassRect(x, y, w, h, radius, 0xFFFFFFFF, glassAlpha, 1.0f,
                        Renderer2D.LiquidGlassPreset.HUD_LARGE));
        int veilTop = withAlpha(mix(GLASS_RGB, 0xFF000000, 0.28f), Math.round(alpha * 124f));
        int veilBottom = withAlpha(mix(GLASS_RGB, 0xFF000000, 0.46f), Math.round(alpha * 148f));
        Renderer2D.COLOR.roundedRectGradientQuad(x, y, w, h, radius, 1f, veilTop, veilTop, veilBottom, veilBottom);
        Renderer2D.COLOR.roundedRectStroke(x, y, w, h, radius, 1.1f, scaleAlpha(PANEL_STROKE, alpha));
    }

    /** Panel header: cube icon, strong title and a hairline split below. Returns the x just after the title. */
    float header(String title, float x, float y, float w, float h, float alpha) {
        float ix = x + 22f;
        SchematicViewport.drawCubeIcon(ix, y + h * 0.5f + 5f, 7.5f, mix(SURFACE_HOVER, ACCENT, 0.6f), alpha);
        float tx = x + 40f;
        text(title, tx, y + (h - height(TITLE)) * 0.5f, TITLE, scaleAlpha(TEXT, alpha), true);
        Renderer2D.COLOR.quad(x + 14f, y + h, w - 28f, 1f, scaleAlpha(SPLIT, alpha));
        return tx + width(title, TITLE, true);
    }

    /** Inner card (list body, strip): a darker veil, no outline. */
    void section(float x, float y, float w, float h, float alpha) {
        int fill = withAlpha(mix(GLASS_RGB, 0xFF000000, 0.45f), Math.round(alpha * 132f));
        Renderer2D.COLOR.roundedRect(x, y, w, h, 12f, fill);
        Renderer2D.COLOR.roundedRectStroke(x, y, w, h, 12f, 1f, scaleAlpha(SPLIT, alpha));
    }

    /** Recessed area such as the 3D viewport or a progress track. */
    void well(float x, float y, float w, float h, float radius, float alpha) {
        int top = withAlpha(WELL, Math.round(alpha * 212f));
        int bottom = withAlpha(mix(WELL, ACCENT, 0.05f), Math.round(alpha * 220f));
        Renderer2D.COLOR.roundedRectGradientQuad(x, y, w, h, radius, 1f, top, top, bottom, bottom);
        Renderer2D.COLOR.roundedRectStroke(x, y, w, h, radius, 1f, scaleAlpha(SPLIT, alpha));
    }

    /** Small rounded label used for dimensions, counts and other row metadata. */
    float chip(float x, float y, String label, int tint, float alpha) {
        float w = width(label, TINY, false) + 14f;
        float h = height(TINY) + 6f;
        Renderer2D.COLOR.roundedRect(x, y, w, h, h * 0.5f, scaleAlpha(withAlpha(mix(SURFACE, tint, 0.28f), 215), alpha));
        Renderer2D.COLOR.roundedRectStroke(x, y, w, h, h * 0.5f, 0.8f, scaleAlpha(withAlpha(tint, 95), alpha));
        text(label, x + 7f, y + 3f, TINY, scaleAlpha(mix(TEXT, tint, 0.35f), alpha), false);
        return w;
    }

    /** Recessed pill that holds a group of {@link #tab} segments. */
    void segmentTrack(float x, float y, float w, float h, float alpha) {
        Renderer2D.COLOR.roundedRect(x, y, w, h, h * 0.5f, withAlpha(mix(GLASS_RGB, 0xFF000000, 0.4f), Math.round(alpha * 110f)));
    }

    /** Pill segment label: the sliding selection pill is drawn once per group by {@link #segmentPill}; this adds hover and text. */
    void tab(float x, float y, float w, float h, String label, float hover, float press, boolean active, boolean enabled, float alpha) {
        float inset = 3f;
        float sx = x + inset, sy = y + inset, sw = w - inset * 2f, sh = h - inset * 2f;
        if (!active && enabled && hover > 0.01f) {
            Renderer2D.COLOR.roundedRect(sx, sy, sw, sh, sh * 0.5f, withAlpha(HOVER_RGB, Math.round(alpha * hover * (90f + press * 40f))));
        }
        int text = !enabled ? scaleAlpha(FAINT, 0.8f) : active ? TEXT : mix(MUTED, TEXT, 0.25f + hover * 0.75f);
        String shown = ellipsize(label, SMALL, sw - 12f);
        textCentered(shown, x + w * 0.5f, y + (h - height(SMALL)) * 0.5f, SMALL, scaleAlpha(text, alpha), active);
    }

    /** Accent pill that slides between segments (idea from Anubis' animated segmented control). */
    void segmentPill(float x, float y, float w, float h, float alpha) {
        float inset = 3f;
        Renderer2D.COLOR.roundedRect(x + inset, y + inset, w - inset * 2f, h - inset * 2f, (h - inset * 2f) * 0.5f,
                withAlpha(mix(GLASS_RGB, ACCENT, 0.55f), Math.round(alpha * 185f)));
    }

    /**
     * Filled pill button. Idle is a faint glass fill, hover lifts it, {@code active} tints it with the accent and
     * {@code primary} is the single accented call to action. Disabled is clearly dimmed.
     */
    void button(float x, float y, float w, float h, String label, float hover, float press, boolean active, boolean enabled,
                boolean primary, float alpha) {
        float lift = enabled ? hover : 0f;
        float sink = press * 1.5f;
        float r = Math.min(h * 0.5f, 18f);
        int fill;
        int text;
        int stroke = 0;
        if (!enabled) {
            fill = withAlpha(mix(GLASS_RGB, 0xFF000000, 0.3f), 52);
            text = scaleAlpha(FAINT, 0.8f);
        } else if (primary) {
            int base = mix(ACCENT, 0xFF000000, 0.16f - lift * 0.08f);
            fill = withAlpha(base, 240);
            text = luminance(base) > 0.62f ? 0xFF10141A : 0xFFFFFFFF;
            stroke = withAlpha(mix(ACCENT, 0xFFFFFFFF, 0.25f), Math.round(140f + lift * 60f));
            Renderer2D.COLOR.roundedRectSoftShadow(x, y + 1f, w, h, r, 12f, 0.022f * alpha,
                    withAlpha(ACCENT, Math.round(alpha * (42f + lift * 44f))));
        } else if (active) {
            fill = withAlpha(mix(GLASS_RGB, ACCENT, 0.52f), Math.round(170f + lift * 45f));
            text = TEXT;
            stroke = withAlpha(ACCENT, 130);
        } else {
            fill = withAlpha(mix(SURFACE, HOVER_RGB, 0.35f + lift * 0.65f), Math.round(142f + lift * 64f));
            text = mix(MUTED, TEXT, 0.65f + lift * 0.35f);
            stroke = withAlpha(STROKE, Math.round(72f + lift * 55f));
        }
        Renderer2D.COLOR.roundedRect(x, y + sink, w, h - sink, r, scaleAlpha(fill, alpha));
        if (stroke != 0) {
            Renderer2D.COLOR.roundedRectStroke(x, y + sink, w, h - sink, r, 0.9f, scaleAlpha(stroke, alpha));
        }
        float size = BODY * 0.95f;
        String shown = ellipsize(label, size, w - 20f);
        textCentered(shown, x + w * 0.5f, y + sink * 0.5f + (h - height(size)) * 0.5f, size, scaleAlpha(text, alpha), primary);
    }

    /** ClickGUI scrollbar: a 3px gradient track with a gradient handle, in the ClickGUI palette. */
    void scrollbar(float x, float y, float h, int first, int visible, int total, float alpha) {
        float w = 3.5f;
        float thumbH = Math.max(22f, h * visible / total);
        float thumbY = y + (h - thumbH) * first / Math.max(1, total - visible);
        Renderer2D.COLOR.roundedRectGradientQuad(x, y, w, h, w * 0.5f, 1f,
                scaleAlpha(scrollTrackA, alpha), scaleAlpha(scrollTrackB, alpha), scaleAlpha(scrollTrackB, alpha), scaleAlpha(scrollTrackA, alpha));
        Renderer2D.COLOR.roundedRectGradientQuad(x, thumbY, w, thumbH, w * 0.5f, 1f,
                scaleAlpha(scrollHandleA, alpha), scaleAlpha(scrollHandleB, alpha), scaleAlpha(scrollHandleB, alpha), scaleAlpha(scrollHandleA, alpha));
    }

    private static float luminance(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        return 0.299f * r + 0.587f * g + 0.114f * b;
    }

    void text(String text, float x, float y, float size, int argb, boolean strong) {
        if (text == null || text.isEmpty()) return;
        TextRenderer renderer = strong ? bold : body;
        if (renderer == null) return;
        renderer.setAlpha(1.0);
        renderer.begin(size, false, false);
        renderer.render(text, x, y, new RenderColor(argb), false);
        renderer.end();
    }

    void textCentered(String text, float centerX, float y, float size, int argb, boolean strong) {
        text(text, centerX - width(text, size, strong) * 0.5f, y, size, argb, strong);
    }

    void textRight(String text, float right, float y, float size, int argb, boolean strong) {
        text(text, right - width(text, size, strong), y, size, argb, strong);
    }

    float width(String text, float size, boolean strong) {
        TextRenderer renderer = strong ? bold : body;
        if (renderer == null || text == null || text.isEmpty()) return 0f;
        renderer.begin(size, true, false);
        float w = (float) renderer.getWidth(text, false);
        renderer.end();
        return w;
    }

    float height(float size) {
        if (body == null) return size * 20f;
        body.begin(size, true, false);
        float h = (float) body.getHeight();
        body.end();
        return h;
    }

    /** Ellipsized strings are cached per (text, size, width) because measuring glyph runs every frame adds up. */
    String ellipsize(String text, float size, float maxWidth) {
        if (text == null || text.isEmpty()) return "";
        String key = text + '\u0000' + Math.round(size * 100f) + '\u0000' + Math.round(maxWidth);
        String cached = ellipsized.get(key);
        if (cached != null) return cached;
        String out = text;
        if (width(text, size, false) > maxWidth) {
            out = text;
            while (out.length() > 1 && width(out + "...", size, false) > maxWidth) {
                out = out.substring(0, out.length() - 1);
            }
            out = out + "...";
        }
        if (ellipsized.size() > 2048) ellipsized.clear();
        ellipsized.put(key, out);
        return out;
    }
}
