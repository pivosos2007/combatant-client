/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public enum LegacyHudNotifier {
    ;
    private static final Minecraft client = Minecraft.getInstance();

    public static void show(String message) {
        if (client.gui != null) {
            client.gui.hud.setOverlayMessage(Component.nullToEmpty(message), false);
        }
    }

    public static void show(Component text) {
        if (client.gui != null) {
            client.gui.hud.setOverlayMessage(text, false);
        }
    }
}
