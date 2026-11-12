/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.clickgui.settings;

import java.nio.file.Path;
import java.util.List;

/** A complete Settings tab UI, replaceable independently of the native controls renderer. */
public interface SettingsPage {
    void render(float x, float y, float width, float height, float mouseX, float mouseY);

    default void open() { }
    default void close() { }
    default void markDirty() { }
    default boolean visible() { return false; }
    /** Implementations may expose the background for draggable HUD previews. */
    default boolean allowsHudPreviewPointer(float mouseX, float mouseY) { return false; }
    default boolean mousePressed(float mouseX, float mouseY, int button) { return false; }
    default void mouseReleased(float mouseX, float mouseY, int button) { }
    default boolean mouseScrolled(float mouseX, float mouseY, double amount) { return false; }
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }
    default boolean charTyped(char character, int modifiers) { return false; }
    default boolean filesDropped(List<Path> paths) { return false; }
    default boolean cancel() { return false; }
}
