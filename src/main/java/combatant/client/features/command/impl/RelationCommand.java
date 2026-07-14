/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.command.impl;

import combatant.client.config.ConfigSerializer;
import combatant.client.features.command.ClientCommand;
import combatant.client.features.command.CommandContext;
import combatant.client.features.command.CommandInfo;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.relations.PlayerRelations;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public abstract class RelationCommand implements ClientCommand {
    protected abstract Set<String> entries(PlayerRelations relations);

    protected abstract boolean add(PlayerRelations relations, String name);

    protected abstract boolean remove(PlayerRelations relations, String name);

    protected abstract String singular();

    @Override
    public final boolean execute(CommandContext ctx) {
        PlayerRelations relations = PlayerRelations.get();
        String action = ctx.arg(0);
        if (action == null || action.equalsIgnoreCase("list")) {
            list(entries(relations));
            return true;
        }
        if (action.equalsIgnoreCase("clear")) {
            int count = entries(relations).size();
            entries(relations).clear();
            save(relations);
            CommandOutput.success("Cleared " + count + ' ' + singular() + " entries.");
            return true;
        }

        String name = ctx.arg(1);
        if (name == null || name.isBlank()) {
            CommandOutput.warning("Usage: " + metadata().usage());
            return true;
        }

        String stored = find(entries(relations), name);
        boolean changed;
        String result;
        switch (action.toLowerCase(Locale.ROOT)) {
            case "add" -> {
                changed = stored == null && add(relations, name);
                result = changed ? "Added " + name + " as " + singular() + '.' : name + " is already listed.";
            }
            case "remove", "delete", "del" -> {
                changed = stored != null && remove(relations, stored);
                result = changed ? "Removed " + stored + " from " + singular() + " list." : name + " is not listed.";
            }
            case "toggle" -> {
                changed = stored == null ? add(relations, name) : remove(relations, stored);
                result = stored == null ? "Added " + name + " as " + singular() + '.' : "Removed " + stored + " from " + singular() + " list.";
            }
            default -> {
                CommandOutput.warning("Usage: " + metadata().usage());
                return true;
            }
        }
        if (changed) save(relations);
        if (changed) CommandOutput.success(result);
        else CommandOutput.warning(result);
        return true;
    }

    @Override
    public final List<String> suggest(CommandContext ctx, int argIndex, String token) {
        String lower = token == null ? "" : token.toLowerCase(Locale.ROOT);
        if (argIndex == 1) {
            return List.of("list", "add", "remove", "toggle", "clear").stream()
                    .filter(value -> value.startsWith(lower))
                    .toList();
        }
        if (argIndex == 2) {
            List<String> names = new ArrayList<>(CommandUtils.suggestPlayers(ctx.mc(), token));
            for (String entry : entries(PlayerRelations.get())) {
                if ((lower.isEmpty() || entry.toLowerCase(Locale.ROOT).startsWith(lower)) && !names.contains(entry)) {
                    names.add(entry);
                }
            }
            names.sort(String.CASE_INSENSITIVE_ORDER);
            return names;
        }
        return List.of();
    }

    private void list(Set<String> values) {
        if (values.isEmpty()) {
            CommandOutput.send("No " + singular() + " entries.");
            return;
        }
        List<String> sorted = values.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        CommandOutput.send(Character.toUpperCase(singular().charAt(0)) + singular().substring(1) + " (" + sorted.size() + "): "
                + String.join(", ", sorted));
    }

    private static String find(Set<String> entries, String query) {
        for (String entry : entries) {
            if (entry.equalsIgnoreCase(query)) return entry;
        }
        return null;
    }

    private static void save(PlayerRelations relations) {
        ConfigSerializer.save(relations);
    }

    @CommandInfo(
            id = "friend",
            aliases = {"friends", "f"},
            usage = "@friend [list|add|remove|toggle|clear] [player]",
            descriptionKey = "command.friend.description"
    )
    public static final class Friends extends RelationCommand {
        @Override protected Set<String> entries(PlayerRelations relations) { return relations.getFriends(); }
        @Override protected boolean add(PlayerRelations relations, String name) { return relations.addFriend(name); }
        @Override protected boolean remove(PlayerRelations relations, String name) { return relations.removeFriend(name); }
        @Override protected String singular() { return "friend"; }
    }

    @CommandInfo(
            id = "enemy",
            aliases = {"enemies", "e"},
            usage = "@enemy [list|add|remove|toggle|clear] [player]",
            descriptionKey = "command.enemy.description"
    )
    public static final class Enemies extends RelationCommand {
        @Override protected Set<String> entries(PlayerRelations relations) { return relations.getEnemies(); }
        @Override protected boolean add(PlayerRelations relations, String name) { return relations.addEnemy(name); }
        @Override protected boolean remove(PlayerRelations relations, String name) { return relations.removeEnemy(name); }
        @Override protected String singular() { return "enemy"; }
    }

    @CommandInfo(
            id = "staff",
            aliases = {"staffs", "admin"},
            usage = "@staff [list|add|remove|toggle|clear] [player]",
            descriptionKey = "command.staff.description"
    )
    public static final class Staff extends RelationCommand {
        @Override protected Set<String> entries(PlayerRelations relations) { return relations.getStaff(); }
        @Override protected boolean add(PlayerRelations relations, String name) { return relations.addStaff(name); }
        @Override protected boolean remove(PlayerRelations relations, String name) { return relations.removeStaff(name); }
        @Override protected String singular() { return "staff"; }
    }
}
