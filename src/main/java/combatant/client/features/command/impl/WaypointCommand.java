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
import combatant.client.util.waypoint.WaypointStore;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;

@CommandInfo(
        id = "waypoint",
        aliases = {"wp", "waypoints"},
        usage = "@waypoint add <name> [x y z] | @waypoint remove <name> | @waypoint list | @waypoint clear",
        descriptionKey = "command.waypoint.description"
)
public final class WaypointCommand implements ClientCommand {
    @Override
    public boolean execute(CommandContext ctx) {
        Minecraft mc = ctx.mc();
        String action = ctx.arg(0);
        if (action == null || action.isBlank()) {
            CommandOutput.warning("Usage: " + metadata().usage());
            return true;
        }
        if (mc == null || mc.level == null || mc.player == null) {
            CommandOutput.error("Join a world first.");
            return true;
        }

        WaypointStore store = WaypointStore.get();
        switch (action.toLowerCase(Locale.ROOT)) {
            case "add", "set" -> add(ctx, mc, store);
            case "remove", "delete", "rm" -> {
                String name = ctx.arg(1);
                if (name == null) {
                    CommandOutput.warning("Usage: @waypoint remove <name>");
                } else if (store.remove(mc, name)) {
                    CommandOutput.success("Removed waypoint '" + name + "'.");
                } else {
                    CommandOutput.error("No waypoint named '" + name + "'.");
                }
            }
            case "list" -> {
                List<WaypointStore.Waypoint> all = store.list(mc);
                if (all.isEmpty()) {
                    CommandOutput.send("No waypoints.");
                    break;
                }
                CommandOutput.send("Waypoints (" + all.size() + "):");
                for (WaypointStore.Waypoint w : all) {
                    CommandOutput.send(String.format(Locale.ROOT, "%s: %d %d %d (%s)",
                            w.name(), (int) Math.floor(w.x()), (int) Math.floor(w.y()), (int) Math.floor(w.z()),
                            w.dimension()));
                }
            }
            case "clear" -> CommandOutput.success("Removed " + store.clear(mc) + " waypoint(s).");
            default -> CommandOutput.warning("Usage: " + metadata().usage());
        }
        return true;
    }

    private void add(CommandContext ctx, Minecraft mc, WaypointStore store) {
        String name = ctx.arg(1);
        if (name == null || name.isBlank()) {
            CommandOutput.warning("Usage: @waypoint add <name> [x y z]");
            return;
        }
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        if (ctx.args().size() > 2) {
            if (ctx.args().size() < 5) {
                CommandOutput.warning("Usage: @waypoint add <name> [x y z]");
                return;
            }
            try {
                x = coordinate(ctx.arg(2), x);
                y = coordinate(ctx.arg(3), y);
                z = coordinate(ctx.arg(4), z);
            } catch (NumberFormatException e) {
                CommandOutput.error("Coordinates must be numbers or ~.");
                return;
            }
        }
        WaypointStore.Waypoint waypoint = new WaypointStore.Waypoint(
                name, x, y, z, mc.level.dimension().identifier().toString(),
                WaypointStore.DEFAULT_COLOR, System.currentTimeMillis());
        if (store.add(mc, waypoint)) {
            CommandOutput.success(String.format(Locale.ROOT, "Saved waypoint '%s' at %d %d %d.",
                    name, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
        } else {
            CommandOutput.error("Could not save waypoint (limit reached).");
        }
    }

    private static double coordinate(String raw, double current) {
        if (raw.equals("~")) return current;
        if (raw.startsWith("~")) return current + Double.parseDouble(raw.substring(1));
        return Double.parseDouble(raw);
    }
}
