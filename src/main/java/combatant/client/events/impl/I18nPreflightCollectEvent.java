/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import combatant.client.config.SettingDef;
import combatant.client.config.SettingOwner;
import combatant.client.events.Event;
import combatant.client.features.gui.clickgui.settings.Setting;
import combatant.client.features.gui.clickgui.settings.SettingFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects setting-backed i18n keys after language resources are loaded.
 */
public final class I18nPreflightCollectEvent extends Event {
    private final String reason;
    private final ArrayList<Entry> entries = new ArrayList<>();

    public I18nPreflightCollectEvent(String reason) {
        this.reason = reason != null && !reason.isBlank() ? reason : "i18n preflight";
    }

    public String reason() {
        return reason;
    }

    public I18nPreflightCollectEvent setting(Setting setting) {
        return setting("settings", setting);
    }

    public I18nPreflightCollectEvent setting(String path, Setting setting) {
        if (setting != null) {
            entries.add(new Entry(normalizePath(path), setting));
        }
        return this;
    }

    public I18nPreflightCollectEvent settings(Iterable<? extends Setting> values) {
        return settings("settings", values);
    }

    public I18nPreflightCollectEvent settings(String path, Iterable<? extends Setting> values) {
        if (values == null) return this;
        for (Setting setting : values) {
            setting(path, setting);
        }
        return this;
    }

    public I18nPreflightCollectEvent settingDefs(SettingOwner owner, Iterable<SettingDef> defs) {
        return settingDefs(owner != null ? owner.name() : "settings", owner, defs);
    }

    public I18nPreflightCollectEvent settingDefs(String path, SettingOwner owner, Iterable<SettingDef> defs) {
        if (owner == null || defs == null) return this;
        for (SettingDef def : defs) {
            Setting setting = SettingFactory.fromDef(def);
            if (setting == null) continue;
            setting.setParent(owner);
            setting(path, setting);
        }
        return this;
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) return "settings";
        String normalized = path.trim().replace('\\', '/');
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized.isBlank() ? "settings" : normalized;
    }

    public record Entry(String path, Setting setting) {
    }
}
