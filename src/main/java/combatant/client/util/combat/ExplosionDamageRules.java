/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/** Common safety and candidate ordering rules for bed/anchor-style explosions. */
public final class ExplosionDamageRules {
    private ExplosionDamageRules() {
    }

    public static boolean shouldOverrideMinDamage(LivingEntity target, float damage, float faceplaceHealth) {
        if (target == null) return false;
        float health = health(target);
        return health <= faceplaceHealth || damage >= health - 0.5f;
    }

    public static boolean shouldOverrideMaxSelfDamage(Player player,
                                                      LivingEntity target,
                                                      float damage,
                                                      float selfDamage,
                                                      float maxSelfDamage) {
        if (player == null || target == null || selfDamage <= maxSelfDamage) return false;

        boolean targetSafe = holdsTotem(target);
        boolean playerSafe = holdsTotem(player);
        float targetHp = health(target) - 1.0f;
        float playerHp = health(player) - 1.0f;
        boolean canPop = damage > targetHp && targetSafe;
        boolean canKill = damage > targetHp && !targetSafe;
        boolean canPopSelf = selfDamage > playerHp && playerSafe;
        boolean canKillSelf = selfDamage > playerHp && !playerSafe;

        if (canPopSelf && canKill) return true;
        return (canPop || canKill) && !canKillSelf && !canPopSelf;
    }

    public static boolean isSafe(Player player, float selfDamage, boolean overrideDamage) {
        return player != null && (overrideDamage || selfDamage + 0.5f <= health(player));
    }

    public static <T extends ExplosionDamageCandidate> T selectBest(Iterable<T> candidates,
                                                                    LivingEntity target,
                                                                    float minDamage,
                                                                    float faceplaceHealth) {
        T best = null;
        float bestDamage = 0.0f;
        for (T data : candidates) {
            if (data == null) continue;
            if (!(shouldOverrideMinDamage(target, data.damage(), faceplaceHealth) || data.damage() > minDamage)) continue;

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

    public static float health(LivingEntity entity) {
        return entity == null ? 0.0f : entity.getHealth() + entity.getAbsorptionAmount();
    }

    public static boolean holdsTotem(LivingEntity entity) {
        return entity != null && (entity.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
                || entity.getOffhandItem().is(Items.TOTEM_OF_UNDYING));
    }
}
