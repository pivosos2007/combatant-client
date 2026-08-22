/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autoanchor;

import combatant.client.util.player.inventory.InventorySearchScope;
import combatant.client.util.player.inventory.InventorySwapVisibility;
import combatant.client.util.combat.CombatBlockUseUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.BlockHitResult;

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
        InteractionHand held = AutoAnchorInteractionUtil.heldAnchorHand(mc.player);
        return CombatBlockUseUtil.useHeldOrSwap(mc, held, AutoAnchorInteractionUtil::isAnchor,
                hitResult, scope, visibility, restore);
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
        return CombatBlockUseUtil.useHeldOrSwap(mc, held, AutoAnchorInteractionUtil::isGlowstone,
                hitResult, scope, visibility, restore);
    }

    public static boolean detonateAnchor(Minecraft mc,
                                         BlockHitResult hitResult,
                                         InventorySearchScope scope,
                                         InventorySwapVisibility visibility,
                                         boolean restore) {
        if (mc == null || hitResult == null || mc.player == null || mc.gameMode == null) return false;
        InteractionHand held = AutoAnchorInteractionUtil.heldDetonatorHand(mc.player);
        return CombatBlockUseUtil.useHeldOrSwap(mc, held, AutoAnchorInteractionUtil::isDetonator,
                hitResult, scope, visibility, restore);
    }

    public static boolean hasAnchor(Minecraft mc, InventorySearchScope scope) {
        if (mc == null || mc.player == null) return false;
        return CombatBlockUseUtil.hasHeldOrInventoryItem(
                AutoAnchorInteractionUtil.heldAnchorHand(mc.player), AutoAnchorInteractionUtil::isAnchor, scope);
    }

    public static boolean hasGlowstone(Minecraft mc, InventorySearchScope scope) {
        if (mc == null || mc.player == null) return false;
        return CombatBlockUseUtil.hasHeldOrInventoryItem(
                AutoAnchorInteractionUtil.heldGlowstoneHand(mc.player), AutoAnchorInteractionUtil::isGlowstone, scope);
    }
}
