/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import combatant.client.config.values.StringValue;
import combatant.client.util.text.TextSelection;

public class TextSetting extends Setting implements TextEditorOwner {

    private final StringValue value;
    private final UiState ui = new UiState();

    public TextSetting(String name, StringValue value) {
        super(name, value);
        this.value = value;
    }

    private static String normalizeSingleLine(String text) {
        if (text == null) return "";
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        return normalized.replace('\n', ' ');
    }

    UiState ui() {
        return ui;
    }

    @Override
    public void render(float x, float y, float w, float mx, float my) {
        SettingRendererBridge.render(this, x, y, w, mx, my);
    }

    @Override
    public void mouseClicked(double mx, double my, int button) {
        SettingRendererBridge.mouseClicked(this, mx, my, button);
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

    @Override
    public void applyEditorText(String rawText) {
        String next = normalizeSingleLine(rawText);
        value.set(next);
        if (getParent() != null) getParent().saveConfig();
    }

    @Override
    public String getEditorText() {
        String current = value.get();
        return current == null ? "" : current;
    }

    @Override
    public boolean isSingleLine() {
        return true;
    }

    static final class UiState {
        final TextSelection editSelection = new TextSelection();
        float fieldX, fieldY, fieldW, fieldH;
        float textX, textY, textSize;
        float textScroll;
        float hoverAnim;
        float focusAnim;
        float errorAnim;
        float cursorBlink;
        boolean editing;
        String editBuffer = "";
        int editCursor;
    }
}
