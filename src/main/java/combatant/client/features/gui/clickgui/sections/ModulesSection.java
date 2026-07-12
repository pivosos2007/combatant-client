/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sections;

import combatant.client.features.gui.clickgui.layout.screen.modules.ModulesMenuScreen;

public final class ModulesSection implements ClickGuiSection {
    private final ModulesMenuScreen screen = new ModulesMenuScreen();

    @Override
    public void layout(float x, float y, float w, float h) {
        screen.layout(x, y, w, h);
    }

    @Override
    public void render(float mouseX, float mouseY) {
        screen.render(mouseX, mouseY);
    }

    @Override
    public void renderGlassPass(float alphaFactor) {
        screen.renderGlassPass(alphaFactor);
    }

    @Override
    public boolean mousePressed(float mouseX, float mouseY, int button) {
        return screen.mousePressed(mouseX, mouseY, button);
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int button) {
        screen.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(float mouseX, float mouseY, double amount) {
        return screen.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return screen.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return screen.charTyped(chr, modifiers);
    }

    @Override
    public void onSelected() {
        screen.open();
    }

    @Override
    public void onDeselected() {
        screen.close();
    }

    @Override
    public boolean isVisible() {
        return screen.isVisible();
    }
}

