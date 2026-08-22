/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui;

import java.util.Locale;

public enum ClickGuiSearch {
    ;

    private static boolean active = false;
    private static String text = "";
    private static String textLower = "";

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean v) {
        active = v;
        if (!v) {
            text = "";
            textLower = "";
        }
    }

    /** Removes keyboard focus but keeps the current query/filter intact. */
    public static void unfocus() {
        active = false;
    }

    /** Clears both keyboard focus and the current query. */
    public static void deactivate() {
        active = false;
        text = "";
        textLower = "";
    }

    public static boolean hasQuery() {
        return !text.isBlank();
    }

    public static String getText() {
        return text;
    }

    public static void append(char c) {
        if (c < 32) return;
        if (text.length() > 32) return;
        text += c;
        textLower = text.toLowerCase(Locale.ROOT);
    }

    public static void backspace() {
        if (text.isEmpty()) return;
        text = text.substring(0, text.length() - 1);
        textLower = text.toLowerCase(Locale.ROOT);
    }

    public static boolean matches(String moduleName) {
        if (text.isEmpty()) return true;
        return matchesCandidate(moduleName);
    }

    public static boolean matches(String moduleName, Iterable<String> aliases) {
        if (text.isEmpty()) return true;
        if (matchesCandidate(moduleName)) return true;
        if (aliases == null) return false;

        for (String alias : aliases) {
            if (matchesCandidate(alias)) return true;
        }
        return false;
    }

    private static boolean matchesCandidate(String candidate) {
        if (candidate == null || candidate.isBlank()) return false;

        String normalized = candidate.toLowerCase(Locale.ROOT);
        String compactCandidate = compact(normalized);
        String compactQuery = compact(textLower);

        return normalized.contains(textLower)
                || compactCandidate.contains(compactQuery);
    }

    private static String compact(String value) {
        return value == null ? "" : value.replace(" ", "").replace("_", "").replace("-", "");
    }
}
