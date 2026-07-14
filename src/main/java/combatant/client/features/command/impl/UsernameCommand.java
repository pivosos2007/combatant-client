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

@CommandInfo(
        id = "username",
        aliases = {"name", "ign", "whoami"},
        usage = "@username",
        descriptionKey = "command.username.description"
)
public final class UsernameCommand implements ClientCommand {
    @Override
    public boolean execute(CommandContext ctx) {
        if (ctx.mc() == null || ctx.mc().player == null) {
            CommandOutput.error("Player is unavailable.");
            return true;
        }
        var profile = ctx.mc().player.getGameProfile();
        CommandOutput.send("Username: " + profile.name() + " | UUID: " + profile.id());
        return true;
    }
}
