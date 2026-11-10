/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.theme;

import combatant.client.features.gui.clickgui.util.ClickGuiI18n;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.LayoutRender2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;

import java.util.Locale;

/** A non-interactive, rect-anchored tooltip using the Spectrum 2 surface and tip assets. */
final class SpectrumThemeSwatchTooltip {
    private static final float HOVER_DELAY = 0.14f;
    private static final int SURFACE = 0xFF333333;
    private static final int TITLE = 0xFFF4F4F4;
    private static final int MUTED = 0xFFB9B9B9;

    record Anchor(String themeId, int slot, String label,
                  float x, float y, float w, float h,
                  Themes.GradientSpec gradient, int fallback) {
        boolean sameSwatch(Anchor other) {
            return other != null && slot == other.slot && themeId.equals(other.themeId);
        }
    }

    private Anchor displayed;
    private float hoverTime;
    private float opacity;
    private TextRenderer regular;
    private TextRenderer semibold;
    private long fontGeneration = -1;

    void reset() {
        displayed = null;
        hoverTime = 0f;
        opacity = 0f;
    }

    void render(Anchor hovered, float panelX, float panelY, float panelW, float panelH, float scale) {
        float dt = AnimationUtility.deltaTime();
        if (hovered != null) {
            if (!hovered.sameSwatch(displayed)) {
                displayed = hovered;
                hoverTime = 0f;
                opacity = 0f;
            } else {
                displayed = hovered;
                hoverTime += dt;
            }
        } else {
            hoverTime = 0f;
        }

        float target = hovered != null && hoverTime >= HOVER_DELAY ? 1f : 0f;
        opacity = AnimationUtility.approach(opacity, target, dt, target > opacity ? 16f : 20f);
        opacity = AnimationUtility.snap(opacity, target, 0.01f);
        if (displayed == null || opacity <= 0.01f) {
            if (hovered == null) displayed = null;
            return;
        }

        refreshFonts();
        draw(displayed, panelX, panelY, panelW, panelH, scale, opacity);
    }

    private void draw(Anchor anchor, float panelX, float panelY, float panelW, float panelH,
                      float scale, float alpha) {
        float width = Math.min(160f * scale, panelW - 12f * scale);
        float height = 39f * scale;
        float tipWidth = 16f * scale;
        float tipHeight = 8f * scale;
        float gap = 5f * scale;
        float minX = panelX + 6f * scale;
        float maxX = panelX + panelW - 6f * scale - width;
        float center = anchor.x + anchor.w * 0.5f;
        float x = AnimationUtility.clamp(center - width * 0.5f, minX, Math.max(minX, maxX));

        float panelTop = panelY + 6f * scale;
        float panelBottom = panelY + panelH - 6f * scale;
        boolean above = anchor.y - height - tipHeight - gap >= panelTop;
        float y = above ? anchor.y - height - tipHeight - gap : anchor.y + anchor.h + tipHeight + gap;
        y = AnimationUtility.clamp(y, panelTop, Math.max(panelTop, panelBottom - height));
        float tipX = AnimationUtility.clamp(center - tipWidth * 0.5f,
                x + 7f * scale, x + width - tipWidth - 7f * scale);
        float tipY = above ? y + height : y - tipHeight;

        int fill = faded(SURFACE, alpha);
        LayoutRender2D.roundedSoftShadow(x, y, width, height, 6f * scale,
                7f * scale, 0f, faded(0xFF000000, 0.42f * alpha));
        LayoutRender2D.rounded(x, y, width, height, 6f * scale, fill);
        Renderer2D.COLOR.svg(above ? "spectrum2/tooltip-tip-down" : "spectrum2/tooltip-tip-up",
                tipX, tipY, tipWidth, tipHeight, SvgRenderOptions.overrideColor(fill));

        boolean gradientEnabled = anchor.gradient != null && anchor.gradient.enabled();
        String status = gradientEnabled
                ? ClickGuiI18n.tr("clickgui.themes.swatch.gradient", "Gradient") + "  " +
                  String.format(Locale.ROOT, "%.0f\u00b0", anchor.gradient.angleDeg())
                : ClickGuiI18n.tr("clickgui.themes.swatch.solid", "Solid");
        String colors = gradientEnabled
                ? colorHex(anchor.gradient.start()) + "  \u2192  " + colorHex(anchor.gradient.end())
                : colorHex(anchor.fallback);

        float titleSize = 9.4f * scale;
        float statusSize = 8f * scale;
        float colorsSize = 8.6f * scale;
        float pad = 10f * scale;
        float statusW = LayoutRender2D.textWidth(regular, status, statusSize);
        float titleW = Math.max(10f * scale, width - 2f * pad - statusW - 7f * scale);
        String title = LayoutRender2D.fitText(semibold, anchor.label, titleSize, titleW);

        LayoutRender2D.drawText(semibold, title, x + pad, y + 6.2f * scale,
                titleSize, faded(TITLE, alpha), false);
        LayoutRender2D.drawText(regular, status, x + width - pad - statusW, y + 7.3f * scale,
                statusSize, faded(MUTED, alpha), false);
        LayoutRender2D.drawText(regular,
                LayoutRender2D.fitText(regular, colors, colorsSize, width - pad * 2f),
                x + pad, y + 22f * scale, colorsSize, faded(TITLE, alpha), false);
    }

    private void refreshFonts() {
        long generation = Fonts.generation();
        if (regular != null && semibold != null && fontGeneration == generation) return;
        regular = Fonts.renderer("SourceSans3", FontInfo.Type.Regular, BuiltinFontCatalog.ONEST_REGULAR.renderer());
        semibold = Fonts.renderer("SourceSans3Semibold", FontInfo.Type.Regular, BuiltinFontCatalog.ONEST_MEDIUM.renderer());
        fontGeneration = generation;
    }

    private static String colorHex(int argb) {
        return String.format(Locale.ROOT, "#%08X", argb);
    }

    private static int faded(int color, float alpha) {
        return (Math.round(255f * AnimationUtility.clamp01(alpha)) << 24) | (color & 0x00FFFFFF);
    }
}
