/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.input.minecraft;

import combatant.client.input.CombatantInput;
import combatant.client.input.event.TextCompositionEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class CombatantTextInputBridge {
    private static Object owner;
    private static Screen ownerScreen;

    private CombatantTextInputBridge() {
    }

    public static void focus(Object nextOwner, int x0, int y0, int x1, int y1) {
        if (nextOwner == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        if (owner == nextOwner) {
            minecraft.textInputManager().setTextInputArea(x0, y0, x1, y1);
            return;
        }

        if (owner != null && owner != nextOwner) {
            minecraft.textInputManager().stopTextInput(owner);
            CombatantInput.focus().releaseText(owner);
        }
        owner = nextOwner;
        ownerScreen = combatant.client.util.screen.ClientScreen.current(minecraft);
        minecraft.textInputManager().setTextInputArea(x0, y0, x1, y1);
        minecraft.textInputManager().startTextInput(nextOwner);
        CombatantInput.focus().claimText(nextOwner);
    }

    public static void updateArea(Object currentOwner, int x0, int y0, int x1, int y1) {
        if (owner != currentOwner) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) minecraft.textInputManager().setTextInputArea(x0, y0, x1, y1);
    }

    public static void blur(Object currentOwner) {
        if (owner != currentOwner) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) minecraft.textInputManager().stopTextInput(currentOwner);
        CombatantInput.focus().releaseText(currentOwner);
        owner = null;
        ownerScreen = null;
        restorePlatformOwner(minecraft);
    }

    public static void clear() {
        Object previous = owner;
        owner = null;
        ownerScreen = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) minecraft.textInputManager().stopTextInput();
        if (previous != null) CombatantInput.focus().releaseText(previous);
    }

    public static boolean owns(Object candidate) {
        return owner == candidate;
    }

    public static boolean hasOwner() {
        return owner != null;
    }

    static void syncScreen(Screen screen) {
        if (owner != null && ownerScreen != screen) clear();
    }

    static boolean deliverText(String text) {
        return owner instanceof CombatantTextInputTarget target && target.insertText(text);
    }

    static boolean deliverComposition(TextCompositionEvent event) {
        if (!(owner instanceof CombatantTextInputTarget target)) return false;
        target.updateComposition(event);
        return true;
    }

    private static void restorePlatformOwner(Minecraft minecraft) {
        if (minecraft == null) return;
        Screen screen = combatant.client.util.screen.ClientScreen.current(minecraft);
        Object focused = screen == null ? null : screen.getFocused();
        if (focused != null) minecraft.textInputManager().onTextInputFocusChange(focused, true);
    }
}
