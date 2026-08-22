/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autocrystal;

import combatant.client.util.combat.ExplosionDamageRules;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

public final class AutoCrystalDamageRules {
    private AutoCrystalDamageRules() {
    }

    public static boolean shouldOverrideMinDamage(LivingEntity target, float damage, float faceplaceHealth) {
        if (target == null) {
            return false;
        }

        float targetHealth = target.getHealth();
        if (target instanceof Player playerTarget) {
            targetHealth += playerTarget.getAbsorptionAmount();
        }

        if (targetHealth - damage <= 0.0f) {
            return true;
        }

        return targetHealth <= faceplaceHealth;
    }

    public static boolean shouldOverrideMaxSelfDamage(
            LocalPlayer player,
            LivingEntity target,
            float damage,
            float selfDamage,
            float maxSelfDamage
    ) {
        if (target == null || player == null) {
            return false;
        }

        float targetHealth = target.getHealth();
        boolean targetHasTotem = false;
        if (target instanceof Player playerTarget) {
            targetHealth += playerTarget.getAbsorptionAmount();
            targetHasTotem = playerTarget.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
                    || playerTarget.getMainHandItem().is(Items.TOTEM_OF_UNDYING);
        }

        float playerHealth = player.getHealth() + player.getAbsorptionAmount();
        boolean playerHasTotem = player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
                || player.getMainHandItem().is(Items.TOTEM_OF_UNDYING);

        boolean canPop = damage > targetHealth && targetHasTotem;
        boolean canKill = damage > targetHealth && !targetHasTotem;
        boolean canPopSelf = selfDamage > playerHealth && playerHasTotem;
        boolean canKillSelf = selfDamage > playerHealth && !playerHasTotem;

        return selfDamage > maxSelfDamage && (canPop || canKill) && !canKillSelf && !canPopSelf;
    }

    public static boolean isSafe(LocalPlayer player, float selfDamage, boolean overrideDamage) {
        return ExplosionDamageRules.isSafe(player, selfDamage, overrideDamage);
    }
}
