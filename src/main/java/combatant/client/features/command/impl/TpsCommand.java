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
import combatant.client.util.player.NetworkStatsUtil;

import java.util.Locale;

@CommandInfo(
        id = "tps",
        aliases = "servertps",
        usage = "@tps",
        descriptionKey = "command.tps.description"
)
public final class TpsCommand implements ClientCommand {
    @Override
    public boolean execute(CommandContext ctx) {
        float tps = NetworkStatsUtil.getTps(ctx.mc());
        CommandOutput.send(String.format(Locale.ROOT, "TPS: %.2f / 20.00", tps));
        return true;
    }
}
