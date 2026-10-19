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
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.player.EnderChestPlus;

import java.util.Map;

@CommandInfo(
        id = "echest",
        aliases = {"enderchest", "ec"},
        usage = "@echest",
        descriptionKey = "command.echest.description"
)
public final class EnderChestCommand implements ClientCommand {
    @Override
    public boolean execute(CommandContext ctx) {
        EnderChestPlus module = Modules.get(EnderChestPlus.class);
        if (module == null || !module.isEnabled()) {
            CommandOutput.warning("Enable EnderChestPlus first.");
            return true;
        }
        if (!module.hasCache()) {
            CommandOutput.warning("Open your ender chest once to cache its contents.");
            return true;
        }
        Map<String, Integer> totals = module.totals();
        if (totals.isEmpty()) {
            CommandOutput.send("Ender chest is empty.");
            return true;
        }
        CommandOutput.send("Ender chest (" + totals.size() + " kinds):");
        for (Map.Entry<String, Integer> entry : totals.entrySet()) {
            CommandOutput.send(entry.getValue() + "x " + entry.getKey());
        }
        return true;
    }
}
