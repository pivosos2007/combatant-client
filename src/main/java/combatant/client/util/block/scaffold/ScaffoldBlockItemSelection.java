/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.block.scaffold;

/*
 * Adapted from LiquidBounce Scaffold block selection
 * (https://github.com/CCBlueX/LiquidBounce).
 * Original copyright (c) CCBlueX.
 */

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

public enum ScaffoldBlockItemSelection {
    ;

    private static final Set<Block> DISALLOWED_BLOCKS = Set.of(
            Blocks.TNT,
            Blocks.COBWEB,
            Blocks.NETHER_PORTAL
    );

    public static boolean isValidBlock(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;

        Block block = blockItem.getBlock();
        BlockState state = block.defaultBlockState();

        if (block instanceof FallingBlock) return false;
        if (DISALLOWED_BLOCKS.contains(block)) return false;

        return !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()
                || state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    public static int findBestHotbarSlot(LocalPlayer player) {
        return findBestHotbarSlot(player, 0, -1);
    }

    public static int findBestHotbarSlot(LocalPlayer player, int doNotUseBelowCount, int preferredSlot) {
        if (player == null) return -1;

        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!isValidBlock(stack)) continue;
            if (stack.getCount() <= doNotUseBelowCount) continue;

            int score = score(stack);
            if (slot == preferredSlot) score += 1000;
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }

        return bestSlot;
    }

    private static int score(ItemStack stack) {
        Block block = ((BlockItem) stack.getItem()).getBlock();
        BlockState state = block.defaultBlockState();

        int score = 0;

        if (state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) score += 50;
        if (!(block instanceof SlabBlock) && !(block instanceof StairBlock)) score += 20;
        if (block.getFriction() <= 0.6f) score += 10;
        if (block.getSpeedFactor() >= 1.0f) score += 10;
        if (block.getJumpFactor() >= 1.0f) score += 10;

        return score + Math.min(stack.getCount(), 64);
    }

    public static boolean shouldPreferUpperHalf(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }

        Block block = blockItem.getBlock();
        return block instanceof SlabBlock || block instanceof StairBlock;
    }
}
