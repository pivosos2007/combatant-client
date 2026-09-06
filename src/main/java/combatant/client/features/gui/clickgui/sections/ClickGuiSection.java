/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sections;

import java.nio.file.Path;
import java.util.List;

public interface ClickGuiSection {
    void layout(float x, float y, float w, float h);

    void render(float mouseX, float mouseY);

    default void renderGlassPass(float alphaFactor) {
    }

    default boolean mousePressed(float mouseX, float mouseY, int button) {
        return false;
    }

    default void mouseReleased(float mouseX, float mouseY, int button) {
    }

    default boolean mouseScrolled(float mouseX, float mouseY, double amount) {
        return false;
    }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    default boolean charTyped(char chr, int modifiers) {
        return false;
    }

    default boolean onFilesDrop(List<Path> paths) {
        return false;
    }

    default void onSelected() {
    }

    default void onDeselected() {
    }

    default boolean isAvailable() {
        return true;
    }

    default boolean usesFullViewport() {
        return false;
    }

    default boolean isVisible() {
        return true;
    }
}
