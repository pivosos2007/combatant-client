/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sections;

public interface ClickGuiSection {
    void layout(float x, float y, float w, float h);

    void render(float mouseX, float mouseY);

    void renderGlassPass(float alphaFactor);

    boolean mousePressed(float mouseX, float mouseY, int button);

    void mouseReleased(float mouseX, float mouseY, int button);

    boolean mouseScrolled(float mouseX, float mouseY, double amount);

    boolean keyPressed(int keyCode, int scanCode, int modifiers);

    boolean charTyped(char chr, int modifiers);

    void onSelected();

    void onDeselected();

    default boolean isVisible() {
        return true;
    }
}

