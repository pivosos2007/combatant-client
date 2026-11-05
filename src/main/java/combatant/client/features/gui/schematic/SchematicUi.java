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
import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

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

    static final float BODY = 0.82f;
    static final float SMALL = 0.72f;
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
        bold = BuiltinFontCatalog.ONEST_BOLD.renderer(TextRenderer.get());
        body = BuiltinFontCatalog.ONEST_MEDIUM.renderer(bold);
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

    void text(String text, float x, float y, float size, int argb, boolean strong) {
        if (text == null || text.isEmpty()) return;
        TextRenderer renderer = strong ? bold : body;
        if (renderer == null) return;
        renderer.setAlpha(1.0);
        renderer.begin(size, false, false);
        renderer.render(text, x, y, new RenderColor(argb), false);
        renderer.end();
    }

    float width(String text, float size, boolean strong) {
        TextRenderer renderer = strong ? bold : body;
        if (renderer == null || text == null || text.isEmpty()) return 0f;
        renderer.begin(size, true, false);
        float w = (float) renderer.getWidth(text, false);
        renderer.end();
        return w;
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
