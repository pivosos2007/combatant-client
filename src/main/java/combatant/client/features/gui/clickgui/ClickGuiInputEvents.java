/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.clickgui;

import combatant.client.events.EventHandler;
import combatant.client.input.InputAction;
import combatant.client.input.InputKeys;
import combatant.client.input.event.KeyboardInputEvent;
import combatant.client.input.event.TextInputEvent;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.gui.screens.Screen;

/** Input bridge for ClickGui screens; the Minecraft mixin only publishes input events. */
final class ClickGuiInputEvents {
    static final ClickGuiInputEvents INSTANCE = new ClickGuiInputEvents();

    private ClickGuiInputEvents() { }

    private static boolean isClickGuiScreen() {
        Screen screen = ClientScreen.current();
        return screen instanceof ClickGuiScreen
                || screen instanceof ClickGuiPickerScreen
                || screen instanceof ClickGuiEditorScreen;
    }

    @EventHandler(priority = 200)
    public void keyboard(KeyboardInputEvent event) {
        if (event.isCancelled() || !RuntimeGate.canRunClientLogic()
                || !ClickGuiRenderer.isGuiSessionActive() || !isClickGuiScreen()) return;
        int key = event.key().physicalKey();
        if (key == InputKeys.ESCAPE && event.action() == InputAction.PRESS) {
            if (!ClickGuiRenderer.onKey(event.key(), event.action(), event.modifiers())) {
                if (ClientScreen.current() instanceof ClickGuiEditorScreen) ClickGuiRenderer.closeGuiEditor();
                else ClickGuiRenderer.closeFromScreen();
            }
            event.cancel();
            return;
        }
        if (ClickGuiSearch.isActive()) {
            ClickGuiRenderer.onKey(event.key(), event.action(), event.modifiers());
            event.cancel();
        } else if (ClickGuiRenderer.onKey(event.key(), event.action(), event.modifiers())) {
            event.cancel();
        }
    }

    @EventHandler(priority = 200)
    public void text(TextInputEvent event) {
        if (event.isCancelled() || !RuntimeGate.canRunClientLogic()
                || !ClickGuiRenderer.isGuiSessionActive() || !isClickGuiScreen()) return;
        boolean handled = false;
        String value = event.text();
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isBmpCodePoint(cp)) handled |= ClickGuiRenderer.onCharTyped((char) cp);
        }
        if (handled) event.cancel();
    }
}
