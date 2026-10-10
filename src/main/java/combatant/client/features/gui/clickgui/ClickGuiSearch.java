/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui;

import combatant.client.util.text.SingleLineTextInput;

import java.util.Locale;

public enum ClickGuiSearch {
    ;

    private static boolean active = false;
    private static final SingleLineTextInput INPUT = new SingleLineTextInput(64);

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean v) {
        active = v;
        if (!v) {
            INPUT.clear();
        }
    }

    /** Removes keyboard focus but keeps the current query/filter intact. */
    public static void unfocus() {
        active = false;
        INPUT.unfocus();
    }

    /** Clears both keyboard focus and the current query. */
    public static void deactivate() {
        active = false;
        INPUT.unfocus();
        INPUT.clear();
    }

    public static boolean hasQuery() {
        return !INPUT.text().isBlank();
    }

    public static String getText() {
        return INPUT.text();
    }

    public static SingleLineTextInput editor() { return INPUT; }

    public static void append(char c) { INPUT.type(c); }

    public static void backspace() { INPUT.backspace(); }

    public static boolean keyPressed(int keyCode, int modifiers) { return INPUT.keyPressed(keyCode, modifiers); }

    public static boolean matches(String moduleName) {
        if (INPUT.text().isEmpty()) return true;
        return matchesCandidate(moduleName);
    }

    public static boolean matches(String moduleName, Iterable<String> aliases) {
        if (INPUT.text().isEmpty()) return true;
        if (matchesCandidate(moduleName)) return true;
        return matchingAlias(aliases) != null;
    }

    /**
     * Returns the alias that best explains why the current query matched a module.
     * The original spelling is preserved for presentation in search results.
     */
    public static String matchingAlias(Iterable<String> aliases) {
        if (INPUT.text().isBlank() || aliases == null) return null;

        String best = null;
        int bestRank = Integer.MAX_VALUE;
        int bestLength = Integer.MAX_VALUE;
        for (String alias : aliases) {
            if (!matchesCandidate(alias)) continue;

            int rank = matchRank(alias);
            int length = alias == null ? Integer.MAX_VALUE : alias.length();
            if (rank < bestRank || (rank == bestRank && length < bestLength)) {
                best = alias;
                bestRank = rank;
                bestLength = length;
            }
        }
        return best;
    }

    private static int matchRank(String candidate) {
        if (candidate == null || candidate.isBlank()) return Integer.MAX_VALUE;

        String normalized = candidate.toLowerCase(Locale.ROOT);
        String compactCandidate = compact(normalized);
        String query = INPUT.text().toLowerCase(Locale.ROOT);
        String compactQuery = compact(query);
        if (normalized.equals(query) || compactCandidate.equals(compactQuery)) return 0;
        if (normalized.startsWith(query) || compactCandidate.startsWith(compactQuery)) return 1;
        return 2;
    }

    private static boolean matchesCandidate(String candidate) {
        if (candidate == null || candidate.isBlank()) return false;

        String normalized = candidate.toLowerCase(Locale.ROOT);
        String compactCandidate = compact(normalized);
        String query = INPUT.text().toLowerCase(Locale.ROOT);
        String compactQuery = compact(query);

        return normalized.contains(query)
                || compactCandidate.contains(compactQuery);
    }

    private static String compact(String value) {
        return value == null ? "" : value.replace(" ", "").replace("_", "").replace("-", "");
    }
}
