/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autobed;

import combatant.client.util.player.inventory.InventoryActionKind;
import combatant.client.util.player.inventory.InventorySearchScope;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySwapRequest;
import combatant.client.util.player.inventory.InventorySwapVisibility;
import combatant.client.util.combat.RubberHandUseUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Predicate;

public final class AutoBedActionUtil {
    private AutoBedActionUtil() {
    }

    public static boolean placeBed(Minecraft mc,
                                   Object owner,
                                   BlockHitResult hitResult,
                                   InventorySearchScope scope,
                                   InventorySwapVisibility visibility,
                                   boolean restore) {
        if (mc == null || owner == null || hitResult == null || mc.player == null || mc.gameMode == null) return false;
        LocalPlayer player = mc.player;
        InteractionHand held = AutoBedInteractionUtil.heldBedHand(player);
        if (held != null) {
            return useOn(mc, held, hitResult);
        }

        return executeSwap(AutoBedInteractionUtil::isBed, scope, visibility, restore,
                () -> useOn(mc, InteractionHand.MAIN_HAND, hitResult));
    }

    public static boolean explodeBed(Minecraft mc,
                                     Object owner,
                                     BlockHitResult hitResult,
                                     InventorySearchScope scope,
                                     InventorySwapVisibility visibility,
                                     boolean restore) {
        if (mc == null || owner == null || hitResult == null || mc.player == null || mc.gameMode == null) return false;
        InteractionHand held = AutoBedInteractionUtil.heldDetonatorHand(mc.player);
        if (held != null) {
            return useOn(mc, held, hitResult);
        }
        return executeSwap(AutoBedInteractionUtil::isDetonator, scope, visibility, restore,
                () -> useOn(mc, InteractionHand.MAIN_HAND, hitResult));
    }

    public static boolean hasBed(Minecraft mc, InventorySearchScope scope) {
        if (mc == null || mc.player == null) return false;
        if (AutoBedInteractionUtil.heldBedHand(mc.player) != null) return true;
        return InventorySwap.INSTANCE.findSlotForMode(AutoBedInteractionUtil::isBed, modeFor(scope, true)).found();
    }

    private static boolean executeSwap(Predicate<ItemStack> predicate,
                                       InventorySearchScope scope,
                                       InventorySwapVisibility visibility,
                                       boolean restore,
                                       Runnable action) {
        return InventorySwap.INSTANCE.execute(InventorySwapRequest.builder(predicate, action)
                .scope(scope != null ? scope : InventorySearchScope.FULL)
                .visibility(visibility != null ? visibility : InventorySwapVisibility.SILENT)
                .restore(restore)
                .actionKind(InventoryActionKind.BLOCK_INTERACT)
                .build());
    }

    private static combatant.client.util.player.inventory.InventorySwapMode modeFor(InventorySearchScope scope, boolean silent) {
        InventorySearchScope safeScope = scope != null ? scope : InventorySearchScope.FULL;
        return switch (safeScope) {
            case HOTBAR -> silent
                    ? combatant.client.util.player.inventory.InventorySwapMode.SILENT
                    : combatant.client.util.player.inventory.InventorySwapMode.NORMAL;
            case INVENTORY -> silent
                    ? combatant.client.util.player.inventory.InventorySwapMode.INVENTORY_SILENT
                    : combatant.client.util.player.inventory.InventorySwapMode.INVENTORY_NORMAL;
            case FULL -> silent
                    ? combatant.client.util.player.inventory.InventorySwapMode.SILENT_FULL
                    : combatant.client.util.player.inventory.InventorySwapMode.NORMAL_FULL;
        };
    }

    private static boolean useOn(Minecraft mc, InteractionHand hand, BlockHitResult hitResult) {
        if (mc.player == null || mc.gameMode == null || hand == null || hitResult == null) return false;
        return RubberHandUseUtil.runBlockUse(mc, () -> {
            InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, hitResult);
            mc.player.swing(hand);
            return result != InteractionResult.FAIL;
        });
    }
}
