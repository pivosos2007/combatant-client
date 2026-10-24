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
import combatant.client.features.module.modules.player.AreaMiner;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Locale;

@CommandInfo(
        id = "areaminer",
        aliases = {"am", "areamine"},
        usage = "@areaminer [pos1|pos2 [x y z]|start|pause|resume|restart|clear|depth <n>|status]",
        descriptionKey = "command.areaminer.description"
)
public final class AreaMinerCommand implements ClientCommand {

    private static final List<String> SUBCOMMANDS =
            List.of("pos1", "pos2", "start", "pause", "resume", "restart", "clear", "depth", "status");

    @Override
    public boolean execute(CommandContext ctx) {
        AreaMiner module = Modules.get(AreaMiner.class);
        if (module == null) {
            CommandOutput.error(I18n.get("command.areaminer.unavailable"));
            return true;
        }
        String sub = ctx.arg(0) == null ? "status" : ctx.arg(0).toLowerCase(Locale.ROOT);
        switch (sub) {
            case "pos1", "p1", "1" -> corner(ctx, module, 0);
            case "pos2", "p2", "2" -> corner(ctx, module, 1);
            case "start", "on" -> {
                if (!module.hasSelection()) {
                    CommandOutput.warning(I18n.get("command.areaminer.no_selection"));
                    return true;
                }
                if (module.isFinished()) module.restart();
                module.setEnabled(true);
                module.setPaused(false);
                CommandOutput.success(module.statusText());
            }
            case "pause" -> {
                module.setPaused(true);
                CommandOutput.send(module.statusText());
            }
            case "resume", "continue" -> {
                if (!module.isEnabled()) module.setEnabled(true);
                module.setPaused(false);
                CommandOutput.send(module.statusText());
            }
            case "restart" -> {
                module.restart();
                CommandOutput.send(module.statusText());
            }
            case "clear", "reset" -> {
                module.clearSelection();
                CommandOutput.success(I18n.get("notification.areaminer.cleared"));
            }
            case "depth" -> depth(ctx, module);
            case "status" -> CommandOutput.send(module.statusText());
            default -> CommandOutput.warning("Usage: " + metadata().usage());
        }
        return true;
    }

    private void corner(CommandContext ctx, AreaMiner module, int index) {
        if (ctx.args().size() >= 4) {
            try {
                BlockPos pos = new BlockPos(Integer.parseInt(ctx.arg(1)), Integer.parseInt(ctx.arg(2)),
                        Integer.parseInt(ctx.arg(3)));
                module.setCorner(index, pos);
                CommandOutput.success(I18n.get("command.areaminer.corner_set", index + 1, pos.getX(), pos.getY(),
                        pos.getZ()));
            } catch (NumberFormatException e) {
                CommandOutput.error(I18n.get("command.areaminer.bad_number"));
            }
            return;
        }
        if (index == 1 && module.corner(0) == null) {
            CommandOutput.warning(I18n.get("command.areaminer.need_first"));
            return;
        }
        if (!module.selectLookedAs(index)) {
            CommandOutput.warning(I18n.get("notification.areaminer.no_block"));
            return;
        }
        BlockPos pos = module.corner(index);
        CommandOutput.success(I18n.get("command.areaminer.corner_set", index + 1, pos.getX(), pos.getY(), pos.getZ()));
    }

    private void depth(CommandContext ctx, AreaMiner module) {
        String value = ctx.arg(1);
        if (value == null) {
            CommandOutput.warning("Usage: @areaminer depth <layers, 0 = whole box>");
            return;
        }
        try {
            module.setDepthLimit(Integer.parseInt(value));
            CommandOutput.success(I18n.get("command.areaminer.depth_set", Integer.parseInt(value)));
        } catch (NumberFormatException e) {
            CommandOutput.error(I18n.get("command.areaminer.bad_number"));
        }
    }

    @Override
    public List<String> suggest(CommandContext ctx, int argIndex, String token) {
        String lower = token == null ? "" : token.toLowerCase(Locale.ROOT);
        if (argIndex == 1) return SUBCOMMANDS.stream().filter(s -> s.startsWith(lower)).toList();
        return List.of();
    }
}
