/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.command.impl;

import combatant.client.features.command.ClientCommand;
import combatant.client.features.command.CommandContext;
import combatant.client.features.command.CommandInfo;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.util.input.KeyUtil;

import java.util.List;
import java.util.Locale;

@CommandInfo(
        id = "bind",
        aliases = {"binds", "keybind"},
        usage = "@bind <module> <key|combo|none> | @bind list",
        descriptionKey = "command.bind.description"
)
public final class BindCommand implements ClientCommand {
    @Override
    public boolean execute(CommandContext ctx) {
        String moduleArg = ctx.arg(0);
        if (moduleArg != null && moduleArg.equalsIgnoreCase("list")) {
            listBinds();
            return true;
        }
        Module module = CommandUtils.findModule(moduleArg);
        if (module == null) {
            CommandOutput.error(moduleArg == null ? "Usage: " + metadata().usage() : "Module not found: " + moduleArg);
            return true;
        }
        String key = ctx.arg(1);
        if (key == null) {
            CommandOutput.send(module.getDisplayName() + " bind: " + module.getKeyBindSetting().getValue().get());
            return true;
        }
        String normalized = key.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("NONE") && KeyUtil.stringToCombo(normalized).isEmpty()) {
            CommandOutput.error("Unknown key or combo: " + key);
            return true;
        }
        module.getKeyBindSetting().setKeyByName(normalized);
        module.saveConfig();
        CommandOutput.success(module.getDisplayName() + " bound to " + normalized);
        return true;
    }

    @Override
    public List<String> suggest(CommandContext ctx, int argIndex, String token) {
        if (argIndex == 1) {
            List<String> modules = CommandUtils.suggestModules(token);
            if (token == null || token.isBlank() || "list".startsWith(token.toLowerCase(Locale.ROOT))) {
                java.util.ArrayList<String> out = new java.util.ArrayList<>(modules);
                out.add("list");
                return out;
            }
            return modules;
        }
        if (argIndex == 2) return List.of("NONE", "R", "V", "LEFT_CTRL+R");
        return List.of();
    }

    private static void listBinds() {
        List<Module> bound = CommandUtils.sortedModules().stream()
                .filter(module -> !module.getKeyBindSetting().getValue().isNone())
                .toList();
        if (bound.isEmpty()) {
            CommandOutput.send("No module binds configured.");
            return;
        }
        CommandOutput.send("Module binds: " + bound.size());
        for (Module module : bound) {
            CommandOutput.send(module.getDisplayName() + " — " + module.getKeyBindSetting().getValue().get());
        }
    }
}
