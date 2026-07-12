/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import combatant.client.config.values.ColorValue;
import combatant.client.config.values.ConfigValue;
import combatant.client.util.text.TextSelection;

public class ColorSetting extends Setting {

    private final ColorValue value;
    private final UiState ui = new UiState();

    public ColorSetting(String name, ColorValue value) {
        super(name, asConfigValue(value));
        this.value = value;
    }

    private static ConfigValue<?> asConfigValue(ColorValue value) {
        return value instanceof ConfigValue<?> configValue ? configValue : null;
    }

    ColorValue value() {
        return value;
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
    public boolean mouseScrolled(double mx, double my, double amount) {
        return SettingRendererBridge.mouseScrolled(this, mx, my, amount);
    }

    @Override
    public float getHeight() {
        return SettingRendererBridge.getHeight(this);
    }

    static final class UiState {
        final TextSelection hexSelection = new TextSelection();
        boolean editing = false;
        boolean showRgbBars = false;
        boolean rainbow = false;
        boolean sbFocused = false;
        boolean hFocused = false;
        boolean aFocused = false;
        boolean rFocused = false;
        boolean gFocused = false;
        boolean bFocused = false;
        boolean aRgbFocused = false;
        float hue = 0f;
        float saturation = 1f;
        float brightness = 1f;
        int alpha = 255;
        int red = 255;
        int green = 255;
        int blue = 255;
        int lastArgb = 0;
        float previewGlowAnim = 0f;
        float expandAnim = 0f;
        float sliderHoverAnim = 0f;
        float rnbHoverAnim = 0f;
        float rnbOffAnim = 0f;
        float rgbExpandAnim = 0f;
        float lastX, lastY, lastW;
        float popupX, popupY, popupW, popupH;
        float moduleHoverAnim;
        float squareX, squareY, squareW, squareH;
        float hueX, hueY, hueW, hueH;
        float alphaX, alphaY, alphaW, alphaH;
        float rX, rY, rW, rH;
        float gX, gY, gW, gH;
        float bX, bY, bW, bH;
        float aX, aY, aW, aH;
        float rgbBottom;
        float sliderX, sliderY, sliderW, sliderH;
        float rnbX, rnbY, rnbW, rnbH;
        boolean hexFocused = false;
        String hexBuffer = "";
        int hexCursor = 0;
        float hexAnim = 0f;
        float hexErrorAnim = 0f;
        float hexCursorBlink = 0f;
        float presetHoverAnim = 0f;
        float savePresetHoverAnim = 0f;
        float deletePresetHoverAnim = 0f;
        boolean presetsExpanded = false;
        float presetsExpandAnim = 0f;
        float presetsScroll = 0f;
        float presetsMaxScroll = 0f;
        float presetsContentH = 0f;
        float presetsViewportH = 0f;
        float presetsToggleHoverAnim = 0f;
        float presetsScrollbarHoverAnim = 0f;
        boolean presetsScrollbarDragging = false;
        float presetsScrollbarDragOffset = 0f;
        float hexX, hexY, hexW, hexH;
        float presetX, presetY, presetW, presetH;
        float presetListX, presetListY, presetListW, presetListH;
        float presetToggleX, presetToggleY, presetToggleW, presetToggleH;
        float presetScrollbarX, presetScrollbarY, presetScrollbarW, presetScrollbarH;
        float presetScrollbarHandleY, presetScrollbarHandleH;
        float savePresetX, savePresetY, savePresetW, savePresetH;
        float deletePresetX, deletePresetY, deletePresetW, deletePresetH;
    }
}
