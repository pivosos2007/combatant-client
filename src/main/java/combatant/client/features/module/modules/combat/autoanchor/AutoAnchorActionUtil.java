/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autoanchor;

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
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Predicate;

public final class AutoAnchorActionUtil {
    private AutoAnchorActionUtil() {
    }

    public static boolean placeAnchor(Minecraft mc,
                                      Object owner,
                                      BlockHitResult hitResult,
                                      InventorySearchScope scope,
                                      InventorySwapVisibility visibility,
                                      boolean restore) {
        if (mc == null || owner == null || hitResult == null || mc.player == null || mc.gameMode == null) return false;
        LocalPlayer player = mc.player;
        InteractionHand held = AutoAnchorInteractionUtil.heldAnchorHand(player);
        if (held != null) {
            return useOn(mc, held, hitResult);
        }

        return executeSwap(AutoAnchorInteractionUtil::isAnchor, scope, visibility, restore,
                () -> useOn(mc, InteractionHand.MAIN_HAND, hitResult));
    }

    public static boolean explodeAnchor(Minecraft mc,
                                        Object owner,
                                        BlockHitResult hitResult,
                                        InventorySearchScope scope,
                                        InventorySwapVisibility visibility,
                                        boolean restore) {
        if (mc == null || owner == null || hitResult == null || mc.player == null || mc.level == null || mc.gameMode == null) return false;

        int charges = 0;
        var state = mc.level.getBlockState(hitResult.getBlockPos());
        if (state.hasProperty(RespawnAnchorBlock.CHARGE)) {
            charges = state.getValue(RespawnAnchorBlock.CHARGE);
        }

        if (charges <= 0) {
            return chargeAnchor(mc, hitResult, scope, visibility, restore);
        }

        return detonateAnchor(mc, hitResult, scope, visibility, restore);
    }

    public static boolean chargeAnchor(Minecraft mc,
                                       BlockHitResult hitResult,
                                       InventorySearchScope scope,
                                       InventorySwapVisibility visibility,
                                       boolean restore) {
        if (mc == null || hitResult == null || mc.player == null || mc.gameMode == null) return false;
        InteractionHand held = AutoAnchorInteractionUtil.heldGlowstoneHand(mc.player);
        if (held != null) {
            return useOn(mc, held, hitResult);
        }
        return executeSwap(AutoAnchorInteractionUtil::isGlowstone, scope, visibility, restore,
                () -> useOn(mc, InteractionHand.MAIN_HAND, hitResult));
    }

    public static boolean detonateAnchor(Minecraft mc,
                                         BlockHitResult hitResult,
                                         InventorySearchScope scope,
                                         InventorySwapVisibility visibility,
                                         boolean restore) {
        if (mc == null || hitResult == null || mc.player == null || mc.gameMode == null) return false;
        InteractionHand held = AutoAnchorInteractionUtil.heldDetonatorHand(mc.player);
        if (held != null) {
            return useOn(mc, held, hitResult);
        }
        return executeSwap(AutoAnchorInteractionUtil::isDetonator, scope, visibility, restore,
                () -> useOn(mc, InteractionHand.MAIN_HAND, hitResult));
    }

    public static boolean hasAnchor(Minecraft mc, InventorySearchScope scope) {
        if (mc == null || mc.player == null) return false;
        if (AutoAnchorInteractionUtil.heldAnchorHand(mc.player) != null) return true;
        return InventorySwap.INSTANCE.findSlotForMode(AutoAnchorInteractionUtil::isAnchor, modeFor(scope, true)).found();
    }

    public static boolean hasGlowstone(Minecraft mc, InventorySearchScope scope) {
        if (mc == null || mc.player == null) return false;
        if (AutoAnchorInteractionUtil.heldGlowstoneHand(mc.player) != null) return true;
        return InventorySwap.INSTANCE.findSlotForMode(AutoAnchorInteractionUtil::isGlowstone, modeFor(scope, true)).found();
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
