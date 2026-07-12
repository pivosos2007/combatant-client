/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.command;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;

public enum VanillaChatRuntimeCleanup {
    ;

    public static void clearVanillaChatCache() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null || mc.gui.hud == null) return;
        ChatComponent chat = mc.gui.hud.getChat();
        if (chat != null) {
            chat.clearMessages(true);
        }
        CommandOutput.clearRecentMessages();
    }
}
