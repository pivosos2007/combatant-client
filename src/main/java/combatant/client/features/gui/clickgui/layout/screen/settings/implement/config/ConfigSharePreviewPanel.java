/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.config;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.features.gui.clickgui.util.ClickGuiMath;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.helpers.SystemCursor;

import java.util.ArrayList;
import java.util.List;

/**
 * Modal confirm card used by share-code import: title, a few detail lines and a row of buttons, drawn over a
 * dimmed area. While open it owns all clicks in the area so nothing underneath changes by accident.
 */
public final class ConfigSharePreviewPanel {
    public record Button(String id, String label) {
    }

    private final List<Line> lines = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();
    private final List<ButtonHit> buttonHits = new ArrayList<>();
    private String title = "";
    private boolean open;
    private float anim;
    private float cardX;
    private float cardY;
    private float cardW;
    private float cardH;
    private float areaX;
    private float areaY;
    private float areaW;
    private float areaH;

    private record Line(String label, String value, boolean warn) {
    }

    private record ButtonHit(Button button, float x, float y, float w, float h) {
    }

    public void open(String title, List<Button> buttons) {
        this.title = title == null ? "" : title;
        this.lines.clear();
        this.buttons.clear();
        this.buttons.addAll(buttons);
        this.open = true;
        this.anim = 0f;
    }

    /** Appends a detail row. {@code warn} tints the value as a caution. */
    public ConfigSharePreviewPanel line(String label, String value, boolean warn) {
        lines.add(new Line(label == null ? "" : label, value == null ? "" : value, warn));
        return this;
    }

