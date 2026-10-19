/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.text;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Sends chat lines and slash commands to the server the way the chat screen does. */
public enum ServerChat {
    ;

    /** Strips the leading slash and whitespace; null or blank input gives an empty string. */
    public static String normalizeCommand(String raw) {
        if (raw == null) return "";
        String text = raw.strip();
        while (text.startsWith("/")) text = text.substring(1).stripLeading();
        return text;
    }

    /** Sends {@code /command}; the leading slash in {@code raw} is optional. Returns false if nothing was sent. */
    public static boolean command(String raw) {
        LocalPlayer player = Minecraft.getInstance().player;
        String command = normalizeCommand(raw);
        if (player == null || player.connection == null || command.isEmpty()) return false;
        player.connection.sendCommand(command);
        return true;
    }

    /** Sends a public chat line. Returns false if nothing was sent. */
    public static boolean chat(String message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.connection == null || message == null || message.isBlank()) return false;
        player.connection.sendChat(message);
        return true;
    }
}
