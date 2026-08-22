/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.chat;

import java.util.Locale;
import java.util.Set;

/** Detects chat-command argument ranges that are very likely to contain credentials. */
public enum ChatPasswordHeuristics {
    ;

    private static final Set<String> PASSWORD_COMMANDS = Set.of(
            "l", "login", "log",
            "register", "reg",
            "password", "passwd",
            "changepassword", "changepass",
            "auth", "authenticate"
    );

    public static SensitiveRange sensitiveRange(String input) {
        if (input == null || input.isBlank()) return null;
        int length = input.length();
        int cursor = 0;
        while (cursor < length && Character.isWhitespace(input.charAt(cursor))) cursor++;
        if (cursor >= length || input.charAt(cursor) != '/') return null;

        int commandStart = ++cursor;
        while (cursor < length && !Character.isWhitespace(input.charAt(cursor))) cursor++;
        if (cursor <= commandStart) return null;

        String command = input.substring(commandStart, cursor).toLowerCase(Locale.ROOT);
        if (!isPasswordCommand(command)) return null;

        while (cursor < length && Character.isWhitespace(input.charAt(cursor))) cursor++;
        if (cursor >= length) return null;
        return new SensitiveRange(cursor, length, command);
    }

    private static boolean isPasswordCommand(String command) {
        if (PASSWORD_COMMANDS.contains(command)) return true;
        return command.contains("login")
                || command.contains("register")
                || command.contains("password")
                || command.contains("passwd")
                || command.contains("changepass");
    }

    public record SensitiveRange(int start, int end, String command) {
        public SensitiveRange {
            if (start < 0 || end < start) {
                throw new IllegalArgumentException("Invalid sensitive range");
            }
        }

        public boolean intersects(int from, int to) {
            return Math.max(start, from) < Math.min(end, to);
        }
    }
}
