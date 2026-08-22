/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autocrystal;

import combatant.client.util.world.ExplosionDamageUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class AutoCrystalPlacementPlanner {
    private static final double CRYSTAL_MAX_DISTANCE_SQ = 144.0;

    public PlaceScanResult findBestPlace(Context context, LivingEntity target, Vec3 center, int range) {
        AutoCrystalPlaceScanDebug debug = new AutoCrystalPlaceScanDebug();
        List<AutoCrystalPlaceData> scannedCandidates = getPossibleBlocks(context, target, center, range, debug);
        List<AutoCrystalPlaceData> candidates = scannedCandidates.stream()
                .filter(data -> context.isSafe(data.damage(), data.selfDamage(), data.overrideDamage()))
                .toList();
        AutoCrystalPlaceData best = candidates.isEmpty()
                ? null
                : AutoCrystalSelectionUtil.selectBest(candidates, target, context.minDamage(), context.faceplaceHealth());
        return new PlaceScanResult(
                best,
                scannedCandidates.size(),
                candidates.size(),
                debug.selfDamageRejects(),
                debug
        );
    }

    public BreakScanResult findBestCrystal(Context context, LivingEntity target) {
        List<AutoCrystalCrystalData> scannedCandidates = getPossibleCrystals(context, target);
        List<AutoCrystalCrystalData> candidates = scannedCandidates.stream()
                .filter(data -> context.isSafe(data.damage(), data.selfDamage(), data.overrideDamage()))
                .toList();
        AutoCrystalCrystalData best = candidates.isEmpty()
                ? null
                : AutoCrystalSelectionUtil.selectBest(candidates, target, context.minDamage(), context.faceplaceHealth());
        return new BreakScanResult(best, scannedCandidates.size(), candidates.size());
    }

    public AutoCrystalPlaceData getPlaceData(Context context, BlockPos pos, LivingEntity currentTarget) {
        Vec3 predictedTarget = context != null && currentTarget != null
                ? context.resolvePredictedPosition(currentTarget, context.predictTicks())
                : null;
        return getPlaceData(context, pos, currentTarget, predictedTarget, new AutoCrystalPlaceScanDebug());
    }

    public AutoCrystalCrystalData evaluateCrystal(Context context, LivingEntity currentTarget, EndCrystal crystal) {
        LocalPlayer player = context.player();
        if (player == null || currentTarget == null || crystal == null || !crystal.isAlive() || crystal.isRemoved()) {
            return null;
        }

        Vec3 crystalPos = crystal.position();
        float damage = ExplosionDamageUtil.getCrystalDamage(currentTarget, crystalPos, context.predictTicks(), context.ignoreTerrain());
        float selfDamage = ExplosionDamageUtil.getCrystalDamage(player, crystalPos, context.selfPredictTicks(), context.ignoreTerrain());
        boolean overrideDamage = context.shouldOverrideMaxSelfDamage(damage, selfDamage);
        return new AutoCrystalCrystalData(crystal, damage, selfDamage, overrideDamage);
    }

    public boolean canAttackCrystal(Context context, LivingEntity currentTarget, EndCrystal crystal) {
        LocalPlayer player = context.player();
        if (player == null || crystal == null || !crystal.isAlive() || crystal.isRemoved() || currentTarget == null) {
            return false;
        }

        Vec3 crystalPos = crystal.position();
        double maxRangeSq = Math.max(context.breakRange(), context.breakWallRange());
        maxRangeSq *= maxRangeSq;
        if (player.getEyePosition().distanceToSqr(crystalPos) > maxRangeSq) {
            return false;
        }

        AutoCrystalCrystalData data = evaluateCrystal(context, currentTarget, crystal);
        if (data == null) {
            return false;
        }
        if (!(context.shouldOverrideMinDamage(currentTarget, data.damage()) || data.damage() > context.minDamage())) {
            return false;
        }
        return (data.selfDamage() <= context.maxSelfDamage() || data.overrideDamage())
                && context.isSafe(data.damage(), data.selfDamage(), data.overrideDamage());
    }

    private List<AutoCrystalPlaceData> getPossibleBlocks(
            Context context,
            LivingEntity currentTarget,
            Vec3 center,
            int range,
            AutoCrystalPlaceScanDebug debug
    ) {
        List<AutoCrystalPlaceData> blocks = new ArrayList<>();
        if (context.player() == null || currentTarget == null || center == null) {
            return blocks;
        }

        Vec3 predictedTarget = context.resolvePredictedPosition(currentTarget, context.predictTicks());
        int minX = Mth.floor(center.x - range);
        int minY = Mth.floor(center.y - range);
        int minZ = Mth.floor(center.z - range);
        int maxX = Mth.floor(center.x + range);
        int maxY = Mth.floor(center.y + range);
        int maxZ = Mth.floor(center.z + range);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    AutoCrystalPlaceData data = getPlaceData(
                            context,
                            new BlockPos(x, y, z),
                            currentTarget,
                            predictedTarget,
                            debug
                    );
                    if (data != null) {
                        blocks.add(data);
                        debug.recordOk();
                    }
                }
            }
        }

        return blocks;
    }

    private List<AutoCrystalCrystalData> getPossibleCrystals(Context context, LivingEntity currentTarget) {
        List<AutoCrystalCrystalData> crystals = new ArrayList<>();
        LocalPlayer player = context.player();
        if (player == null || currentTarget == null) {
            return crystals;
        }

        double maxRangeSq = Math.max(context.breakRange(), context.breakWallRange());
        maxRangeSq *= maxRangeSq;

        for (Entity entity : context.entities()) {
            if (!(entity instanceof EndCrystal crystal)) {
                continue;
            }

            if (!crystal.isAlive() || crystal.isRemoved()) {
                continue;
            }

            if (context.isDeadCrystal(crystal.getId()) || context.isCrystalBlocked(crystal.getId())) {
                continue;
            }

            Vec3 crystalPos = crystal.position();
            if (player.getEyePosition().distanceToSqr(crystalPos) > maxRangeSq) {
                continue;
            }

            AutoCrystalCrystalData data = evaluateCrystal(context, currentTarget, crystal);
            if (data == null) {
                continue;
            }
            if (data.selfDamage() > context.maxSelfDamage() && !data.overrideDamage()) {
                continue;
            }

            crystals.add(data);
        }

        return crystals;
    }

    private AutoCrystalPlaceData getPlaceData(
            Context context,
            BlockPos pos,
            LivingEntity currentTarget,
            Vec3 predictedTarget,
            AutoCrystalPlaceScanDebug debug
    ) {
        LocalPlayer player = context.player();
        if (player == null || currentTarget == null || pos == null) {
            return null;
        }

        Vec3 crystalVec = AutoCrystalInteractionUtil.crystalVec(pos);
        if (predictedTarget == null || predictedTarget.distanceToSqr(crystalVec) > CRYSTAL_MAX_DISTANCE_SQ) {
            debug.recordTargetDistance();
            return null;
        }

        AutoCrystalPlaceValidationResult placeValidation = context.validatePlaceCrystal(pos, true);
        if (placeValidation != AutoCrystalPlaceValidationResult.OK) {
            debug.recordPlaceValidation(placeValidation);
            return null;
        }

        BlockHitResult interactResult = context.getInteractResult(pos, crystalVec);
        if (interactResult == null) {
            debug.recordInteractFail();
            return null;
        }

        float damage = ExplosionDamageUtil.getCrystalDamage(currentTarget, crystalVec, context.predictTicks(), context.ignoreTerrain());
        float selfDamage = ExplosionDamageUtil.getCrystalDamage(player, crystalVec, context.selfPredictTicks(), context.ignoreTerrain());
        if (debug.recordRawDamage(pos, damage, selfDamage)) {
            // The detailed exposure trace repeats the expensive ray sampling. It is useful
            // only for the highest raw-damage candidate shown in diagnostics, not for every
            // valid block in the scan cube.
            debug.recordRawDamageDebug(ExplosionDamageUtil.debugCrystalDamage(
                    currentTarget,
                    crystalVec,
                    context.predictTicks(),
                    context.ignoreTerrain()
            ));
        }
        boolean overrideDamage = context.shouldOverrideMaxSelfDamage(damage, selfDamage);
        if (!(context.shouldOverrideMinDamage(currentTarget, damage) || damage > context.minDamage())) {
            debug.recordDamageReject();
            return null;
        }
        if (selfDamage > context.maxSelfDamage() && !overrideDamage) {
            debug.recordSelfDamageReject();
            return null;
        }

        return new AutoCrystalPlaceData(pos, interactResult, damage, selfDamage, overrideDamage);
    }

    public interface Context {
        LocalPlayer player();

        Iterable<Entity> entities();

        Vec3 resolvePredictedPosition(LivingEntity entity, int ticks);

        AutoCrystalPlaceValidationResult validatePlaceCrystal(BlockPos pos, boolean calcPhase);

        BlockHitResult getInteractResult(BlockPos pos, Vec3 crystalVec);

        boolean isDeadCrystal(int id);

        boolean isCrystalBlocked(int id);

        boolean shouldOverrideMinDamage(LivingEntity target, float damage);

        boolean shouldOverrideMaxSelfDamage(float damage, float selfDamage);

        boolean isSafe(float damage, float selfDamage, boolean overrideDamage);

        float minDamage();

        float faceplaceHealth();

        float maxSelfDamage();

        float breakRange();

        float breakWallRange();

        int predictTicks();

        int selfPredictTicks();

        boolean ignoreTerrain();
    }

    public record PlaceScanResult(
            AutoCrystalPlaceData best,
            int scannedCandidates,
            int safeCandidates,
            int selfDamageRejects,
            AutoCrystalPlaceScanDebug debug
    ) {
    }

    public record BreakScanResult(
            AutoCrystalCrystalData best,
            int scannedCandidates,
            int safeCandidates
    ) {
    }
}
