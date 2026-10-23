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
import combatant.client.features.gui.schematic.SchematicActions;
import combatant.client.util.schematic.LayerStats;
import combatant.client.util.schematic.Schematic;
import combatant.client.util.schematic.SchematicLibrary;
import combatant.client.util.schematic.SchematicMaterials;
import combatant.client.util.schematic.SchematicPlacement;
import combatant.client.util.schematic.SchematicState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

@CommandInfo(
        id = "schematic",
        aliases = {"schem", "litematic", "build"},
        usage = "@schematic [menu|load <name>|origin|layer <n>|materials|supply|unload]",
        descriptionKey = "command.schematic.description"
)
public final class SchematicCommand implements ClientCommand {

    private static final int MATERIAL_LINES = 12;
    private static final List<String> SUBCOMMANDS = List.of("menu", "load", "origin", "layer", "materials", "supply", "unload");

    @Override
    public boolean execute(CommandContext ctx) {
        String sub = ctx.arg(0) == null ? "menu" : ctx.arg(0).toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu", "gui", "open" -> SchematicActions.openMenu();
            case "load" -> load(ctx);
            case "origin" -> origin(ctx.arg(1));
            case "layer" -> layer(ctx.arg(1));
            case "materials", "mats" -> materials();
            case "supply", "chest" -> supply(ctx.arg(1));
            case "unload", "clear" -> {
                SchematicActions.unload();
                CommandOutput.success("Schematic unloaded");
            }
            default -> CommandOutput.warning("Usage: " + metadata().usage());
        }
        return true;
    }

    private void load(CommandContext ctx) {
        if (ctx.args().size() < 2) {
            CommandOutput.warning("Usage: @schematic load <name>");
            return;
        }
        String name = String.join(" ", ctx.args().subList(1, ctx.args().size()));
        Path path = SchematicLibrary.INSTANCE.resolve(name);
        if (path == null) {
            CommandOutput.error("No schematic named '" + name + "' in " + SchematicLibrary.INSTANCE.directory());
            return;
        }
        CommandOutput.send("Loading " + path.getFileName() + "...");
        SchematicActions.load(path);
    }

    private void origin(String mode) {
        if (!SchematicState.INSTANCE.hasSchematic()) {
            CommandOutput.error("Load a schematic first");
            return;
        }
        boolean looked = mode != null && (mode.equalsIgnoreCase("look") || mode.equalsIgnoreCase("looked"));
        boolean ok = looked ? SchematicActions.setOriginLooked() : SchematicActions.setOriginHere();
        if (!ok) {
            CommandOutput.error(looked ? "Look at a block first" : "Not in a world");
            return;
        }
        SchematicPlacement placement = SchematicState.INSTANCE.placement();
        BlockPos origin = placement.origin();
        CommandOutput.success("Origin set to " + origin.getX() + " " + origin.getY() + " " + origin.getZ());
    }

    private void layer(String value) {
        SchematicState state = SchematicState.INSTANCE;
        if (!state.hasSchematic()) {
            CommandOutput.error("Load a schematic first");
            return;
        }
        if (value == null) {
            CommandOutput.send("Layer " + (state.layer() + 1) + " / " + state.layerCount());
            return;
        }
        switch (value.toLowerCase(Locale.ROOT)) {
            case "up", "+" -> state.stepLayer(1);
            case "down", "-" -> state.stepLayer(-1);
            default -> {
                try {
                    state.setLayer(Integer.parseInt(value) - 1);
                } catch (NumberFormatException e) {
                    CommandOutput.error("Not a layer number: " + value);
                    return;
                }
            }
        }
        CommandOutput.success("Layer " + (state.layer() + 1) + " / " + state.layerCount());
    }

    private void supply(String arg) {
        if (arg != null && (arg.equalsIgnoreCase("clear") || arg.equalsIgnoreCase("off"))) {
            combatant.client.features.module.modules.player.schematic.SupplyPoint.INSTANCE.clear();
            CommandOutput.success("Supply chest cleared");
            return;
        }
        var res = combatant.client.features.module.modules.player.schematic.SupplyPoint.INSTANCE.markLooked();
        switch (res) {
            case SET -> {
                BlockPos p = combatant.client.features.module.modules.player.schematic.SupplyPoint.INSTANCE.pos();
                CommandOutput.success("Supply container set to " + p.getX() + " " + p.getY() + " " + p.getZ());
            }
            case NOT_LOOKING -> CommandOutput.error("Look at a chest, barrel or shulker box first");
            case NOT_CONTAINER -> CommandOutput.error("That block is not a storage container");
            case NO_WORLD -> CommandOutput.error("Not in a world");
        }
    }

    private void materials() {
        SchematicState state = SchematicState.INSTANCE;
        Schematic schematic = state.schematic();
        LayerStats stats = state.stats();
        if (schematic == null || stats == null) {
            CommandOutput.error("Load a schematic first");
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        List<SchematicMaterials.Entry> total = SchematicMaterials.materials(schematic, stats.paletteTotals());
        CommandOutput.send(schematic.name() + ": " + schematic.totalBlocks() + " blocks, " + total.size() + " materials");
        for (int i = 0; i < Math.min(MATERIAL_LINES, total.size()); i++) {
            SchematicMaterials.Entry entry = total.get(i);
            int have = SchematicMaterials.countInInventory(mc.player, entry.item());
            String line = " " + new ItemStack(entry.item()).getHoverName().getString() + ": " + have + " / " + entry.count();
            if (have >= entry.count()) {
                CommandOutput.success(line);
            } else {
                CommandOutput.warning(line);
            }
        }
        if (total.size() > MATERIAL_LINES) {
            CommandOutput.send(" ... " + (total.size() - MATERIAL_LINES) + " more in @schematic menu");
        }
    }

    @Override
    public List<String> suggest(CommandContext ctx, int argIndex, String token) {
        String lower = token == null ? "" : token.toLowerCase(Locale.ROOT);
        if (argIndex == 1) return SUBCOMMANDS.stream().filter(s -> s.startsWith(lower)).toList();
        if (argIndex == 2) {
            String sub = ctx.arg(0) == null ? "" : ctx.arg(0).toLowerCase(Locale.ROOT);
            return switch (sub) {
                case "load" -> SchematicLibrary.INSTANCE.fileNames().stream()
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
                case "origin" -> List.of("here", "look").stream().filter(s -> s.startsWith(lower)).toList();
                case "layer" -> List.of("up", "down").stream().filter(s -> s.startsWith(lower)).toList();
                default -> List.of();
            };
        }
        return List.of();
    }
}
