/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.other;

import combatant.client.util.text.SingleLineTextInput;

import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.ClickGuiSearch;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.features.gui.clickgui.util.ClickGuiMath;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ScissorFunction;

import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.theme.Theme;

public final class SearchComponent {
    private static final float CORNER_RADIUS = 3f;
    private static final float STROKE_WIDTH = 0.5f;
    private static final float DIVIDER_X = 65.5f;
    private static final float BLUR_STRENGTH = 135f / 255f;

    public void render(float x, float y, float w, float h) {
        render(x, y, w, h, CLICK_GUI_MODEL);
    }

    public void render(float x, float y, float w, float h, Model model) {
        Model state = model != null ? model : CLICK_GUI_MODEL;
        boolean typing = state.focused();
        String text = state.text() == null ? "" : state.text();
        float scale = w / 80f;

        SettingsGuiPalette palette = SettingsGuiPalette.current();
        float radius = CORNER_RADIUS * scale;
        ClickGuiRenderer.drawBlur(x, y, w, h, radius, palette.panelBlurTint(), BLUR_STRENGTH);
        LayoutRender2D.roundedQuad(
                x, y, w, h, radius,
                palette.panelBgLeft(),
                palette.panelBgRight(),
                palette.panelBgRight(),
                palette.panelBgLeft()
        );
        LayoutRender2D.roundedStroke(x, y, w, h, radius, STROKE_WIDTH * scale,
                typing ? palette.glassEdgeStrong() : palette.panelStroke());

        float dividerX = x + DIVIDER_X * scale;
        LayoutRender2D.rect(dividerX, y + 4f * scale, 0.5f * scale, h - 8f * scale, palette.panelDivider());

        float tx = x + 4f * scale;
        float ts = 12f * scale;
        TextRenderer font = ClickGuiRenderer.getInterRegular();
        String display = text.isEmpty() && !typing
                ? ClickGuiRenderer.fitText(font, state.placeholder(), ts, Math.max(1f, DIVIDER_X * scale - 6f * scale))
                : text;
        float th = ClickGuiRenderer.textHeight(font, ts);
        float ty = y + (h - th) * 0.5f - 0.5f * scale;
        boolean clipped = ScissorFunction.pushRaw(x + scale, y, w - 16f * scale, h);
        SingleLineTextInput editor = state.editor();
        if (editor != null && (!text.isEmpty() || typing)) {
            editor.render(tx, ty, w - 17f * scale, h, font, ts,
                    palette.panelText(), LayoutRender2D.alpha(Theme.theme().accent() | 0xFF000000, 0.5f), typing);
        } else {
            ClickGuiRenderer.drawText(font, display, tx, ty, ts,
                    typing ? palette.panelText() : palette.panelMuted(), false);
        }
        if (clipped) ScissorFunction.pop();

        TextRenderer icons = BuiltinFontCatalog.ICONS.renderer(ClickGuiRenderer.getInterRegular());
        float iconSize = 8f * scale;
        String icon = "s";
        float iconW = ClickGuiRenderer.textWidth(icons, icon, iconSize);
        float iconH = ClickGuiRenderer.textHeight(icons, iconSize);
        float iconX = dividerX + (w - (dividerX - x) - iconW) * 0.5f;
        float iconY = y + (h - iconH) * 0.5f - 0.35f * scale;
        int iconColor = typing ? palette.panelText() : palette.panelMuted();
        ClickGuiRenderer.drawText(icons, icon, iconX, iconY, iconSize, iconColor, false);
    }

    public boolean click(float x, float y, float w, float h, float mx, float my, int button) {
        return click(x, y, w, h, mx, my, button, CLICK_GUI_MODEL);
    }

    public boolean click(float x, float y, float w, float h,
                         float mx, float my, int button, Model model) {
        if (button != combatant.client.input.InputKeys.MOUSE_LEFT) return false;
        Model state = model != null ? model : CLICK_GUI_MODEL;
        boolean inside = ClickGuiMath.insideRect(mx, my, x, y, w, h);
        if (inside) {
            state.setFocused(true);
            if (state.editor() != null) {
                float scale = w / 80f;
                state.editor().layout(x + 4f * scale, w - 17f * scale, ClickGuiRenderer.getInterRegular(), 12f * scale);
                var window = net.minecraft.client.Minecraft.getInstance().getWindow();
                state.editor().beginDrag(mx,
                combatant.client.input.CombatantInput.state().isPressed(
                        combatant.client.input.InputDevice.KEYBOARD, combatant.client.input.InputKeys.LEFT_SHIFT)
                                || combatant.client.input.CombatantInput.state().isPressed(
                        combatant.client.input.InputDevice.KEYBOARD, combatant.client.input.InputKeys.RIGHT_SHIFT));
            }
            return true;
        }
        if (state.editor() != null) state.editor().unfocus();
        state.setFocused(false);
        return false;
    }

    public interface Model {
        boolean focused();
        String text();
        default String placeholder() { return "Search"; }
        void setFocused(boolean focused);
        default SingleLineTextInput editor() { return null; }
    }

    private static final Model CLICK_GUI_MODEL = new Model() {
        @Override public boolean focused() { return ClickGuiSearch.isActive(); }
        @Override public String text() { return ClickGuiSearch.getText(); }
        @Override public SingleLineTextInput editor() { return ClickGuiSearch.editor(); }
        @Override public void setFocused(boolean focused) {
            if (focused) ClickGuiSearch.setActive(true);
            else ClickGuiSearch.unfocus();
        }
    };
}
