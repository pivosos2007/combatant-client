/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.surround;

import combatant.client.features.module.modules.combat.surround.SurroundPlanner.Pos;
import combatant.client.automation.placement.PlacementSlot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** World-facing helpers shared by Surround and SelfTrap: where the player stands and what to place with. */
public final class ShellBlocks {

    private ShellBlocks() {
    }

    /**
     * The feet-level cells the player's hitbox touches. {@code dynamic} follows a player straddling two
     * or four blocks; otherwise only the cell under the player's position counts.
     */
    public static Set<Pos> footprint(LocalPlayer player, boolean dynamic) {
        Set<Pos> cells = new LinkedHashSet<>();
        if (dynamic) {
            // A little shrink so brushing a neighbouring block does not count as standing in it.
            AABB box = player.getBoundingBox().deflate(0.05, 0.0, 0.05);
            int feetY = (int) Math.floor(player.getY() + 0.05);
            for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX); x++) {
                for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ); z++) {
                    cells.add(new Pos(x, feetY, z));
                }
            }
        } else {
            BlockPos feet = BlockPos.containing(player.getX(), player.getY(), player.getZ());
            cells.add(new Pos(feet.getX(), feet.getY(), feet.getZ()));
        }
        return cells;
    }

    /** Whether the cell is free for a block: air, plants, fluids. Mirrors what BlockPlacer accepts. */
    public static Predicate<Pos> replaceable(ClientLevel level) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        return pos -> level.getBlockState(cursor.set(pos.x(), pos.y(), pos.z())).canBeReplaced();
    }

    public static List<BlockPos> toBlockPos(List<Pos> cells) {
        List<BlockPos> positions = new ArrayList<>(cells.size());
        for (Pos cell : cells) positions.add(new BlockPos(cell.x(), cell.y(), cell.z()));
        return positions;
    }

    /** Offhand first, then the hotbar left to right; null when nothing fits the priority. */
    public static PlacementSlot find(LocalPlayer player, BlockPriority priority) {
        PlacementSlot preferred = PlacementSlot.find(player, stack -> isBlockOf(stack, priority::matches));
        if (preferred != null || !priority.fallsBack()) return preferred;
        return PlacementSlot.find(player, stack -> isBlockOf(stack, BlockPriority.ANY::matches));
    }

    private static boolean isBlockOf(ItemStack stack, Predicate<Block> accepts) {
        return !stack.isEmpty()
                && stack.getItem() instanceof BlockItem blockItem
                && accepts.test(blockItem.getBlock());
    }
}
