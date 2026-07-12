/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.SystemCursor;

enum SettingRendererBridge {
    ;
    private static final String ICON_WARN = "L";

    static void render(BooleanSetting setting, float x, float y, float w, float mx, float my) {
        UnifiedSettingRenderer.render(setting, x, y, w, mx, my);
        renderUnavailable(setting, x, y, w, mx, my);
    }

    static void mouseClicked(BooleanSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        UnifiedSettingRenderer.mouseClicked(setting, mx, my, button);
    }

    static float getHeight(BooleanSetting setting) {
        return UnifiedSettingRenderer.getHeight(setting);
    }

    static <N extends Number> void render(SliderSetting<N> setting, float x, float y, float w, float mx, float my) {
        UnifiedSettingRenderer.render(setting, x, y, w, mx, my);
        renderUnavailable(setting, x, y, w, mx, my);
    }

    static <N extends Number> void mouseClicked(SliderSetting<N> setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        UnifiedSettingRenderer.mouseClicked(setting, mx, my, button);
    }

    static <N extends Number> void mouseReleased(SliderSetting<N> setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        UnifiedSettingRenderer.mouseReleased(setting, mx, my, button);
    }

    static <N extends Number> void mouseClickedOutside(SliderSetting<N> setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        UnifiedSettingRenderer.mouseClickedOutside(setting, mx, my, button);
    }

    static <N extends Number> boolean keyPressed(SliderSetting<N> setting, int keyCode, int scanCode, int modifiers) {
        if (!setting.isAvailable()) return false;
        return UnifiedSettingRenderer.keyPressed(setting, keyCode, scanCode, modifiers);
    }

    static <N extends Number> boolean charTyped(SliderSetting<N> setting, char chr, int modifiers) {
        if (!setting.isAvailable()) return false;
        return UnifiedSettingRenderer.charTyped(setting, chr, modifiers);
    }

    static <N extends Number> float getHeight(SliderSetting<N> setting) {
        return UnifiedSettingRenderer.getHeight(setting);
    }

    static void render(ColorSetting setting, float x, float y, float w, float mx, float my) {
        UnifiedSettingRenderer.render(setting, x, y, w, mx, my);
        renderUnavailable(setting, x, y, w, mx, my);
    }

    static void mouseClicked(ColorSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        UnifiedSettingRenderer.mouseClicked(setting, mx, my, button);
    }

    static void mouseReleased(ColorSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        UnifiedSettingRenderer.mouseReleased(setting, mx, my, button);
    }

    static void mouseClickedOutside(ColorSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        UnifiedSettingRenderer.mouseClickedOutside(setting, mx, my, button);
    }

    static boolean keyPressed(ColorSetting setting, int keyCode, int scanCode, int modifiers) {
        if (!setting.isAvailable()) return false;
        return UnifiedSettingRenderer.keyPressed(setting, keyCode, scanCode, modifiers);
    }

    static boolean charTyped(ColorSetting setting, char chr, int modifiers) {
        if (!setting.isAvailable()) return false;
        return UnifiedSettingRenderer.charTyped(setting, chr, modifiers);
    }

    static boolean mouseScrolled(ColorSetting setting, double mx, double my, double amount) {
        if (!setting.isAvailable()) return false;
        return UnifiedSettingRenderer.mouseScrolled(setting, mx, my, amount);
    }

    static float getHeight(ColorSetting setting) {
        return UnifiedSettingRenderer.getHeight(setting);
    }

    static void render(TextSetting setting, float x, float y, float w, float mx, float my) {
        UnifiedSettingRenderer.render(setting, x, y, w, mx, my);
        renderUnavailable(setting, x, y, w, mx, my);
    }

    static void mouseClicked(TextSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        UnifiedSettingRenderer.mouseClicked(setting, mx, my, button);
    }

    static void mouseClickedOutside(TextSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        UnifiedSettingRenderer.mouseClickedOutside(setting, mx, my, button);
    }

    static boolean keyPressed(TextSetting setting, int keyCode, int scanCode, int modifiers) {
        if (!setting.isAvailable()) return false;
        return UnifiedSettingRenderer.keyPressed(setting, keyCode, scanCode, modifiers);
    }

    static boolean charTyped(TextSetting setting, char chr, int modifiers) {
        if (!setting.isAvailable()) return false;
        return UnifiedSettingRenderer.charTyped(setting, chr, modifiers);
    }

    static float getHeight(TextSetting setting) {
        return UnifiedSettingRenderer.getHeight(setting);
    }

    static void render(TextListSetting setting, float x, float y, float w, float mx, float my) {
        UnifiedSettingRenderer.render(setting, x, y, w, mx, my);
        renderUnavailable(setting, x, y, w, mx, my);
    }

    static void mouseClicked(TextListSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        UnifiedSettingRenderer.mouseClicked(setting, mx, my, button);
    }

    static void mouseClickedOutside(TextListSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        UnifiedSettingRenderer.mouseClickedOutside(setting, mx, my, button);
    }

    static boolean keyPressed(TextListSetting setting, int keyCode, int scanCode, int modifiers) {
        if (!setting.isAvailable()) return false;
        return UnifiedSettingRenderer.keyPressed(setting, keyCode, scanCode, modifiers);
    }

    static boolean charTyped(TextListSetting setting, char chr, int modifiers) {
        if (!setting.isAvailable()) return false;
        return UnifiedSettingRenderer.charTyped(setting, chr, modifiers);
    }

    static float getHeight(TextListSetting setting) {
        return UnifiedSettingRenderer.getHeight(setting);
    }

