/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.clickgui.settings;

/** One native backend, with independently selectable geometry and appearance presets. */
final class JavaSettingsImplementation implements SettingsImplementation {
    private final String id;
    private final SettingRenderSurface forcedSurface;

    JavaSettingsImplementation(String id, SettingRenderSurface forcedSurface) {
        this.id = id;
        this.forcedSurface = forcedSurface;
    }

    @Override
    public String id() { return id; }

    private SettingRenderContext.Scope scope() {
        if (forcedSurface == null) return null;
        return SettingRenderContext.push(
                forcedSurface,
                SettingRenderContext.scale(), SettingRenderContext.overlayHost());
    }

    @Override
    public void render(Setting setting, float x, float y, float w, float mx, float my) {
        try (SettingRenderContext.Scope ignored = scope()) {
            if (setting instanceof BooleanSetting control) UnifiedSettingRenderer.render(control, x, y, w, mx, my);
            else if (setting instanceof SliderSetting<?> control) UnifiedSettingRenderer.render(control, x, y, w, mx, my);
            else if (setting instanceof ColorSetting control) UnifiedSettingRenderer.render(control, x, y, w, mx, my);
            else if (setting instanceof TextSetting control) UnifiedSettingRenderer.render(control, x, y, w, mx, my);
            else if (setting instanceof TextListSetting control) UnifiedSettingRenderer.render(control, x, y, w, mx, my);
            else if (setting instanceof GroupSetting control) UnifiedSettingRenderer.render(control, x, y, w, mx, my);
            else if (setting instanceof ModeSetting control) UnifiedSettingRenderer.render(control, x, y, w, mx, my);
            else if (setting instanceof KeyBindSetting control) UnifiedSettingRenderer.render(control, x, y, w, mx, my);
            else if (setting instanceof FunctionBindSetting control) UnifiedSettingRenderer.render(control, x, y, w, mx, my);
        }
    }

    @Override
    public float height(Setting setting) {
        try (SettingRenderContext.Scope ignored = scope()) {
            if (setting instanceof BooleanSetting control) return UnifiedSettingRenderer.getHeight(control);
            else if (setting instanceof SliderSetting<?> control) return UnifiedSettingRenderer.getHeight(control);
            else if (setting instanceof ColorSetting control) return UnifiedSettingRenderer.getHeight(control);
            else if (setting instanceof TextSetting control) return UnifiedSettingRenderer.getHeight(control);
            else if (setting instanceof TextListSetting control) return UnifiedSettingRenderer.getHeight(control);
            else if (setting instanceof GroupSetting control) return UnifiedSettingRenderer.getHeight(control);
            else if (setting instanceof ModeSetting control) return UnifiedSettingRenderer.getHeight(control);
            else if (setting instanceof KeyBindSetting control) return UnifiedSettingRenderer.getHeight(control);
            else if (setting instanceof FunctionBindSetting control) return UnifiedSettingRenderer.getHeight(control);
            return 0f;
        }
    }

    @Override
    public void mouseClicked(Setting setting, double x, double y, int button) {
        try (SettingRenderContext.Scope ignored = scope()) {
            if (setting instanceof BooleanSetting control) UnifiedSettingRenderer.mouseClicked(control, x, y, button);
            else if (setting instanceof SliderSetting<?> control) UnifiedSettingRenderer.mouseClicked(control, x, y, button);
            else if (setting instanceof ColorSetting control) UnifiedSettingRenderer.mouseClicked(control, x, y, button);
            else if (setting instanceof TextSetting control) UnifiedSettingRenderer.mouseClicked(control, x, y, button);
            else if (setting instanceof TextListSetting control) UnifiedSettingRenderer.mouseClicked(control, x, y, button);
            else if (setting instanceof GroupSetting control) UnifiedSettingRenderer.mouseClicked(control, x, y, button);
            else if (setting instanceof ModeSetting control) UnifiedSettingRenderer.mouseClicked(control, x, y, button);
            else if (setting instanceof KeyBindSetting control) UnifiedSettingRenderer.mouseClicked(control, x, y, button);
            else if (setting instanceof FunctionBindSetting control) UnifiedSettingRenderer.mouseClicked(control, x, y, button);
        }
    }

    @Override
    public void mouseReleased(Setting setting, double x, double y, int button) {
        try (SettingRenderContext.Scope ignored = scope()) {
            if (setting instanceof SliderSetting<?> control) UnifiedSettingRenderer.mouseReleased(control, x, y, button);
            else if (setting instanceof ColorSetting control) UnifiedSettingRenderer.mouseReleased(control, x, y, button);
        }
    }

    @Override
    public void mouseClickedOutside(Setting setting, double x, double y, int button) {
        try (SettingRenderContext.Scope ignored = scope()) {
            if (setting instanceof SliderSetting<?> control) UnifiedSettingRenderer.mouseClickedOutside(control, x, y, button);
            else if (setting instanceof ColorSetting control) UnifiedSettingRenderer.mouseClickedOutside(control, x, y, button);
            else if (setting instanceof TextSetting control) UnifiedSettingRenderer.mouseClickedOutside(control, x, y, button);
            else if (setting instanceof TextListSetting control) UnifiedSettingRenderer.mouseClickedOutside(control, x, y, button);
        }
    }

    @Override
    public boolean mouseScrolled(Setting setting, double x, double y, double amount) {
        try (SettingRenderContext.Scope ignored = scope()) {
            return setting instanceof ColorSetting control && UnifiedSettingRenderer.mouseScrolled(control, x, y, amount);
        }
    }

    @Override
    public boolean keyPressed(Setting setting, int keyCode, int scanCode, int modifiers) {
        try (SettingRenderContext.Scope ignored = scope()) {
            if (setting instanceof SliderSetting<?> control) return UnifiedSettingRenderer.keyPressed(control, keyCode, scanCode, modifiers);
            if (setting instanceof ColorSetting control) return UnifiedSettingRenderer.keyPressed(control, keyCode, scanCode, modifiers);
            if (setting instanceof TextSetting control) return UnifiedSettingRenderer.keyPressed(control, keyCode, scanCode, modifiers);
            if (setting instanceof TextListSetting control) return UnifiedSettingRenderer.keyPressed(control, keyCode, scanCode, modifiers);
            return false;
        }
    }

    @Override
    public boolean charTyped(Setting setting, char character, int modifiers) {
        try (SettingRenderContext.Scope ignored = scope()) {
            if (setting instanceof SliderSetting<?> control) return UnifiedSettingRenderer.charTyped(control, character, modifiers);
            if (setting instanceof ColorSetting control) return UnifiedSettingRenderer.charTyped(control, character, modifiers);
            if (setting instanceof TextSetting control) return UnifiedSettingRenderer.charTyped(control, character, modifiers);
            if (setting instanceof TextListSetting control) return UnifiedSettingRenderer.charTyped(control, character, modifiers);
            return false;
        }
    }
}
