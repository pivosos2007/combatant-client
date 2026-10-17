/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.input;

import combatant.client.events.EventHandler;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.module.ModuleManager;
import combatant.client.input.event.InputButtonEvent;

public enum ModuleBindInputHandler {
    INSTANCE;

    @EventHandler
    private void onInput(InputButtonEvent event) {
        if (event.action() != InputAction.PRESS || ClickGuiRenderer.isBlockingModuleKeybinds()) return;
        ModuleManager.handleModuleKeybinds();
    }
}
