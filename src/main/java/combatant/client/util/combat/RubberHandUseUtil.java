/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.combat;

import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.RubberHand;
import combatant.client.util.item.FoodUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Items;

import java.util.function.BooleanSupplier;

public final class RubberHandUseUtil {
    private RubberHandUseUtil() {
    }

    /**
     * Executes a block-use action while RubberHand allows bypassing the currently active item.
     *
     * The helper mirrors RubberHand's release/restore pattern for module block interactions, so
     * AutoAnchor/AutoBed can place and detonate while shield/food active-use bypass is enabled
     * without permanently dropping the user's held use key.
     */
    public static boolean runBlockUse(Minecraft mc, BooleanSupplier action) {
        if (action == null) return false;
        if (mc == null || mc.player == null) return action.getAsBoolean();

        LocalPlayer player = mc.player;
        RubberHand rubberHand = Modules.get(RubberHand.class);
        if (!canBypassCurrentUse(mc, player, rubberHand)) {
            return action.getAsBoolean();
        }

        boolean restoreUseKey = mc.options != null && mc.options.keyUse.isDown();
        player.releaseUsingItem();
        if (mc.options != null) {
            mc.options.keyUse.setDown(false);
        }

        try {
            return action.getAsBoolean();
        } finally {
            if (restoreUseKey && mc.options != null) {
                mc.options.keyUse.setDown(true);
            }
        }
    }

    public static boolean canBypassCurrentUse(Minecraft mc) {
        if (mc == null || mc.player == null) return false;
        return canBypassCurrentUse(mc, mc.player, Modules.get(RubberHand.class));
    }

    public static boolean isUsingShield(LocalPlayer player) {
        if (player == null || !player.isUsingItem()) return false;
        return player.getUseItem().is(Items.SHIELD) || player.getOffhandItem().is(Items.SHIELD);
    }

    public static boolean isUsingFood(LocalPlayer player) {
        return player != null && player.isUsingItem() && FoodUtil.isFood(player.getUseItem());
    }

    private static boolean canBypassCurrentUse(Minecraft mc, LocalPlayer player, RubberHand rubberHand) {
        if (mc == null
                || mc.options == null
                || player == null
                || rubberHand == null
                || !rubberHand.isEnabled()
                || !player.isUsingItem()) {
            return false;
        }

        if (isUsingShield(player)) {
            return rubberHand.canBypassShieldUse();
        }

        if (isUsingFood(player)) {
            return rubberHand.canBypassFoodUse();
        }

        return false;
    }
}
