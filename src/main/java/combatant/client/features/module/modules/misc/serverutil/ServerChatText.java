/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc.serverutil;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

/** Plain-text helpers shared by the chat-driven server utility modules. */
public final class ServerChatText {

    private static final Pattern PLAYER_NAME = Pattern.compile("^\\.?[A-Za-z0-9_]{1,16}$");

    private ServerChatText() {
    }

    /** Removes {@code §} colour/format codes and trims. Never returns null. */
    public static String stripSection(String message) {
        if (message == null) return "";
        return message.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
    }

    public static boolean isPlayerName(String name) {
        return name != null && PLAYER_NAME.matcher(name).matches();
    }

    /** First configured keyword contained (case-insensitively) in the message, or null. */
    public static String firstKeyword(String message, Collection<String> keywords) {
        if (message == null || keywords == null || keywords.isEmpty()) return null;
        String body = stripSection(message).toLowerCase(Locale.ROOT);
        if (body.isEmpty()) return null;
        for (String keyword : keywords) {
            if (keyword == null) continue;
            String needle = keyword.trim().toLowerCase(Locale.ROOT);
            if (!needle.isEmpty() && body.contains(needle)) return keyword.trim();
        }
        return null;
    }
}
