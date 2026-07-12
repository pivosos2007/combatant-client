/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.profile;


import combatant.client.features.theme.Theme;
import combatant.client.config.ConfigObject;
import combatant.client.config.values.ConfigValue;
import combatant.client.addon.AddonManager;
import combatant.client.features.gui.hud.BaseHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.gui.hud.nondraggable.StaticHudElementRegistry;
import combatant.client.features.theme.Themes;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleManager;
import combatant.client.util.logging.DebugLog;

import java.util.HashMap;
import java.util.Map;

public final class ConfigProfileApplier {
    public ApplyResult apply(ConfigProfileSnapshot snapshot) {
        if (snapshot == null) return new ApplyResult(0, 0, 0);
        ApplyCounter counter = new ApplyCounter();
        if (snapshot.meta().type() == ConfigProfileType.MODULES) {
            ModuleManager.runSilentConfigApply(() -> applyEntries(snapshot, counter));
        } else {
            applyEntries(snapshot, counter);
        }
        return new ApplyResult(counter.appliedOwners, counter.missingOwners, counter.missingValues);
    }

    private void applyEntries(ConfigProfileSnapshot snapshot, ApplyCounter counter) {
        if (snapshot.meta().type() == ConfigProfileType.THEMES) {
            applyThemeEntries(snapshot, counter);
            return;
        }
        for (ConfigProfileEntry entry : snapshot.entries()) {
            ConfigObject owner = resolveOwner(entry.ownerId());
            if (owner == null) {
                counter.missingOwners++;
                DebugLog.config("Config profile missing owner: %s", entry.ownerId());
                continue;
            }
            applyValues(owner, entry, counter);
            counter.appliedOwners++;
            afterOwnerApplied(owner);
        }
    }

    private void applyThemeEntries(ConfigProfileSnapshot snapshot, ApplyCounter counter) {
        String currentId = null;
        for (ConfigProfileEntry entry : snapshot.entries()) {
            String ownerId = entry.ownerId();
            if (ownerId == null) {
                counter.missingOwners++;
                continue;
            }
            if (ownerId.equals("theme-state:current")) {
                Object current = entry.values().get("current");
                if (current instanceof CharSequence s && !s.toString().isBlank()) {
                    currentId = s.toString();
                }
                counter.appliedOwners++;
                continue;
            }
            if (!ownerId.startsWith("theme:")) {
                counter.missingOwners++;
                continue;
            }
            String id = ownerId.substring("theme:".length());
            try {
                Themes.importProfileValues(id, entry.displayName(), entry.values());
                counter.appliedOwners++;
            } catch (Throwable t) {
                counter.missingValues++;
                DebugLog.error("Failed to apply theme profile entry %s", t, ownerId);
            }
        }
        if (currentId != null) {
            String selectedId = currentId.toLowerCase(java.util.Locale.ROOT);
            if (Theme.themes().stream().anyMatch(theme -> theme.getId().equals(selectedId))) {
                Theme.setTheme(selectedId);
            }
        }
    }

    private void applyValues(ConfigObject owner, ConfigProfileEntry entry, ApplyCounter counter) {
        Map<String, ConfigValue<?>> byName = new HashMap<>();
        for (ConfigValue<?> value : owner.getConfigValues()) {
            if (value != null && value.getName() != null) byName.put(value.getName(), value);
        }
        for (Map.Entry<String, Object> saved : entry.values().entrySet()) {
            ConfigValue<?> value = byName.get(saved.getKey());
            if (value == null) {
                counter.missingValues++;
                continue;
            }
            try {
                value.fromJson(saved.getValue());
            } catch (Throwable t) {
                counter.missingValues++;
                DebugLog.error("Failed to apply profile value %s.%s", t, entry.ownerId(), saved.getKey());
            }
        }
    }

    private ConfigObject resolveOwner(String ownerId) {
        if (ownerId == null) return null;
        if (ownerId.startsWith("addon:")) {
            return resolveOwner(stripAddonPrefix(ownerId));
        }
        if (ownerId.startsWith("module:")) return ModuleManager.get(ownerId.substring("module:".length()));
        if (ownerId.startsWith("hud.draggable:")) {
            return DraggableHudElementRegistry.getById(ownerId.substring("hud.draggable:".length()));
        }
        if (ownerId.startsWith("hud.static:")) {
            return StaticHudElementRegistry.getById(ownerId.substring("hud.static:".length()));
        }
        return null;
    }

    private String stripAddonPrefix(String ownerId) {
        String addonId = AddonManager.addonIdFromProfileOwner(ownerId);
        if (addonId == null) return "";
        String prefix = "addon:" + addonId + ":";
        if (!ownerId.startsWith(prefix)) return "";
        return ownerId.substring(prefix.length());
    }

    private void afterOwnerApplied(ConfigObject owner) {
        if (owner instanceof Module module) {
            module.applyConfiguredEnabledState();
            module.syncSettingsAfterLoad();
            module.saveConfig();
            return;
        }
        if (owner instanceof BaseHudElement hud) {
            hud.saveConfig();
            return;
        }
    }

    private static final class ApplyCounter {
        int appliedOwners;
        int missingOwners;
        int missingValues;
    }

    public record ApplyResult(int appliedOwners, int missingOwners, int missingValues) {
    }
}
