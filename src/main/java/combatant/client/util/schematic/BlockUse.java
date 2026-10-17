/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a plain right click on a block does something to it (opens, toggles, rings, eats, feeds, lights,
 * cycles) instead of placing the held block against it.
 *
 * <p>Rather than a list of interactive blocks that is never complete, every block class is asked whether it,
 * or any class between it and {@link BlockBehaviour}, overrides one of the two use hooks. The hooks are matched
 * by signature, not by name, so the check holds under any mapping. A block that only reacts to particular items
 * (a pumpkin to shears) counts too: the builder then sneaks, which is never wrong, only a tick slower.</p>
 */
public final class BlockUse {

    private static final Map<Class<?>, Boolean> CACHE = new ConcurrentHashMap<>();

    private BlockUse() {
    }

    /** True when a click without sneaking might use the block rather than place against it. */
    public static boolean reactsToClick(BlockState state) {
        Block block = state.getBlock();
        return CACHE.computeIfAbsent(block.getClass(), BlockUse::overridesUse);
    }

    private static boolean overridesUse(Class<?> type) {
        for (Class<?> c = type; c != null && c != BlockBehaviour.class && c != Block.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (isUseHook(method)) return true;
            }
        }
        return false;
    }

    private static final Class<?>[] WITHOUT_ITEM = {
            BlockState.class, Level.class, BlockPos.class, Player.class, BlockHitResult.class
    };
    private static final Class<?>[] WITH_ITEM = {
            ItemStack.class, BlockState.class, Level.class, BlockPos.class, Player.class, InteractionHand.class,
            BlockHitResult.class
    };

    /** {@code useWithoutItem(BlockState, Level, BlockPos, Player, BlockHitResult)} or {@code useItemOn(ItemStack, ...)}. */
    private static boolean isUseHook(Method method) {
        if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != InteractionResult.class) return false;
        Class<?>[] params = method.getParameterTypes();
        return Arrays.equals(params, WITHOUT_ITEM) || Arrays.equals(params, WITH_ITEM);
    }
}
