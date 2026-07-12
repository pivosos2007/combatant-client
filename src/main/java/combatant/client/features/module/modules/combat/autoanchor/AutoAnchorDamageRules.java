/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autoanchor;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class AutoAnchorDamageRules {
    private AutoAnchorDamageRules() {
    }

    public static boolean shouldOverrideMinDamage(LivingEntity target, float damage, float faceplaceHealth) {
        if (target == null) return false;
        if (target.getHealth() + target.getAbsorptionAmount() <= faceplaceHealth) return true;
        return damage >= target.getHealth() + target.getAbsorptionAmount() - 0.5f;
    }

    public static boolean shouldOverrideMaxSelfDamage(Player player,
                                                      LivingEntity target,
                                                      float damage,
                                                      float selfDamage,
                                                      float maxSelfDamage) {
        if (player == null || target == null) return false;
        if (selfDamage <= maxSelfDamage) return false;

        boolean targetSafe = holdsTotem(target);
        boolean playerSafe = holdsTotem(player);
        float targetHp = target.getHealth() + target.getAbsorptionAmount() - 1.0f;
        float playerHp = player.getHealth() + player.getAbsorptionAmount() - 1.0f;

        boolean canPop = damage > targetHp && targetSafe;
        boolean canKill = damage > targetHp && !targetSafe;
        boolean canPopSelf = selfDamage > playerHp && playerSafe;
        boolean canKillSelf = selfDamage > playerHp && !playerSafe;

        if (canPopSelf && canKill) return true;
        return (canPop || canKill) && !canKillSelf && !canPopSelf;
    }

    public static boolean isSafe(Player player, float selfDamage, boolean overrideDamage) {
        if (player == null) return false;
        if (overrideDamage) return true;
        return selfDamage + 0.5f <= player.getHealth() + player.getAbsorptionAmount();
    }

    public static <T extends AutoAnchorDamageCandidate> T selectBest(Iterable<T> candidates,
                                                                      LivingEntity target,
                                                                      float minDamage,
                                                                      float faceplaceHealth) {
        T best = null;
        float bestDamage = 0.0f;
        for (T data : candidates) {
            if (data == null) continue;
            if (!(shouldOverrideMinDamage(target, data.damage(), faceplaceHealth) || data.damage() > minDamage)) {
                continue;
            }

            if (best != null
                    && Math.abs(best.damage() - data.damage()) < 1.0f
                    && best.selfDamage() > data.selfDamage()) {
                best = data;
                bestDamage = data.damage();
                continue;
            }

            if (data.damage() > bestDamage) {
                best = data;
                bestDamage = data.damage();
            }
        }
        return best;
    }

    private static boolean holdsTotem(LivingEntity entity) {
        if (entity == null) return false;
        ItemStack main = entity.getMainHandItem();
        ItemStack off = entity.getOffhandItem();
        return (!main.isEmpty() && main.is(Items.TOTEM_OF_UNDYING))
                || (!off.isEmpty() && off.is(Items.TOTEM_OF_UNDYING));
    }
}