    public void close() {
        open = false;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isActive() {
        return open || anim > 0.01f;
    }

    public void render(float x, float y, float w, float h, float mx, float my, float scale, SettingsGuiPalette palette) {
        float target = open ? 1f : 0f;
        anim = AnimationUtility.snap(AnimationUtility.approach(anim, target, AnimationUtility.deltaTime(), 16f), target, 0.01f);
        if (anim <= 0.01f) {
            buttonHits.clear();
            return;
        }
        areaX = x;
        areaY = y;
        areaW = w;
        areaH = h;
        float a = AnimationUtility.easeOutCubic(anim);

        LayoutRender2D.roundedQuad(x, y, w, h, 5f * scale,
                argb(150f * a, 0x000000), argb(150f * a, 0x000000), argb(170f * a, 0x000000), argb(170f * a, 0x000000));

        float pad = 8f * scale;
        float lineH = 9.4f * scale;
        float btnH = 15f * scale;
        cardW = Math.min(w - 16f * scale, 200f * scale);
        cardH = pad + 11f * scale + lines.size() * lineH + 6f * scale + btnH + pad;
        cardX = x + (w - cardW) * 0.5f;
        cardY = y + (h - cardH) * 0.5f + (1f - a) * 5f * scale;

        LayoutRender2D.roundedSoftShadow(cardX, cardY + 0.6f * scale, cardW, cardH, 5f * scale, 8f * scale, 0.05f,
                SettingsGuiPalette.withAlpha(palette.menuShadow(), Math.round(110f * a)));
        ClickGuiRenderer.drawBlur(cardX, cardY, cardW, cardH, 5f * scale, palette.panelBlurTint(), 0.9f * a);
        int bgA = SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.panelBgLeft(), palette.moduleCardTop(), 0.55f), Math.round(236f * a));
        int bgB = SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.panelBgRight(), palette.moduleCardBottom(), 0.55f), Math.round(236f * a));
        LayoutRender2D.roundedQuad(cardX, cardY, cardW, cardH, 5f * scale, bgA, bgA, bgB, bgB);
        LayoutRender2D.roundedStrokeQuad(cardX, cardY, cardW, cardH, 5f * scale, 0.5f * scale,
                SettingsGuiPalette.withAlpha(palette.moduleDividerStart(), Math.round(170f * a)),
                SettingsGuiPalette.withAlpha(palette.moduleDividerEnd(), Math.round(190f * a)),
                SettingsGuiPalette.withAlpha(palette.moduleDividerEnd(), Math.round(150f * a)),
                SettingsGuiPalette.withAlpha(palette.moduleDividerStart(), Math.round(170f * a)));

        float innerW = cardW - pad * 2f;
        float cy = cardY + pad;
        float titleSize = 8.8f * scale;
        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterMedium(),
                ClickGuiRenderer.fitText(ClickGuiRenderer.getInterMedium(), title, titleSize, innerW),
                cardX + pad, cy, titleSize, SettingsGuiPalette.withAlpha(palette.moduleTitleText(), Math.round(255f * a)), false);
        cy += 11f * scale;

        float rowSize = 7.1f * scale;
        float labelW = Math.min(innerW * 0.42f,
                maxLabelWidth(rowSize) + 5f * scale);
        for (Line line : lines) {
            ClickGuiRenderer.drawText(ClickGuiRenderer.getInterRegular(), line.label(), cardX + pad, cy, rowSize,
                    SettingsGuiPalette.withAlpha(palette.moduleDescriptionIcon(), Math.round(235f * a)), false);
            int valueColor = line.warn() ? SettingsGuiPalette.mix(palette.moduleDescriptionText(), 0xFFE0A040, 0.7f) : palette.moduleDescriptionText();
            ClickGuiRenderer.drawText(ClickGuiRenderer.getInterRegular(),
                    ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), line.value(), rowSize, innerW - labelW),
                    cardX + pad + labelW, cy, rowSize, SettingsGuiPalette.withAlpha(valueColor, Math.round(255f * a)), false);
            cy += lineH;
        }

        buttonHits.clear();
        if (buttons.isEmpty()) return;
        float gap = 4f * scale;
        float btnW = (innerW - gap * (buttons.size() - 1)) / buttons.size();
        float by = cardY + cardH - pad - btnH;
        for (int i = 0; i < buttons.size(); i++) {
            Button button = buttons.get(i);
            float bx = cardX + pad + i * (btnW + gap);
            boolean hover = ClickGuiMath.insideRect(mx, my, bx, by, btnW, btnH);
            boolean primary = i == 0;
            float hv = hover ? 1f : 0f;
            int c1 = primary
                    ? SettingsGuiPalette.mix(palette.panelPillActive(), palette.menuCategorySelectedLeft(), 0.30f + hv * 0.25f)
                    : SettingsGuiPalette.mix(palette.moduleCardTop(), palette.menuCategoryHoverLeft(), 0.18f + hv * 0.34f);
            int c2 = primary
                    ? SettingsGuiPalette.mix(palette.panelPillActive(), palette.menuCategorySelectedRight(), 0.26f + hv * 0.25f)
                    : SettingsGuiPalette.mix(palette.moduleCardTopStrong(), palette.menuCategoryHoverRight(), 0.14f + hv * 0.30f);
            LayoutRender2D.roundedQuad(bx, by, btnW, btnH, 3.6f * scale,
                    SettingsGuiPalette.withAlpha(c1, Math.round(230f * a)), SettingsGuiPalette.withAlpha(c2, Math.round(230f * a)),
                    SettingsGuiPalette.withAlpha(c2, Math.round(230f * a)), SettingsGuiPalette.withAlpha(c1, Math.round(230f * a)));
            float textSize = 7f * scale;
            String label = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterMedium(), button.label(), textSize, btnW - 4f * scale);
            float tw = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterMedium(), label, textSize);
            float th = ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterMedium(), textSize);
            ClickGuiRenderer.drawText(ClickGuiRenderer.getInterMedium(), label, bx + (btnW - tw) * 0.5f, by + (btnH - th) * 0.5f,
                    textSize, SettingsGuiPalette.withAlpha(palette.panelText(), Math.round(255f * a)), false);
            buttonHits.add(new ButtonHit(button, bx, by, btnW, btnH));
            if (hover) SystemCursor.set(SystemCursor.CursorType.HAND);
        }
    }

    /**
     * Handles a press inside the dimmed area. Returns the pressed button id, an empty string when the press was
     * consumed without hitting a button, or {@code null} when the panel is closed or the press is outside the area.
     */
    public String click(float mx, float my) {
        if (!open) return null;
        if (!ClickGuiMath.insideRect(mx, my, areaX, areaY, areaW, areaH)) return null;
        for (ButtonHit hit : buttonHits) {
            if (ClickGuiMath.insideRect(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) return hit.button().id();
        }
        return "";
    }

    private float maxLabelWidth(float size) {
        float max = 0f;
        for (Line line : lines) {
            max = Math.max(max, ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterRegular(), line.label(), size));
        }
        return max;
    }

    private static int argb(float alpha, int rgb) {
        return (Math.round(Math.max(0f, Math.min(255f, alpha))) << 24) | (rgb & 0xFFFFFF);
    }
}
