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

import java.util.List;
import java.util.Locale;

@CommandInfo(
        id = "hide",
        aliases = {"modulevisibility", "showmodule"},
        usage = "@hide <module> [on|off|toggle]",
        descriptionKey = "command.hide.description"
)
public final class HideCommand implements ClientCommand {
    @Override
    public boolean execute(CommandContext ctx) {
        Module module = CommandUtils.findModule(ctx.arg(0));
        if (module == null) {
            CommandOutput.error(ctx.arg(0) == null ? "Usage: " + metadata().usage() : "Module not found: " + ctx.arg(0));
            return true;
        }
        String action = ctx.arg(1);
        if (!CommandUtils.isState(action)) {
            CommandOutput.warning("Usage: " + metadata().usage());
            return true;
        }
        boolean hidden = CommandUtils.parseState(action, !module.isShownInModuleList());
        module.setShownInModuleList(!hidden);
        CommandOutput.success(module.getDisplayName() + (hidden ? " hidden from ModuleList" : " shown in ModuleList"));
        return true;
    }

    @Override
    public List<String> suggest(CommandContext ctx, int argIndex, String token) {
        if (argIndex == 1) return CommandUtils.suggestModules(token);
        if (argIndex == 2) {
            String lower = token == null ? "" : token.toLowerCase(Locale.ROOT);
            return List.of("on", "off", "toggle").stream().filter(value -> value.startsWith(lower)).toList();
        }
        return List.of();
    }
}
