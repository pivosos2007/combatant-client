/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.clickgui.settings;

import combatant.client.features.gui.clickgui.sections.settings.SettingsTabRuntime;

import java.nio.file.Path;
import java.util.List;

/** Adapter for the existing Java Settings workspace. */
final class NativeSettingsPage implements SettingsPage {
    static final NativeSettingsPage INSTANCE = new NativeSettingsPage();

    private NativeSettingsPage() { }

    @Override
    public void render(float x, float y, float width, float height, float mouseX, float mouseY) {
        SettingsTabRuntime.render(x, y, width, height, mouseX, mouseY);
    }

    @Override
    public void open() { SettingsTabRuntime.open(); }

    @Override
    public void close() {
        SettingsTabRuntime.mouseReleased(0f, 0f, 0);
        SettingsTabRuntime.close();
    }

    @Override
    public void markDirty() { SettingsTabRuntime.markDirty(); }

    @Override
    public boolean visible() { return SettingsTabRuntime.isVisible(); }

    @Override
    public boolean allowsHudPreviewPointer(float mouseX, float mouseY) {
        return !SettingsTabRuntime.isColorEditorOpen()
                && SettingsTabRuntime.allowsHudPreviewPointer(mouseX, mouseY);
    }

    @Override
    public boolean mousePressed(float mouseX, float mouseY, int button) {
        if (SettingsTabRuntime.isColorEditorOpen()) {
            SettingsTabRuntime.handleColorEditorMouseButton(mouseX, mouseY, button, true);
            return true;
        }
        return SettingsTabRuntime.mousePressed(mouseX, mouseY, button);
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int button) {
        if (SettingsTabRuntime.isColorEditorOpen()) {
            SettingsTabRuntime.handleColorEditorMouseButton(mouseX, mouseY, button, false);
        } else {
            SettingsTabRuntime.mouseReleased(mouseX, mouseY, button);
        }
    }

    @Override
    public boolean mouseScrolled(float mouseX, float mouseY, double amount) {
        if (SettingsTabRuntime.isColorEditorOpen()) {
            SettingsTabRuntime.handleColorEditorScroll(mouseX, mouseY, amount);
        } else {
            SettingsTabRuntime.mouseScrolled(mouseX, mouseY, amount);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (SettingsTabRuntime.isColorEditorOpen()) {
            if (keyCode == combatant.client.input.InputKeys.ESCAPE) SettingsTabRuntime.closeColorEditor();
            return true;
        }
        return SettingsTabRuntime.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return SettingsTabRuntime.charTyped(character, modifiers);
    }

    @Override
    public boolean filesDropped(List<Path> paths) { return SettingsTabRuntime.onFilesDrop(paths); }
}