    static void render(GroupSetting setting, float x, float y, float w, float mx, float my) {
        UnifiedSettingRenderer.render(setting, x, y, w, mx, my);
        renderUnavailable(setting, x, y, w, mx, my);
    }

    static void mouseClicked(GroupSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        UnifiedSettingRenderer.mouseClicked(setting, mx, my, button);
    }

    static float getHeight(GroupSetting setting) {
        return UnifiedSettingRenderer.getHeight(setting);
    }

    static void render(ModeSetting setting, float x, float y, float w, float mx, float my) {
        UnifiedSettingRenderer.render(setting, x, y, w, mx, my);
        renderUnavailable(setting, x, y, w, mx, my);
    }

    static void mouseClicked(ModeSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        UnifiedSettingRenderer.mouseClicked(setting, mx, my, button);
    }

    static float getHeight(ModeSetting setting) {
        return UnifiedSettingRenderer.getHeight(setting);
    }

    static void render(KeyBindSetting setting, float x, float y, float w, float mx, float my) {
        UnifiedSettingRenderer.render(setting, x, y, w, mx, my);
        renderUnavailable(setting, x, y, w, mx, my);
    }

    static void mouseClicked(KeyBindSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        UnifiedSettingRenderer.mouseClicked(setting, mx, my, button);
    }

    static float getHeight(KeyBindSetting setting) {
        return UnifiedSettingRenderer.getHeight(setting);
    }

    static void render(FunctionBindSetting setting, float x, float y, float w, float mx, float my) {
        UnifiedSettingRenderer.render(setting, x, y, w, mx, my);
        renderUnavailable(setting, x, y, w, mx, my);
    }

    static void mouseClicked(FunctionBindSetting setting, double mx, double my, int button) {
        if (!setting.isAvailable()) return;
        UnifiedSettingRenderer.mouseClicked(setting, mx, my, button);
    }

    static float getHeight(FunctionBindSetting setting) {
        return UnifiedSettingRenderer.getHeight(setting);
    }

    private static void renderUnavailable(Setting setting, float x, float y, float w, float mx, float my) {
        String reason = setting.getUnavailableReason();
        if (reason.isEmpty()) return;

        float scale = UnifiedSettingsSkin.scale();
        float h = Math.max(1.0f, setting.getHeight());
        boolean hovered = UnifiedSettingsSkin.inside(mx, my, x, y, w, h);
        if (hovered) {
            SystemCursor.set(SystemCursor.CursorType.NOT_ALLOWED);
        }

        float inset = UnifiedSettingsSkin.metric(2.0f, 2.0f);
        float radius = UnifiedSettingsSkin.metric(7.0f, 4.5f);
        int veil = hovered ? 0x7A050607 : 0x63050607;
        ClickGuiRenderer.drawRoundedRect(x + inset, y + inset, Math.max(1.0f, w - inset * 2.0f), Math.max(1.0f, h - inset * 2.0f), radius, veil);
        ClickGuiRenderer.drawRoundedRectStroke(
                x + inset,
                y + inset,
                Math.max(1.0f, w - inset * 2.0f),
                Math.max(1.0f, h - inset * 2.0f),
                radius,
                UnifiedSettingsSkin.metric(0.7f, 0.45f),
                UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.accentGradientStart(1.0f), hovered ? 145 : 98)
        );

        TextRenderer iconFont = Fonts.renderer("Icons", FontInfo.Type.Regular, UnifiedSettingsSkin.fontMedium());
        TextRenderer textFont = UnifiedSettingsSkin.fontMedium();
        float iconSize = UnifiedSettingsSkin.metric(16.0f, 7.4f);
        float textSize = UnifiedSettingsSkin.metric(11.5f, 5.7f);
        float pillH = Math.min(h - inset * 3.0f, UnifiedSettingsSkin.metric(20.0f, 11.5f));
        float pillY = y + h - pillH - UnifiedSettingsSkin.metric(4.0f, 2.8f);
        float pillX = x + UnifiedSettingsSkin.metric(7.0f, 4.0f);
        float pillW = Math.max(1.0f, w - UnifiedSettingsSkin.metric(14.0f, 8.0f));
        int pillA = UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.SURFACE_HOVER, hovered ? 170 : 132);
        int pillB = UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.ACCENT_SOFT, hovered ? 112 : 78);
        ClickGuiRenderer.drawRoundedRectGradient(pillX, pillY, pillW, pillH, pillH * 0.5f, pillA, pillB, UnifiedSettingsSkin.ACCENT_GRADIENT_ANGLE);

        float iconY = pillY + (pillH - ClickGuiRenderer.textHeight(iconFont, iconSize)) * 0.5f - UnifiedSettingsSkin.metric(0.2f, 0.1f);
        int iconColor = UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.accentGradientStart(1.0f), hovered ? 255 : 220);
        ClickGuiRenderer.drawText(iconFont, ICON_WARN, pillX + UnifiedSettingsSkin.metric(5.0f, 3.0f), iconY, iconSize, iconColor, false);

        float textX = pillX + UnifiedSettingsSkin.metric(22.0f, 11.0f);
        float textMax = Math.max(1.0f, pillX + pillW - textX - UnifiedSettingsSkin.metric(5.0f, 3.0f));
        String fitted = UnifiedSettingsSkin.fit(textFont, reason, textSize, textMax);
        float textY = pillY + (pillH - ClickGuiRenderer.textHeight(textFont, textSize)) * 0.5f - UnifiedSettingsSkin.metric(0.2f, 0.1f);
        int textColor = UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_PRIMARY, hovered ? 245 : 215);
        ClickGuiRenderer.drawText(textFont, fitted, textX, textY, textSize, textColor, false);
    }
}
