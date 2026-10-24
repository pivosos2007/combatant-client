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
import combatant.client.runtime.stability.ModuleStabilityRun;

import java.util.List;
import java.util.Locale;

@CommandInfo(
        id = "stability",
        usage = "@stability [run|resume [ticks]|only <module> [ticks]|stop]",
        descriptionKey = "command.stability.description"
)
public final class StabilityCommand implements ClientCommand {
    @Override
    public boolean execute(CommandContext ctx) {
        String action = ctx.arg(0) == null ? "run" : ctx.arg(0).toLowerCase(Locale.ROOT);
        String error = switch (action) {
            case "run", "resume" -> ModuleStabilityRun.start("run".equals(action), parseTicks(ctx.arg(1)));
            case "only" -> ctx.arg(1) == null
                    ? "Usage: " + metadata().usage()
                    : ModuleStabilityRun.start(false, parseTicks(ctx.arg(2)), ctx.arg(1));
            case "stop" -> {
                if (!ModuleStabilityRun.running()) yield "No stability run is active.";
                ModuleStabilityRun.stop("stopped");
                yield null;
            }
            default -> "Usage: " + metadata().usage();
        };
        if (error != null) CommandOutput.warning(error);
        return true;
    }

    private static int parseTicks(String value) {
        if (value == null) return ModuleStabilityRun.DEFAULT_HOLD_TICKS;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return ModuleStabilityRun.DEFAULT_HOLD_TICKS;
        }
    }

    @Override
    public List<String> suggest(CommandContext ctx, int argIndex, String token) {
        if (argIndex == 2 && "only".equalsIgnoreCase(ctx.arg(0))) return CommandUtils.suggestModules(token);
        if (argIndex != 1) return List.of();
        String lower = token == null ? "" : token.toLowerCase(Locale.ROOT);
        return List.of("run", "resume", "only", "stop").stream().filter(value -> value.startsWith(lower)).toList();
    }
}
