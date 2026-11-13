/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import combatant.client.render.engine.renderer.ui.draw.LayoutRender2D;

import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.SystemCursor;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

enum SettingRendererBridge {
    ;
    private static final String ICON_WARN = "L";

    private static final Map<Setting, UnavailableMarquee> UNAVAILABLE_MARQUEES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final class UnavailableMarquee {
        String reason;
        float width;
        long entered;
        boolean hovered;
    }

    private static void drawUnavailableReason(Setting setting, String reason, TextRenderer font,
                                              float x, float y, float size, float maxWidth,
                                              int color, boolean hovered) {
        float available = Math.max(1.0f, maxWidth);
        if (LayoutRender2D.textWidth(font, reason, size) <= available + 0.5f) {
            UNAVAILABLE_MARQUEES.remove(setting);
            LayoutRender2D.drawText(font, reason, x, y, size, color, false);
            return;
        }
        if (!hovered) {
            UNAVAILABLE_MARQUEES.remove(setting);
            LayoutRender2D.drawMarqueeText(font, reason, x, y, size, available, color,
                    -1f, 29f * UnifiedSettingsSkin.scale(),
                    18f * UnifiedSettingsSkin.scale(), 12f * UnifiedSettingsSkin.scale());
            return;
        }
        UnavailableMarquee state = UNAVAILABLE_MARQUEES.computeIfAbsent(setting, ignored -> new UnavailableMarquee());
        long now = System.nanoTime();
        if (!state.hovered || !reason.equals(state.reason) || Math.abs(state.width - available) > 0.5f) {
            state.entered = now;
            state.reason = reason;
            state.width = available;
            state.hovered = true;
        }
        LayoutRender2D.drawMarqueeText(font, reason, x, y, size, available, color,
                Math.max(0f, (now - state.entered) * 1e-9f),
                29f * UnifiedSettingsSkin.scale(),
                18f * UnifiedSettingsSkin.scale(), 12f * UnifiedSettingsSkin.scale());
    }

    static void render(BooleanSetting setting, float x, float y, float w, float mx, float my) {
        SettingsImplementations.active().render(setting, x, y, w, mx, my);
        renderUnavailable(setting, x, y, w, mx, my);
    }

    static void mouseClicked(BooleanSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        SettingsImplementations.active().mouseClicked(setting, mx, my, button);
    }

    static float getHeight(BooleanSetting setting) {
        return SettingsImplementations.active().height(setting);
    }

    static <N extends Number> void render(SliderSetting<N> setting, float x, float y, float w, float mx, float my) {
        SettingsImplementations.active().render(setting, x, y, w, mx, my);
        renderUnavailable(setting, x, y, w, mx, my);
    }

    static <N extends Number> void mouseClicked(SliderSetting<N> setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        SettingsImplementations.active().mouseClicked(setting, mx, my, button);
    }

    static <N extends Number> void mouseReleased(SliderSetting<N> setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        SettingsImplementations.active().mouseReleased(setting, mx, my, button);
    }

    static <N extends Number> void mouseClickedOutside(SliderSetting<N> setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        SettingsImplementations.active().mouseClickedOutside(setting, mx, my, button);
    }

    static <N extends Number> boolean keyPressed(SliderSetting<N> setting, int keyCode, int scanCode, int modifiers) {
        if (!setting.isAvailable()) return false;
        return SettingsImplementations.active().keyPressed(setting, keyCode, scanCode, modifiers);
    }

    static <N extends Number> boolean charTyped(SliderSetting<N> setting, char chr, int modifiers) {
        if (!setting.isAvailable()) return false;
        return SettingsImplementations.active().charTyped(setting, chr, modifiers);
    }

    static <N extends Number> float getHeight(SliderSetting<N> setting) {
        return SettingsImplementations.active().height(setting);
    }

    static void render(ColorSetting setting, float x, float y, float w, float mx, float my) {
        SettingsImplementations.active().render(setting, x, y, w, mx, my);
        renderUnavailable(setting, x, y, w, mx, my);
    }

    static void mouseClicked(ColorSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        SettingsImplementations.active().mouseClicked(setting, mx, my, button);
    }

    static void mouseReleased(ColorSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        SettingsImplementations.active().mouseReleased(setting, mx, my, button);
    }

    static void mouseClickedOutside(ColorSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        SettingsImplementations.active().mouseClickedOutside(setting, mx, my, button);
    }

    static boolean keyPressed(ColorSetting setting, int keyCode, int scanCode, int modifiers) {
        if (!setting.isAvailable()) return false;
        return SettingsImplementations.active().keyPressed(setting, keyCode, scanCode, modifiers);
    }

    static boolean charTyped(ColorSetting setting, char chr, int modifiers) {
        if (!setting.isAvailable()) return false;
        return SettingsImplementations.active().charTyped(setting, chr, modifiers);
    }

