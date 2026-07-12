/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.theme;

import java.util.List;

public enum Theme {
    ;

    public static Themes.Theme theme() {
        return Themes.theme();
    }

    public static Themes.ThemeEntry currentEntry() {
        return Themes.currentEntry();
    }

    public static String currentId() {
        return Themes.currentId();
    }

    public static List<Themes.ThemeEntry> themes() {
        return Themes.themes();
    }

    public static List<Themes.ThemeEntry> customThemes() {
        return Themes.customThemes();
    }

    public static boolean isCustomTheme(String id) {
        return Themes.isCustomTheme(id);
    }

    public static String uniqueCustomId(String name) {
        return Themes.uniqueCustomId(name);
    }

    public static Themes.ThemeEntry saveCustomTheme(Themes.ThemeEntry entry, boolean select) {
        return Themes.saveCustomTheme(entry, select);
    }

    public static Themes.ThemeEntry saveCustomTheme(Themes.ThemeEntry entry) {
        return Themes.saveCustomTheme(entry);
    }

    public static boolean deleteCustomTheme(String id) {
        return Themes.deleteCustomTheme(id);
    }

    public static Themes.ThemeEntry createCustomTheme(String name, Themes.ThemeEntry base, boolean select) {
        return Themes.createCustomTheme(name, base, select);
    }

    public static void setTheme(String id) {
        Themes.setTheme(id);
    }

    public static void setTheme(Themes.Theme theme) {
        Themes.setTheme(theme);
    }

    public static Themes.Theme classic() {
        return Themes.classic();
    }
}
