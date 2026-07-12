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
import combatant.client.features.command.CommandOutput;
import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class XaeroWaypointCommand implements ClientCommand {
    @Override
    public String name() {
        return "xaero";
    }

    @Override
    public List<String> aliases() {
        return List.of("xmark", "xwp", "waypoint", "marker");
    }

    @Override
    public String usage() {
        return "@xaero [add] <x> <y> <z> [name...] | @xaero remove [all|#|name] | @xaero list";
    }

    @Override
    public boolean execute(CommandContext ctx) {
        String first = ctx.arg(0);
        if (first == null || first.isBlank()) {
            CommandOutput.warning("Usage: " + usage());
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

        int offset = ("add".equals(action) || "mark".equals(action) || "set".equals(action)) ? 1 : 0;
        if (ctx.args().size() - offset < 3) {
            CommandOutput.warning("Usage: " + usage());
            return true;
        }

        Integer x = parseInt(ctx.arg(offset));
        Integer y = parseInt(ctx.arg(offset + 1));
        Integer z = parseInt(ctx.arg(offset + 2));
        if (x == null || y == null || z == null) {
            CommandOutput.error("Invalid coordinates. Expected: x y z, for example @xaero 120 64 -300");
            return true;
        }

        String name = joinName(ctx.args(), offset + 3);
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
            for (String value : List.of("add", "remove", "clear", "list")) {
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
        return List.of();
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

    private static Integer parseInt(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
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
