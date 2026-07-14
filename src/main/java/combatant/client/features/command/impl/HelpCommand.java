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
import combatant.client.features.command.CommandManager;
import combatant.client.features.command.CommandMetadata;
import combatant.client.features.command.CommandOutput;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@CommandInfo(
        id = "help",
        aliases = {"commands", "cmds"},
        usage = "@help [page|command]",
        descriptionKey = "command.help.description"
)
public final class HelpCommand implements ClientCommand {
    private static final int PAGE_SIZE = 7;
    private static final int TITLE_COLOR = 0xFF7EC8FF;
    private static final int COMMAND_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFF9AA7B5;
    private static final int ACTIVE_COLOR = 0xFF70E0A0;

    @Override
    public boolean execute(CommandContext ctx) {
        String selector = ctx.arg(0);
        if (selector != null && !selector.isBlank() && !isInteger(selector)) {
            ClientCommand command = CommandManager.find(selector.toLowerCase(Locale.ROOT));
            if (command == null || !command.isAvailable()) {
                CommandOutput.error("Command not found: @" + selector);
                return true;
            }
            showDetails(command);
            return true;
        }

        List<ClientCommand> commands = CommandManager.getCommands().stream()
                .filter(ClientCommand::isAvailable)
                .sorted(Comparator.comparing(command -> command.metadata().id(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        int pages = Math.max(1, (commands.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(1, Math.min(parseInt(selector, 1), pages));
        int from = Math.min(commands.size(), (page - 1) * PAGE_SIZE);
        int to = Math.min(commands.size(), from + PAGE_SIZE);

        MutableComponent message = Component.empty();
        message.append(Component.literal("Client commands " + commands.size())
                .withStyle(style -> style.withBold(true).withColor(TITLE_COLOR)));
        message.append(Component.literal("  -  page " + page + "/" + pages)
                .withStyle(style -> style.withColor(MUTED_COLOR)));

        for (int i = from; i < to; i++) {
            ClientCommand command = commands.get(i);
            message.append(Component.literal("\n- ").withStyle(style -> style.withColor(MUTED_COLOR)));
            message.append(commandLink(command));
            message.append(Component.literal(" - " + command.metadata().description())
                    .withStyle(style -> style.withColor(MUTED_COLOR)));
        }

        message.append(Component.literal("\n"));
        if (page > 1) {
            message.append(pageButton("< previous", page - 1));
        } else {
            message.append(Component.literal("< previous").withStyle(style -> style.withColor(0xFF59616B)));
        }
        message.append(Component.literal("    "));
        if (page < pages) {
            message.append(pageButton("next >", page + 1));
        } else {
            message.append(Component.literal("next >").withStyle(style -> style.withColor(0xFF59616B)));
        }
        CommandOutput.send(message);
        return true;
    }

    @Override
    public List<String> suggest(CommandContext ctx, int argIndex, String token) {
        if (argIndex != 1) return List.of();
        String lower = token == null ? "" : token.toLowerCase(Locale.ROOT);
        return CommandManager.getCommands().stream()
                .filter(ClientCommand::isAvailable)
                .map(command -> command.metadata().id())
                .filter(name -> lower.isEmpty() || name.startsWith(lower))
                .sorted()
                .toList();
    }

    private static MutableComponent commandLink(ClientCommand command) {
        CommandMetadata metadata = command.metadata();
        String insert = "@" + metadata.id() + " ";
        String aliases = metadata.aliases().isEmpty() ? "none" : String.join(", ", metadata.aliases());
        Component hover = Component.literal(metadata.description()
                + "\nUsage: " + metadata.usage()
                + "\nAliases: " + aliases
                + "\nClick to insert");
        return Component.literal("@" + metadata.id())
                .withStyle(style -> style
                        .withColor(COMMAND_COLOR)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.SuggestCommand(insert))
                        .withHoverEvent(new HoverEvent.ShowText(hover)));
    }

    private static MutableComponent pageButton(String label, int page) {
        return Component.literal(label)
                .withStyle(style -> style
                        .withBold(true)
                        .withColor(ACTIVE_COLOR)
                        .withClickEvent(new ClickEvent.RunCommand("@help " + page))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Open help page " + page))));
    }

    private static void showDetails(ClientCommand command) {
        CommandMetadata metadata = command.metadata();
        MutableComponent message = Component.literal("@" + metadata.id())
                .withStyle(style -> style.withBold(true).withColor(TITLE_COLOR));
        message.append(Component.literal("\n" + metadata.description()).withStyle(style -> style.withColor(COMMAND_COLOR)));
        message.append(Component.literal("\nUsage: " + metadata.usage()).withStyle(style -> style.withColor(MUTED_COLOR)));
        if (!metadata.aliases().isEmpty()) {
            message.append(Component.literal("\nAliases: " + String.join(", ", metadata.aliases()))
                    .withStyle(style -> style.withColor(MUTED_COLOR)));
        }
        CommandOutput.send(message);
    }

    private static boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
