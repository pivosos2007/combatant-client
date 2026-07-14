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
import net.minecraft.client.multiplayer.ServerData;

import java.util.Locale;

@CommandInfo(
        id = "serverinfo",
        aliases = {"server", "sinfo"},
        usage = "@serverinfo",
        descriptionKey = "command.serverinfo.description"
)
public final class ServerInfoCommand implements ClientCommand {
    @Override
    public boolean execute(CommandContext ctx) {
        if (ctx.mc() == null || ctx.mc().level == null) {
            CommandOutput.error("No world is loaded.");
            return true;
        }
        if (ctx.mc().hasSingleplayerServer()) {
            String name = ctx.mc().getSingleplayerServer() == null
                    ? "Singleplayer"
                    : ctx.mc().getSingleplayerServer().getWorldData().getLevelName();
            CommandOutput.send("World: " + name + " | Singleplayer | TPS: 20.00");
            return true;
        }
        ServerData server = ctx.mc().getCurrentServer();
        String address = server == null || server.ip == null ? "unknown" : server.ip;
        String label = server == null || server.name == null ? "unknown" : server.name;
        int ping = NetworkStatsUtil.getPing(ctx.mc());
        float tps = NetworkStatsUtil.getTps(ctx.mc());
        CommandOutput.send(String.format(Locale.ROOT, "Server: %s | %s | Ping: %s | TPS: %.2f",
                label, address, ping < 0 ? "unknown" : ping + " ms", tps));
        return true;
    }
}
