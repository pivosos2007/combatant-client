/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.profile;


import combatant.client.features.theme.Theme;
import net.minecraft.client.Minecraft;
import combatant.client.config.ConfigObject;
import combatant.client.config.values.ConfigValue;
import combatant.client.addon.AddonManager;
import combatant.client.features.gui.hud.AbstractHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.gui.hud.nondraggable.StaticHudElementRegistry;
import combatant.client.features.theme.Themes;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigProfileCollector {
    public ConfigProfileSnapshot collect(ConfigProfileType type, String name) {
        long now = System.currentTimeMillis();
        String safeName = (name == null || name.isBlank()) ? defaultName(type) : name.trim();
        String id = ConfigProfileStorage.sanitizeFileName(safeName);
        ConfigProfileMeta meta = new ConfigProfileMeta(
                id,
                safeName,
                type,
                currentAuthorName(),
                now,
                now,
                ConfigProfileBinaryCodec.VERSION
        );
        return new ConfigProfileSnapshot(meta, collectEntries(type));
    }

    public ConfigProfileSnapshot collectWithMeta(ConfigProfileMeta meta) {
        if (meta == null) throw new IllegalArgumentException("profile meta is null");
        return new ConfigProfileSnapshot(meta, collectEntries(meta.type()));
    }

    public List<ConfigProfileEntry> collectEntries(ConfigProfileType type) {
        List<ConfigProfileEntry> entries = new ArrayList<>();
        if (type == ConfigProfileType.HUD) {
            for (DraggableHudElement widget : DraggableHudElementRegistry.getWidgets()) {
                String ownerId = AddonManager.draggableHudProfileOwnerId(widget.getId());
                entries.add(entry(ownerId, AddonManager.profileDisplayName(ownerId, widget.getTitle()), widget));
            }
            for (AbstractHudElement element : StaticHudElementRegistry.getAll()) {
                String ownerId = AddonManager.staticHudProfileOwnerId(element.getId());
                entries.add(entry(ownerId, AddonManager.profileDisplayName(ownerId, element.getTitle()), element));
            }
        } else if (type == ConfigProfileType.THEMES) {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("current", Theme.currentId());
            entries.add(new ConfigProfileEntry("theme-state:current", "Current Theme", state));
            for (Themes.ThemeEntry theme : Theme.customThemes()) {
                entries.add(new ConfigProfileEntry("theme:" + theme.getId(), theme.name(), Themes.toProfileValues(theme)));
            }
        } else {
            for (Module module : ModuleManager.getModules()) {
                String ownerId = AddonManager.moduleProfileOwnerId(module.name());
                entries.add(entry(ownerId, AddonManager.profileDisplayName(ownerId, module.getDisplayName()), module));
            }
        }
        return entries;
    }

    private ConfigProfileEntry entry(String ownerId, String displayName, ConfigObject owner) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (owner != null) {
            for (ConfigValue<?> value : owner.getConfigValues()) {
                if (value != null && value.getName() != null) {
                    values.put(value.getName(), value.toJson());
                }
            }
        }
        return new ConfigProfileEntry(ownerId, displayName, values);
    }

    private String currentAuthorName() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getUser() != null) {
                String username = mc.getUser().getName();
                if (username != null && !username.isBlank()) return username;
            }
        } catch (Throwable ignored) {
        }
        // TODO: use Discord RPC nickname/avatar when Discord integration is ready.
        return "Unknown";
    }

    private String defaultName(ConfigProfileType type) {
        return type == ConfigProfileType.HUD ? "Hud Config" : type == ConfigProfileType.THEMES ? "Themes Config" : "Modules Config";
    }
}
