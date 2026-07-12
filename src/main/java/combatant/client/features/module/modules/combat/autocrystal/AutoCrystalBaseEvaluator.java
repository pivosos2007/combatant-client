/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autocrystal;

import combatant.client.util.world.ExplosionDamageUtil;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class AutoCrystalBaseEvaluator {
    private static final double CRYSTAL_MAX_DISTANCE_SQ = 144.0;
    private static final float BASE_MIN_DAMAGE = 1.5f;

    private AutoCrystalBaseEvaluator() {
    }

    public static boolean canEvaluateBaseAt(ClientLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.isInWorldBounds(pos)) {
            return false;
        }

        if (!level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above())) {
            return false;
        }

        AABB blockBox = new AABB(pos);
        for (Entity entity : level.entitiesForRendering()) {
            if (entity == null || !entity.isAlive() || entity.isRemoved() || entity instanceof ExperienceOrb) {
                continue;
            }
            if (entity.getBoundingBox().intersects(blockBox)) {
                return false;
            }
        }

        return true;
    }

    public static AutoCrystalBasePlanner.BaseData getBaseData(Context context, BlockPos pos, LivingEntity currentTarget) {
        LocalPlayer player = context.player();
        ClientLevel level = context.level();
        if (player == null || level == null || currentTarget == null || pos == null) {
            return null;
        }

        if (!canEvaluateBaseAt(level, pos)) {
            return null;
        }

        Vec3 crystalVec = AutoCrystalInteractionUtil.crystalVec(pos);
        if (context.resolvePredictedPosition(currentTarget, context.predictTicks()).distanceToSqr(crystalVec) > CRYSTAL_MAX_DISTANCE_SQ) {
            return null;
        }

        BlockHitResult interactResult = AutoCrystalInteractionUtil.getObsidianInteractResult(level, player, pos, context.placeRange());
        if (interactResult == null) {
            return null;
        }

        float damage = ExplosionDamageUtil.getCrystalDamage(currentTarget, crystalVec, context.predictTicks(), context.ignoreTerrain(), pos);
        float selfDamage = ExplosionDamageUtil.getCrystalDamage(player, crystalVec, context.selfPredictTicks(), context.ignoreTerrain(), pos);
        boolean overrideDamage = context.shouldOverrideMaxSelfDamage(damage, selfDamage);
        if (damage < BASE_MIN_DAMAGE) {
            return null;
        }
        if (selfDamage > context.maxSelfDamage() && !overrideDamage) {
            return null;
        }

        return new AutoCrystalBasePlanner.BaseData(pos, interactResult, damage, selfDamage, overrideDamage);
    }

    public interface Context {
        LocalPlayer player();

        ClientLevel level();

        Vec3 resolvePredictedPosition(LivingEntity entity, int ticks);

        boolean shouldOverrideMaxSelfDamage(float damage, float selfDamage);

        float placeRange();

        float maxSelfDamage();

        int predictTicks();

        int selfPredictTicks();

        boolean ignoreTerrain();
    }
}
