/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.command;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.client.Minecraft;
import combatant.client.features.command.impl.AddonsCommand;
import combatant.client.features.command.impl.IrisCommand;
import combatant.client.features.command.impl.ProfilerCommand;
import combatant.client.features.command.impl.RuntimeCommand;
import combatant.client.features.command.impl.XaeroWaypointCommand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum CommandManager {
    ;
    private static final List<ClientCommand> COMMANDS = new ArrayList<>();
    private static boolean initialized;

    public static void init() {
        if (initialized) return;
        initialized = true;
        register(new RuntimeCommand());
        register(new AddonsCommand());
        register(new IrisCommand());
        register(new ProfilerCommand());
        register(new XaeroWaypointCommand());
    }

    public static List<ClientCommand> getCommands() {
        return Collections.unmodifiableList(COMMANDS);
    }

    public static void register(ClientCommand cmd) {
        if (cmd == null) return;
        COMMANDS.add(cmd);
    }

    public static boolean handle(String raw) {
        if (raw == null) return false;
        String trimmed = raw.trim();
        if (!isCommandLike(trimmed)) return false;
        if (!initialized) init();

        String body = trimmed.substring(1).trim();
        if (body.isEmpty()) return false;
        String[] parts = body.split("\\s+");
        if (parts.length == 0) return false;
        String name = parts[0].toLowerCase();

        ClientCommand cmd = find(name);
        if (cmd == null) return false;
        if (!cmd.isAvailable()) {
            return true; // silently ignore when command is disabled
        }

        List<String> args = new ArrayList<>();
        args.addAll(Arrays.asList(parts).subList(1, parts.length));
        CommandContext ctx = new CommandContext(Minecraft.getInstance(), raw, name, args);
        return cmd.execute(ctx);
    }

    public static List<Suggestion> suggest(String input, int cursor) {
        if (input == null) return List.of();
        int start = firstNonWhitespace(input);
        if (start < 0 || input.charAt(start) != '@') return List.of();
        if (!initialized) init();

        int cursorClamped = Math.max(start + 1, Math.min(cursor, input.length()));
        String before = input.substring(start + 1, cursorClamped);
        String[] parts = before.split("\\s+", -1);
        if (parts.length == 0) return List.of();

        int argIndex = parts.length - 1;
        String token = parts[argIndex];
        String cmdName = parts[0].toLowerCase();

        int tokenStartInBefore = before.lastIndexOf(' ');
        tokenStartInBefore = tokenStartInBefore >= 0 ? tokenStartInBefore + 1 : 0;
        int tokenStartInInput = start + 1 + tokenStartInBefore;
        int tokenEndInInput = tokenStartInInput + token.length();
        StringRange range = StringRange.between(tokenStartInInput, tokenEndInInput);

        List<String> suggestions;
        if (argIndex == 0) {
            suggestions = suggestCommandNames(token);
        } else {
            ClientCommand cmd = find(cmdName);
            if (cmd == null || !cmd.isAvailable()) return List.of();
            List<String> args = new ArrayList<>();
            args.addAll(Arrays.asList(parts).subList(1, parts.length));
            CommandContext ctx = new CommandContext(Minecraft.getInstance(), input, cmdName, args);
            suggestions = cmd.suggest(ctx, argIndex, token);
        }

        if (suggestions == null || suggestions.isEmpty()) return List.of();
        List<Suggestion> out = new ArrayList<>(suggestions.size());
        for (String s : suggestions) {
            if (s == null || s.isBlank()) continue;
            out.add(new Suggestion(range, s));
        }
        return out;
    }

    private static ClientCommand find(String name) {
        for (ClientCommand cmd : COMMANDS) {
            if (cmd.name().equalsIgnoreCase(name)) return cmd;
            for (String alias : cmd.aliases()) {
                if (alias.equalsIgnoreCase(name)) return cmd;
            }
        }
        return null;
    }

    private static boolean isCommandLike(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) continue;
            return c == '@';
        }
        return false;
    }

    private static List<String> suggestCommandNames(String token) {
        String lower = token == null ? "" : token.toLowerCase();
        List<String> out = new ArrayList<>();
        for (ClientCommand cmd : COMMANDS) {
            if (!cmd.isAvailable()) continue;
            if (lower.isEmpty() || cmd.name().startsWith(lower)) {
                out.add(cmd.name());
            }
            for (String alias : cmd.aliases()) {
                if (alias == null || alias.isBlank()) continue;
                if (lower.isEmpty() || alias.startsWith(lower)) {
                    out.add(alias);
                }
            }
        }
        return out;
    }

    private static int firstNonWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c)) return i;
        }
        return -1;
    }
}
