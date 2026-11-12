/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.util;

import combatant.client.render.engine.renderer.ui.draw.LayoutRender2D;

import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.text.Fonts;
import combatant.client.features.theme.Theme;
import combatant.client.render.engine.text.TextRenderer;

public enum ClickGuiHintOverlay {
    ;
    private static final float TEXT_ALPHA = 0.84f;
    private static final float SHADOW_ALPHA = 0.36f;

    private static TextRenderer comfortaa;
    private static long fontGeneration = Long.MIN_VALUE;

    public static void renderBottomLeft(float areaX, float areaY, float areaW, float areaH, float scale, float alpha, String... lines) {
        float ui = AnimationUtility.clamp(scale, 1f, 2f);
        render(areaX, areaY, areaW, areaH, 7.1f * ui, 2.25f * ui, 12f * ui,
                0.7f * ui, 0.8f * ui, alpha, lines);
    }

    public static void renderBottomLeft(float areaX, float areaY, float areaW, float areaH,
                                        float alpha, String... lines) {
        render(areaX, areaY, areaW, areaH, 14.2f, 4.5f, 24f, 1.4f, 1.6f, alpha, lines);
    }

    private static void render(float areaX, float areaY, float areaW, float areaH,
                               float size, float lineGap, float padding, float shadowX, float shadowY,
                               float alpha, String... lines) {
        if (lines == null || lines.length == 0 || areaW <= 0.5f || areaH <= 0.5f) return;
        TextRenderer font = comfortaa();
        float a = AnimationUtility.clamp01(alpha);
        if (a <= 0.001f) return;
        float lineH = LayoutRender2D.textHeight(font, size);

        int visible = 0;
        for (String line : lines) {
            if (line != null && !line.isBlank()) visible++;
        }
        if (visible == 0) return;

        float totalH = visible * lineH + Math.max(0, visible - 1) * lineGap;
        float x = areaX + padding;
        float y = areaY + areaH - padding - totalH;
        int text = withAlpha(Theme.theme().textPrimary() | 0xFF000000, TEXT_ALPHA * a);
        int shadow = withAlpha(0xFF000000, SHADOW_ALPHA * a);
        float maxW = Math.max(1f, areaW - padding * 2f);

        int index = 0;
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;

            float lineY = y + index * (lineH + lineGap);
            String fitted = LayoutRender2D.fitText(font, line, size, maxW);
            LayoutRender2D.drawText(font, fitted, x + shadowX, lineY + shadowY, size, shadow, false);
            LayoutRender2D.drawText(font, fitted, x, lineY, size, text, false);
            index++;
        }
    }

    private static TextRenderer comfortaa() {
        long generation = Fonts.generation();
        if (fontGeneration != generation) {
            comfortaa = null;
            fontGeneration = generation;
        }
        if (comfortaa == null) {
            comfortaa = BuiltinFontCatalog.ONEST_REGULAR.renderer(BuiltinFontCatalog.ONEST_REGULAR.renderer());
        }
        return comfortaa;
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(((color >>> 24) & 0xFF) * alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