    static boolean mouseScrolled(ColorSetting setting, double mx, double my, double amount) {
        if (!setting.isAvailable()) return false;
        return SettingsImplementations.active().mouseScrolled(setting, mx, my, amount);
    }

    static float getHeight(ColorSetting setting) {
        return SettingsImplementations.active().height(setting);
    }

    static void render(TextSetting setting, float x, float y, float w, float mx, float my) {
        SettingsImplementations.active().render(setting, x, y, w, mx, my);
        renderUnavailable(setting, x, y, w, mx, my);
    }

    static void mouseClicked(TextSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        SettingsImplementations.active().mouseClicked(setting, mx, my, button);
    }

    static void mouseClickedOutside(TextSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        SettingsImplementations.active().mouseClickedOutside(setting, mx, my, button);
    }

    static boolean keyPressed(TextSetting setting, int keyCode, int scanCode, int modifiers) {
        if (!setting.isAvailable()) return false;
        return SettingsImplementations.active().keyPressed(setting, keyCode, scanCode, modifiers);
    }

    static boolean charTyped(TextSetting setting, char chr, int modifiers) {
        if (!setting.isAvailable()) return false;
        return SettingsImplementations.active().charTyped(setting, chr, modifiers);
    }

    static float getHeight(TextSetting setting) {
        return SettingsImplementations.active().height(setting);
    }

    static void render(TextListSetting setting, float x, float y, float w, float mx, float my) {
        SettingsImplementations.active().render(setting, x, y, w, mx, my);
        renderUnavailable(setting, x, y, w, mx, my);
    }

    static void mouseClicked(TextListSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        SettingsImplementations.active().mouseClicked(setting, mx, my, button);
    }

    static void mouseClickedOutside(TextListSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        SettingsImplementations.active().mouseClickedOutside(setting, mx, my, button);
    }

    static boolean keyPressed(TextListSetting setting, int keyCode, int scanCode, int modifiers) {
        if (!setting.isAvailable()) return false;
        return SettingsImplementations.active().keyPressed(setting, keyCode, scanCode, modifiers);
    }

    static boolean charTyped(TextListSetting setting, char chr, int modifiers) {
        if (!setting.isAvailable()) return false;
        return SettingsImplementations.active().charTyped(setting, chr, modifiers);
    }

    static float getHeight(TextListSetting setting) {
        return SettingsImplementations.active().height(setting);
    }

    static void render(GroupSetting setting, float x, float y, float w, float mx, float my) {
        SettingsImplementations.active().render(setting, x, y, w, mx, my);
        renderUnavailable(setting, x, y, w, mx, my);
    }

    static void mouseClicked(GroupSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        SettingsImplementations.active().mouseClicked(setting, mx, my, button);
    }

    static float getHeight(GroupSetting setting) {
        return SettingsImplementations.active().height(setting);
    }

    static void render(ModeSetting setting, float x, float y, float w, float mx, float my) {
        SettingsImplementations.active().render(setting, x, y, w, mx, my);
        renderUnavailable(setting, x, y, w, mx, my);
    }

    static void mouseClicked(ModeSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        SettingsImplementations.active().mouseClicked(setting, mx, my, button);
    }

    static float getHeight(ModeSetting setting) {
        return SettingsImplementations.active().height(setting);
    }

    static void render(KeyBindSetting setting, float x, float y, float w, float mx, float my) {
        SettingsImplementations.active().render(setting, x, y, w, mx, my);
        renderUnavailable(setting, x, y, w, mx, my);
    }

    static void mouseClicked(KeyBindSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        SettingsImplementations.active().mouseClicked(setting, mx, my, button);
    }

    static float getHeight(KeyBindSetting setting) {
        return SettingsImplementations.active().height(setting);
    }

    static void render(FunctionBindSetting setting, float x, float y, float w, float mx, float my) {
        SettingsImplementations.active().render(setting, x, y, w, mx, my);
        renderUnavailable(setting, x, y, w, mx, my);
    }

    static void mouseClicked(FunctionBindSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        SettingsImplementations.active().mouseClicked(setting, mx, my, button);
    }

    static float getHeight(FunctionBindSetting setting) {
        return SettingsImplementations.active().height(setting);
    }

