/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autobed;

import combatant.client.util.player.inventory.InventorySearchScope;
import combatant.client.util.player.inventory.InventorySwapVisibility;
import combatant.client.util.combat.CombatBlockUseUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

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
        InteractionHand held = AutoBedInteractionUtil.heldBedHand(mc.player);
        return CombatBlockUseUtil.useHeldOrSwap(mc, held, AutoBedInteractionUtil::isBed,
                hitResult, scope, visibility, restore);
    }

    public static boolean explodeBed(Minecraft mc,
                                     Object owner,
                                     BlockHitResult hitResult,
                                     InventorySearchScope scope,
                                     InventorySwapVisibility visibility,
                                     boolean restore) {
        if (mc == null || owner == null || hitResult == null || mc.player == null || mc.gameMode == null) return false;
        InteractionHand held = AutoBedInteractionUtil.heldDetonatorHand(mc.player);
        return CombatBlockUseUtil.useHeldOrSwap(mc, held, AutoBedInteractionUtil::isDetonator,
                hitResult, scope, visibility, restore);
    }

    public static boolean hasBed(Minecraft mc, InventorySearchScope scope) {
        if (mc == null || mc.player == null) return false;
        return CombatBlockUseUtil.hasHeldOrInventoryItem(
                AutoBedInteractionUtil.heldBedHand(mc.player), AutoBedInteractionUtil::isBed, scope);
    }
}
