/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import combatant.client.config.values.NumberValue;
import combatant.client.util.text.TextSelection;

public class SliderSetting<N extends Number> extends Setting {

    private final NumberValue<N> value;
    private final UiState ui = new UiState();

    public SliderSetting(String name, NumberValue<N> value) {
        super(name, value);
        this.value = value;
    }

    NumberValue<N> value() {
        return value;
    }

    UiState ui() {
        return ui;
    }

    // ==================== CONFIG ==================== //

    @Override
    public Object save() {
        return value == null ? null : value.toJson();
    }

    @Override
    public void load(Object json) {
        if (value != null) value.fromJson(json);
    }

    // ========================= UI ========================= //

    @Override
    public void render(float x, float y, float w, float mx, float my) {
        SettingRendererBridge.render(this, x, y, w, mx, my);
    }

    @Override
    public void mouseClicked(double mx, double my, int button) {
        SettingRendererBridge.mouseClicked(this, mx, my, button);
    }

    @Override
    public void mouseReleased(double mx, double my, int button) {
        SettingRendererBridge.mouseReleased(this, mx, my, button);
    }

    @Override
    public void mouseClickedOutside(double mx, double my, int button) {
        SettingRendererBridge.mouseClickedOutside(this, mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return SettingRendererBridge.keyPressed(this, keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return SettingRendererBridge.charTyped(this, chr, modifiers);
    }

    @Override
    public float getHeight() {
        return SettingRendererBridge.getHeight(this);
    }

    static final class UiState {
        final TextSelection editSelection = new TextSelection();
        boolean dragging;
        float lastX, lastY, lastW;
        float anim = 0f;
        float hoverAnim = 0f;
        float valueHoverAnim = 0f;
        float editAnim = 0f;
        float errorAnim = 0f;
        float cursorBlink = 0f;
        boolean editing = false;
        String editBuffer = "";
        int editCursor = 0;
        float inputHoverAnim = 0f;
        float inputFocusGlowAnim = 0f;
        float inputPressAnim = 0f;
        float valueX, valueY, valueW, valueH;
        float valueTextX, valueTextY, valueTextSize, valueTextPad;
    }
}