    private static void renderUnavailable(Setting setting, float x, float y, float w, float mx, float my) {
        String reason = setting.getUnavailableReason();
        if (reason.isEmpty()) {
            UNAVAILABLE_MARQUEES.remove(setting);
            return;
        }

        float scale = UnifiedSettingsSkin.scale();
        float h = Math.max(1.0f, setting.getHeight());
        boolean hovered = UnifiedSettingsSkin.inside(mx, my, x, y, w, h);
        if (hovered) {
            SystemCursor.set(SystemCursor.CursorType.NOT_ALLOWED);
        }

        if (UnifiedSettingsSkin.modernSettings()) {
            float inset = UnifiedSettingsSkin.modernMetric(1.0f, 1.0f);
            float radius = UnifiedSettingsSkin.controlRadius();
            float veilX = x + inset;
            float veilY = y + inset;
            float veilW = Math.max(1.0f, w - inset * 2.0f);
            float veilH = Math.max(1.0f, h - inset * 2.0f);
            LayoutRender2D.drawRoundedRect(veilX, veilY, veilW, veilH, radius, hovered ? 0x71070809 : 0x59070809);

            TextRenderer textFont = UnifiedSettingsSkin.fontMedium();
            float textSize = UnifiedSettingsSkin.modernMetric(11.0f, 11.0f);
            float iconSize = UnifiedSettingsSkin.modernMetric(13.0f, 13.0f);
            TextRenderer iconFont = BuiltinFontCatalog.ICONS.renderer(textFont);
            float rightPad = UnifiedSettingsSkin.modernMetric(7.0f, 7.0f);
            float iconW = LayoutRender2D.textWidth(iconFont, ICON_WARN, iconSize);
            float reasonMax = Math.max(24.0f, Math.min(w * 0.42f, w - iconW - rightPad * 3.0f));
            float reasonWidth = Math.min(reasonMax, LayoutRender2D.textWidth(textFont, reason, textSize));
            float ty = y + (h - LayoutRender2D.textHeight(textFont, textSize)) * 0.5f;
            float tx = x + w - rightPad - reasonWidth;
            float iy = y + (h - LayoutRender2D.textHeight(iconFont, iconSize)) * 0.5f;
            float ix = tx - iconW - UnifiedSettingsSkin.modernMetric(4.0f, 4.0f);
            int color = UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_MUTED, hovered ? 245 : 205);
            LayoutRender2D.drawText(iconFont, ICON_WARN, ix, iy, iconSize, color, false);
            drawUnavailableReason(setting, reason, textFont, tx, ty, textSize, reasonWidth, color, hovered);
            return;
        }

        float inset = UnifiedSettingsSkin.metric(2.0f, 2.0f);
        float radius = UnifiedSettingsSkin.metric(7.0f, 4.5f);
        int veil = hovered ? 0x7A050607 : 0x63050607;
        LayoutRender2D.drawRoundedRect(x + inset, y + inset, Math.max(1.0f, w - inset * 2.0f), Math.max(1.0f, h - inset * 2.0f), radius, veil);
        LayoutRender2D.drawRoundedRectStroke(
                x + inset,
                y + inset,
                Math.max(1.0f, w - inset * 2.0f),
                Math.max(1.0f, h - inset * 2.0f),
                radius,
                UnifiedSettingsSkin.metric(0.7f, 0.45f),
                UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.accentGradientStart(1.0f), hovered ? 145 : 98)
        );

        TextRenderer iconFont = BuiltinFontCatalog.ICONS.renderer(UnifiedSettingsSkin.fontMedium());
        TextRenderer textFont = UnifiedSettingsSkin.fontMedium();
        float iconSize = UnifiedSettingsSkin.metric(16.0f, 7.4f);
        float textSize = UnifiedSettingsSkin.metric(11.5f, 5.7f);
        float pillH = Math.min(h - inset * 3.0f, UnifiedSettingsSkin.metric(20.0f, 11.5f));
        float pillY = y + h - pillH - UnifiedSettingsSkin.metric(4.0f, 2.8f);
        float pillX = x + UnifiedSettingsSkin.metric(7.0f, 4.0f);
        float pillW = Math.max(1.0f, w - UnifiedSettingsSkin.metric(14.0f, 8.0f));
        int pillA = UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.SURFACE_HOVER, hovered ? 170 : 132);
        int pillB = UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.ACCENT_SOFT, hovered ? 112 : 78);
        LayoutRender2D.drawRoundedRectGradient(pillX, pillY, pillW, pillH, pillH * 0.5f, pillA, pillB, UnifiedSettingsSkin.ACCENT_GRADIENT_ANGLE);

        float iconY = pillY + (pillH - LayoutRender2D.textHeight(iconFont, iconSize)) * 0.5f - UnifiedSettingsSkin.metric(0.2f, 0.1f);
        int iconColor = UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.accentGradientStart(1.0f), hovered ? 255 : 220);
        LayoutRender2D.drawText(iconFont, ICON_WARN, pillX + UnifiedSettingsSkin.metric(5.0f, 3.0f), iconY, iconSize, iconColor, false);

        float textX = pillX + UnifiedSettingsSkin.metric(22.0f, 11.0f);
        float textMax = Math.max(1.0f, pillX + pillW - textX - UnifiedSettingsSkin.metric(5.0f, 3.0f));
        float textY = pillY + (pillH - LayoutRender2D.textHeight(textFont, textSize)) * 0.5f - UnifiedSettingsSkin.metric(0.2f, 0.1f);
        int textColor = UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_PRIMARY, hovered ? 245 : 215);
        drawUnavailableReason(setting, reason, textFont, textX, textY, textSize, textMax, textColor, hovered);
    }
}
