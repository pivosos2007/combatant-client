/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sections;

import org.lwjgl.glfw.GLFW;
import combatant.client.features.gui.clickgui.sections.settings.SettingsTabRuntime;

import java.nio.file.Path;
import java.util.List;

@ClickGuiSectionInfo(id = "combatant:settings", label = "Settings", order = 100)
public final class SettingsSection implements ClickGuiSection {
    private float x;
    private float y;
    private float w;
    private float h;

    @Override
    public void layout(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    @Override
    public void render(float mouseX, float mouseY) {
        SettingsTabRuntime.render(x, y, w, h, mouseX, mouseY);
    }

    @Override
    public void renderGlassPass(float alphaFactor) {
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
            return;
        }
        SettingsTabRuntime.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(float mouseX, float mouseY, double amount) {
        if (SettingsTabRuntime.isColorEditorOpen()) {
            SettingsTabRuntime.handleColorEditorScroll(mouseX, mouseY, amount);
            return true;
        }
        SettingsTabRuntime.mouseScrolled(mouseX, mouseY, amount);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (SettingsTabRuntime.isColorEditorOpen()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                SettingsTabRuntime.closeColorEditor();
            }
            return true;
        }
        return SettingsTabRuntime.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return SettingsTabRuntime.charTyped(chr, modifiers);
    }

    @Override
    public boolean onFilesDrop(List<Path> paths) {
        return SettingsTabRuntime.onFilesDrop(paths);
    }

    @Override
    public void onSelected() {
        SettingsTabRuntime.open();
        SettingsTabRuntime.markDirty();
    }

    @Override
    public void onDeselected() {
        SettingsTabRuntime.mouseReleased(0f, 0f, 0);
        SettingsTabRuntime.close();
    }

    @Override
    public boolean isVisible() {
        return SettingsTabRuntime.isVisible();
    }
}
