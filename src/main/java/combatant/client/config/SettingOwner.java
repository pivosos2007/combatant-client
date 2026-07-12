/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config;

/**
 * Owner of GUI settings. Allows settings to save config and build i18n keys
 * without tying them to Module.
 */
public interface SettingOwner {
    String name();

    void saveConfig();

    default String getTranslationKeyPrefix() {
        String name = name();
        if (name == null || name.isBlank()) return null;
        return "setting." + name;
    }
}
