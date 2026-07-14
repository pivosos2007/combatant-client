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

import java.util.Locale;

@CommandInfo(
        id = "coordinates",
        aliases = {"coords", "position", "pos"},
        usage = "@coordinates",
        descriptionKey = "command.coordinates.description"
)
public final class CoordinatesCommand implements ClientCommand {
    @Override
    public boolean execute(CommandContext ctx) {
        if (ctx.mc() == null || ctx.mc().player == null) {
            CommandOutput.error("Player is unavailable.");
            return true;
        }
        CommandOutput.send(String.format(Locale.ROOT, "Position: %.2f, %.2f, %.2f | Block: %d, %d, %d",
                ctx.mc().player.getX(), ctx.mc().player.getY(), ctx.mc().player.getZ(),
                ctx.mc().player.getBlockX(), ctx.mc().player.getBlockY(), ctx.mc().player.getBlockZ()));
        return true;
    }
}
