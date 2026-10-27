/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc.donut;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Shared helpers for the spawner-drop modules: finding and opening spawners, and plain chest-menu clicks. */
final class SpawnerGui {

    private SpawnerGui() {
    }

    /** Nearest spawner block within {@code range} blocks of the player, or null. */
    static BlockPos nearestSpawner(Minecraft mc, int range) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return null;
        BlockPos origin = player.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-range, -range, -range), origin.offset(range, range, range))) {
            if (!mc.level.getBlockState(pos).is(Blocks.SPAWNER)) continue;
            double distance = player.distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }

    /** Right-clicks the block like a player would; true when the server-side interaction was accepted locally. */
    static boolean open(Minecraft mc, BlockPos pos) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return false;
        InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        return result != null && result.consumesAction();
    }

    /** The open chest-style menu (spawner/order/sell GUIs are all chest menus), or null. */
    static ChestMenu chest(LocalPlayer player) {
        return player != null && player.containerMenu instanceof ChestMenu menu ? menu : null;
    }

    static void click(Minecraft mc, ChestMenu menu, int slot, int button, ContainerInput type) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || slot < 0 || slot >= menu.slots.size()) return;
        mc.gameMode.handleContainerInput(menu.containerId, slot, button, type, player);
    }

    static boolean slotIs(ChestMenu menu, int slot, Item item) {
        return slot >= 0 && slot < menu.slots.size() && menu.getSlot(slot).getItem().is(item);
    }

    /** First non-empty main-inventory stack's item, or null. */
    static Item firstInventoryItem(LocalPlayer player) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) return stack.getItem();
        }
        return null;
    }
}
