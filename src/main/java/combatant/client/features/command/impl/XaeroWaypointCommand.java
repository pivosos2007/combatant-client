/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.command.impl;

import combatant.client.compat.xaero.XaeroWaypointStore;
import combatant.client.features.command.ClientCommand;
import combatant.client.features.command.CommandContext;
import combatant.client.features.command.CommandInfo;
import combatant.client.features.command.CommandOutput;
import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@CommandInfo(
        id = "xaero",
        aliases = {"xmark", "xwp", "waypoint", "marker"},
        usage = "@xaero here [name...] | @xaero [add] <x|~> <y|~> <z|~> [name...] | @xaero remove [all|#|name] | @xaero list",
        descriptionKey = "command.xaero.description"
)
public final class XaeroWaypointCommand implements ClientCommand {
    private static final String CURRENT_COORDINATE = "~";

    @Override
    public boolean execute(CommandContext ctx) {
        String first = ctx.arg(0);
        if (first == null || first.isBlank()) {
            CommandOutput.warning("Usage: " + metadata().usage());
            return true;
        }

        String action = first.toLowerCase(Locale.ROOT);
        if (!isXaeroInstalled()) {
            CommandOutput.error("Xaero's Minimap / World Map is not installed.");
            return true;
        }

        if ("clear".equals(action) || "remove".equals(action) || "delete".equals(action) || "rm".equals(action)) {
            String selector = joinName(ctx.args(), 1);
            XaeroWaypointStore.RemoveResult result = XaeroWaypointStore.remove(selector);
            if (result.success()) {
                CommandOutput.success(result.message());
            } else {
                CommandOutput.error(result.message());
            }
            return true;
        }
        if ("list".equals(action)) {
            listMarkers();
            return true;
        }

        boolean explicitAdd = "add".equals(action) || "mark".equals(action) || "set".equals(action);
        if (isCurrentPositionKeyword(action)) {
            return addAtCurrentPosition(ctx, 1);
        }
        if (explicitAdd && isCurrentPositionKeyword(ctx.arg(1))) {
            return addAtCurrentPosition(ctx, 2);
        }

        int offset = explicitAdd ? 1 : 0;
        if (ctx.args().size() - offset < 3) {
            CommandOutput.warning("Usage: " + metadata().usage());
            return true;
        }

        boolean needsCurrentPosition = isCurrentCoordinate(ctx.arg(offset))
                || isCurrentCoordinate(ctx.arg(offset + 1))
                || isCurrentCoordinate(ctx.arg(offset + 2));
        if (needsCurrentPosition && (ctx.mc() == null || ctx.mc().player == null)) {
            CommandOutput.error("Current player position is unavailable.");
            return true;
        }

        int currentX = needsCurrentPosition ? ctx.mc().player.getBlockX() : 0;
        int currentY = needsCurrentPosition ? ctx.mc().player.getBlockY() : 0;
        int currentZ = needsCurrentPosition ? ctx.mc().player.getBlockZ() : 0;
        Integer x = parseCoordinate(ctx.arg(offset), currentX);
        Integer y = parseCoordinate(ctx.arg(offset + 1), currentY);
        Integer z = parseCoordinate(ctx.arg(offset + 2), currentZ);
        if (x == null || y == null || z == null) {
            CommandOutput.error("Invalid coordinates. Use an integer or ~ for the current coordinate.");
            return true;
        }

        String name = joinName(ctx.args(), offset + 3);
        return addMarker(x, y, z, name);
    }

    private static boolean addAtCurrentPosition(CommandContext ctx, int nameOffset) {
        if (ctx.mc() == null || ctx.mc().player == null) {
            CommandOutput.error("Current player position is unavailable.");
            return true;
        }
        return addMarker(
                ctx.mc().player.getBlockX(),
                ctx.mc().player.getBlockY(),
                ctx.mc().player.getBlockZ(),
                joinName(ctx.args(), nameOffset)
        );
    }

    private static boolean addMarker(int x, int y, int z, String name) {
        XaeroWaypointStore.AddResult result = XaeroWaypointStore.addFromCurrentDimension(x, y, z, name, true);
        if (result.success()) {
            CommandOutput.success(result.message());
        } else {
            CommandOutput.error(result.message());
        }
        return true;
    }

    @Override
    public List<String> suggest(CommandContext ctx, int argIndex, String token) {
        String lower = token == null ? "" : token.toLowerCase(Locale.ROOT);
        if (argIndex == 1) {
            List<String> out = new ArrayList<>();
            for (String value : List.of("add", "here", "remove", "clear", "list", CURRENT_COORDINATE)) {
                if (lower.isEmpty() || value.startsWith(lower)) out.add(value);
            }
            return out;
        }
        if (argIndex == 2) {
            String action = ctx.arg(0);
            if (action != null && List.of("remove", "delete", "rm", "clear").contains(action.toLowerCase(Locale.ROOT))) {
                List<String> out = new ArrayList<>();
                for (String value : List.of("all")) {
                    if (lower.isEmpty() || value.startsWith(lower)) out.add(value);
                }
                List<XaeroWaypointStore.Marker> markers = XaeroWaypointStore.markers();
                for (int i = 1; i <= markers.size(); i++) {
                    String value = Integer.toString(i);
                    if (lower.isEmpty() || value.startsWith(lower)) out.add(value);
                }
                return out;
            }
        }
        if (isCoordinateArgument(ctx, argIndex)) {
            return CURRENT_COORDINATE.startsWith(lower) ? List.of(CURRENT_COORDINATE) : List.of();
        }
        return List.of();
    }

    private static boolean isCoordinateArgument(CommandContext ctx, int argIndex) {
        String first = ctx.arg(0);
        if (first == null) return false;
        String action = first.toLowerCase(Locale.ROOT);
        if (isCurrentPositionKeyword(action)) return false;
        if ("add".equals(action) || "mark".equals(action) || "set".equals(action)) {
            return !isCurrentPositionKeyword(ctx.arg(1)) && argIndex >= 2 && argIndex <= 4;
        }
        if (List.of("remove", "delete", "rm", "clear", "list").contains(action)) return false;
        return argIndex >= 1 && argIndex <= 3;
    }

    private static void listMarkers() {
        List<XaeroWaypointStore.Marker> markers = XaeroWaypointStore.markers();
        if (markers.isEmpty()) {
            CommandOutput.send("No Combatant Xaero markers.");
            return;
        }
        CommandOutput.send("Combatant Xaero markers: " + markers.size());
        int index = 1;
        for (XaeroWaypointStore.Marker marker : markers) {
            CommandOutput.send(index++ + ". " + marker.name() + " "
                    + marker.dimensionId() + " "
                    + marker.x() + " " + marker.y() + " " + marker.z());
        }
    }

    private static boolean isXaeroInstalled() {
        FabricLoader loader = FabricLoader.getInstance();
        return loader.isModLoaded("xaerominimap") || loader.isModLoaded("xaeroworldmap");
    }

    static Integer parseCoordinate(String value, int currentCoordinate) {
        if (value == null || value.isBlank()) return null;
        if (isCurrentCoordinate(value)) return currentCoordinate;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isCurrentCoordinate(String value) {
        return CURRENT_COORDINATE.equals(value == null ? null : value.trim());
    }

    private static boolean isCurrentPositionKeyword(String value) {
        if (value == null) return false;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "here", "current", "position", "pos" -> true;
            default -> false;
        };
    }

    private static String joinName(List<String> args, int start) {
        if (args == null || start >= args.size()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.size(); i++) {
            String part = args.get(i);
            if (part == null || part.isBlank()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(part.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
