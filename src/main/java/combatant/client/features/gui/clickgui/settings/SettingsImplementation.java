/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.clickgui.settings;

/** Complete setting widget implementation: geometry, rendering and input are owned together. */
public interface SettingsImplementation {
    String id();

    /** A provider can replace the entire Settings tab, not just individual setting widgets. */
    default SettingsPage page() { return NativeSettingsPage.INSTANCE; }

    /** Called inside an active settings render context and Renderer2D batch. */
    void render(Setting setting, float x, float y, float width, float mouseX, float mouseY);

    float height(Setting setting);

    default void mouseClicked(Setting setting, double x, double y, int button) { }
    default void mouseReleased(Setting setting, double x, double y, int button) { }
    default void mouseClickedOutside(Setting setting, double x, double y, int button) { }
    default boolean mouseScrolled(Setting setting, double x, double y, double amount) { return false; }
    default boolean keyPressed(Setting setting, int keyCode, int scanCode, int modifiers) { return false; }
    default boolean charTyped(Setting setting, char character, int modifiers) { return false; }
}
