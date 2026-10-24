/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.util;

import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
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
        if (lines == null || lines.length == 0 || areaW <= 0.5f || areaH <= 0.5f) return;

        TextRenderer font = comfortaa();
        float a = AnimationUtility.clamp(alpha, 0.0f, 1.0f);
        if (a <= 0.001f) return;

        float ui = AnimationUtility.clamp(scale, 1.0f, 2.0f);
        float size = 7.1f * ui;
        float lineGap = 2.25f * ui;
        float padX = 12.0f * ui;
        float padY = 12.0f * ui;
        float lineH = ClickGuiRenderer.textHeight(font, size);

        int visible = 0;
        for (String line : lines) {
            if (line != null && !line.isBlank()) visible++;
        }
        if (visible == 0) return;

        float totalH = visible * lineH + Math.max(0, visible - 1) * lineGap;
        float x = areaX + padX;
        float y = areaY + areaH - padY - totalH;
        int text = withAlpha(Theme.theme().textPrimary() | 0xFF000000, TEXT_ALPHA * a);
        int shadow = withAlpha(0xFF000000, SHADOW_ALPHA * a);
        float maxW = Math.max(1f, areaW - padX * 2f);

        int index = 0;
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;

            float lineY = y + index * (lineH + lineGap);
            String fitted = ClickGuiRenderer.fitText(font, line, size, maxW);
            ClickGuiRenderer.drawText(font, fitted, x + 0.7f * ui, lineY + 0.8f * ui, size, shadow, false);
            ClickGuiRenderer.drawText(font, fitted, x, lineY, size, text, false);
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
            comfortaa = BuiltinFontCatalog.ONEST_REGULAR.renderer(ClickGuiRenderer.getInterRegular());
        }
        return comfortaa;
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(((color >>> 24) & 0xFF) * alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
