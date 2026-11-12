/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.clickgui.sound;

import combatant.client.features.gui.hud.BaseHudElement;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleManager;

/** Owns audio for module transitions initiated by a ClickGUI control. */
public final class GuiModuleToggle {
    private GuiModuleToggle() { }

    public static boolean toggle(Module module) {
        if (module == null) return false;
        boolean previous = module.isEnabled();
        // Suppression is scoped to the transition, including synchronous state listeners.
        // HUD Notifier may still show its toast, but cannot play its own toggle audio.
        ModuleManager.runWithToggleSoundSuppressed(module::toggle);
        return soundTransition(previous, module.isEnabled());
    }

    /** HUD elements share the same GUI-toggle feedback, not a module-state listener. */
    public static boolean toggle(BaseHudElement element) {
        if (element == null) return false;
        boolean previous = element.isEnabled();
        element.setEnabled(!previous);
        return soundTransition(previous, element.isEnabled());
    }

    private static boolean soundTransition(boolean previous, boolean enabled) {
        if (previous == enabled) return false;
        // ModuleList visibility is presentation metadata, not an audio policy.
        (enabled ? GuiSound.MODULE_ON_OPEN : GuiSound.MODULE_ON_CLOSE).feedback();
        return true;
    }
}
