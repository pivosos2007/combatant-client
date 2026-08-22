/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autocrystal;

import combatant.client.util.combat.ExplosionDamageCandidate;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public final class AutoCrystalSelectionUtil {
    private AutoCrystalSelectionUtil() {
    }

    public static <T extends ExplosionDamageCandidate> T selectBest(
            List<T> candidates,
            LivingEntity target,
            float minDamage,
            float faceplaceHealth
    ) {
        T bestData = null;
        float bestVal = 0.0f;

        for (T data : candidates) {
            if (!(AutoCrystalDamageRules.shouldOverrideMinDamage(target, data.damage(), faceplaceHealth) || data.damage() > minDamage)) {
                continue;
            }

            if (bestData != null
                    && data.overrideDamage()
                    && target != null
                    && target.getAbsorptionAmount() + target.getHealth() < bestData.damage()
                    && bestData.selfDamage() < data.selfDamage()) {
                continue;
            }

            boolean shouldStopOverride = bestData != null
                    && bestData.overrideDamage()
                    && target != null
                    && data.damage() > target.getHealth() + target.getAbsorptionAmount()
                    && data.selfDamage() < bestData.selfDamage();

            float safetyComparatorDelta = shouldStopOverride ? 10.0f : 1.0f;

            if (bestData != null
                    && Math.abs(bestData.damage() - data.damage()) < safetyComparatorDelta
                    && Math.abs(bestData.selfDamage() - data.selfDamage()) > 1.0f) {
                if (bestData.selfDamage() >= data.selfDamage()) {
                    bestData = data;
                    bestVal = data.damage();
                }
            } else if (bestVal < data.damage()) {
                bestData = data;
                bestVal = data.damage();
            }
        }

        return bestData;
    }
}
