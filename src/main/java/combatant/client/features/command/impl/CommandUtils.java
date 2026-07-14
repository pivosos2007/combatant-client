/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.command.impl;

import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class CommandUtils {
    private CommandUtils() {
    }

    static Module findModule(String query) {
        if (query == null || query.isBlank()) return null;
        for (Module module : ModuleManager.getModules()) {
            if (matchesModule(module, query)) return module;
        }
        return null;
    }

    static List<String> suggestModules(String token) {
        String lower = normalize(token);
        return ModuleManager.getModules().stream()
                .map(Module::name)
                .filter(name -> lower.isEmpty() || name.startsWith(lower))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    static List<String> suggestPlayers(Minecraft mc, String token) {
        if (mc == null || mc.getConnection() == null) return List.of();
        String lower = normalize(token);
        List<String> names = new ArrayList<>();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            if (info == null || info.getProfile() == null) continue;
            String name = info.getProfile().name();
            if (name != null && (lower.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(lower))) {
                names.add(name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    static List<Module> sortedModules() {
        return ModuleManager.getModules().stream()
                .sorted(Comparator.comparing(Module::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    static String join(List<String> parts, int start) {
        if (parts == null || start < 0 || start >= parts.size()) return "";
        return String.join(" ", parts.subList(start, parts.size())).trim();
    }

    static boolean parseState(String value, boolean current) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("toggle")) return !current;
        return value.equalsIgnoreCase("on")
                || value.equalsIgnoreCase("true")
                || value.equalsIgnoreCase("enable")
                || value.equalsIgnoreCase("enabled")
                || value.equals("1");
    }

    static boolean isState(String value) {
        if (value == null) return true;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "toggle", "on", "off", "true", "false", "enable", "disable", "enabled", "disabled", "1", "0" -> true;
            default -> false;
        };
    }

    private static boolean matchesModule(Module module, String query) {
        if (module.name().equalsIgnoreCase(query) || module.getDisplayName().equalsIgnoreCase(query)) return true;
        for (String alias : module.getAliases()) {
            if (alias.equalsIgnoreCase(query)) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
