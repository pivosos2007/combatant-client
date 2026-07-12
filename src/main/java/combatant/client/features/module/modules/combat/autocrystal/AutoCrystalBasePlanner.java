/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autocrystal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AutoCrystalBasePlanner {
    private int lastScanned;
    private int lastCandidates;
    private int lastSafeCandidates;
    private int lastWorthCandidates;
    private int lastDeltaCandidates;

    public BaseData findBest(Context context, LivingEntity target, List<Vec3> centers, int range, float minDamageDelta) {
        if (context == null || target == null || centers == null || centers.isEmpty()) return null;

        lastScanned = 0;
        lastCandidates = 0;
        lastSafeCandidates = 0;
        lastWorthCandidates = 0;
        lastDeltaCandidates = 0;

        List<BaseData> candidates = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        for (Vec3 center : centers) {
            if (center == null) continue;
            BlockPos origin = BlockPos.containing(center);
            for (int x = origin.getX() - range; x <= origin.getX() + range; x++) {
                for (int y = origin.getY() - range; y <= origin.getY() + range; y++) {
                    for (int z = origin.getZ() - range; z <= origin.getZ() + range; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!visited.add(pos)) continue;
                        lastScanned++;
                        if (!context.canEvaluateBaseAt(pos)) continue;

                        BaseData data = context.getBaseData(pos, target);
                        if (data == null) continue;
                        lastCandidates++;
                        if (!context.isSafe(data.damage(), data.selfDamage(), data.overrideDamage())) continue;
                        lastSafeCandidates++;
                        candidates.add(data);
                    }
                }
            }
        }

        return filter(context, minDamageDelta, candidates);
    }

    private BaseData filter(Context context, float minDamageDelta, List<BaseData> candidates) {
        BaseData best = null;
        float bestDamage = 0.0f;

        for (BaseData data : candidates) {
            if (!context.isWorthBaseDamage(data.damage())) continue;
            lastWorthCandidates++;

            if (!context.isEnoughBaseDamageDelta(data.damage(), minDamageDelta)) continue;
            lastDeltaCandidates++;

            if (isBetter(data, best, bestDamage)) {
                best = data;
                bestDamage = data.damage();
            }
        }

        return best;
    }

    public int lastScanned() {
        return lastScanned;
    }

    public int lastCandidates() {
        return lastCandidates;
    }

    public int lastSafeCandidates() {
        return lastSafeCandidates;
    }

    public int lastWorthCandidates() {
        return lastWorthCandidates;
    }

    public int lastDeltaCandidates() {
        return lastDeltaCandidates;
    }

    private boolean isBetter(BaseData candidate, BaseData current, float currentDamage) {
        if (candidate == null) return false;
        if (current != null
                && Math.abs(current.damage() - candidate.damage()) < 1.0f
                && current.selfDamage() > candidate.selfDamage()) {
            return true;
        }
        return candidate.damage() > currentDamage;
    }

    public interface Context {
        boolean canEvaluateBaseAt(BlockPos pos);

        BaseData getBaseData(BlockPos pos, LivingEntity target);

        boolean isSafe(float damage, float selfDamage, boolean overrideDamage);

        boolean hasCurrentCrystalPlan();

        float currentCrystalDamage();

        boolean isWorthBaseDamage(float damage);

        boolean isEnoughBaseDamageDelta(float damage, float minDamageDelta);
    }

    public record BaseData(
            BlockPos position,
            BlockHitResult hitResult,
            float damage,
            float selfDamage,
            boolean overrideDamage
    ) {
    }
}
