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
import combatant.client.features.module.ModuleCategory;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Locale;

@CommandInfo(
        id = "modules",
        aliases = {"modulelist", "mods"},
        usage = "@modules [all|enabled|combat|movement|player|visuals|misc] [page]",
        descriptionKey = "command.modules.description"
)
public final class ModulesCommand implements ClientCommand {
    private static final int PAGE_SIZE = 10;

    @Override
    public boolean execute(CommandContext ctx) {
        String filter = ctx.arg(0) == null ? "all" : ctx.arg(0).toLowerCase(Locale.ROOT);
        List<Module> modules = CommandUtils.sortedModules().stream()
                .filter(module -> accepts(module, filter))
                .toList();
        if (!filter.equals("all") && !filter.equals("enabled") && parseCategory(filter) == null) {
            CommandOutput.warning("Usage: " + metadata().usage());
            return true;
        }
        int pages = Math.max(1, (modules.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(1, Math.min(parsePage(ctx.arg(1)), pages));
        int from = Math.min(modules.size(), (page - 1) * PAGE_SIZE);
        int to = Math.min(modules.size(), from + PAGE_SIZE);

        MutableComponent message = Component.literal("Modules " + filter + " (" + modules.size() + ") — " + page + "/" + pages)
                .withStyle(style -> style.withBold(true).withColor(0xFF7EC8FF));
        for (int i = from; i < to; i++) {
            Module module = modules.get(i);
            int color = module.isEnabled() ? 0xFF70E0A0 : 0xFF9AA7B5;
            message.append(Component.literal("\n• "));
            message.append(Component.literal(module.getDisplayName())
                    .withStyle(style -> style
                            .withColor(color)
                            .withClickEvent(new ClickEvent.RunCommand("@toggle " + module.name()))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal(
                                    module.getDescription() + "\nClick to toggle\nID: " + module.name())))));
            message.append(Component.literal(" [" + (module.isEnabled() ? "ON" : "OFF") + "]")
                    .withStyle(style -> style.withColor(color)));
        }
        if (pages > 1) {
            message.append(Component.literal("\n"));
            if (page > 1) message.append(pageButton("‹", filter, page - 1));
            message.append(Component.literal("  page " + page + "/" + pages + "  "));
            if (page < pages) message.append(pageButton("›", filter, page + 1));
        }
        CommandOutput.send(message);
        return true;
    }

    @Override
    public List<String> suggest(CommandContext ctx, int argIndex, String token) {
        if (argIndex != 1) return List.of();
        String lower = token == null ? "" : token.toLowerCase(Locale.ROOT);
        return List.of("all", "enabled", "combat", "movement", "player", "visuals", "misc").stream()
                .filter(value -> value.startsWith(lower)).toList();
    }

    private static boolean accepts(Module module, String filter) {
        if (filter.equals("all")) return true;
        if (filter.equals("enabled")) return module.isEnabled();
        ModuleCategory category = parseCategory(filter);
        return category != null && module.getCategory() == category;
    }

    private static ModuleCategory parseCategory(String value) {
        try {
            return ModuleCategory.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static int parsePage(String value) {
        try {
            return value == null ? 1 : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static MutableComponent pageButton(String label, String filter, int page) {
        return Component.literal(label).withStyle(style -> style
                .withBold(true)
                .withColor(0xFF70E0A0)
                .withClickEvent(new ClickEvent.RunCommand("@modules " + filter + " " + page)));
    }
}
