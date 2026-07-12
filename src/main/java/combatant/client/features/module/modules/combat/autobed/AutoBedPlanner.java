/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autobed;

import combatant.client.util.world.ExplosionDamageUtil;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AutoBedPlanner {
    private static final float BED_POWER = 5.0f;
    private static final double TARGET_MAX_DISTANCE_SQ = 144.0;
    private static final float MIN_RAW_DAMAGE = 1.5f;
    private static final int MAX_PLACE_DAMAGE_EVALUATIONS = 64;
    private static final int MAX_EXISTING_DAMAGE_EVALUATIONS = 32;
    private static final double RANGE_MARGIN = 0.95;
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private int lastScanned;
    private int lastCandidates;
    private int lastSafeCandidates;
    private int lastSelfDamageRejects;

    public ScanResult scan(Context context, LivingEntity target, Vec3 center, int range) {
        lastScanned = 0;
        lastCandidates = 0;
        lastSafeCandidates = 0;
        lastSelfDamageRejects = 0;

        if (context == null || context.player() == null || context.level() == null || target == null || center == null) {
            return new ScanResult(null, null, 0, 0, 0, 0);
        }

        Vec3 predictedTarget = context.resolvePredictedPosition(target, context.predictTicks());
        Vec3 eyes = context.player().getEyePosition();
        BlockPos origin = BlockPos.containing(center);
        int horizontalRange = Math.max(1, range);
        int verticalRange = Math.min(horizontalRange, 3);
        double radiusSq = (double) horizontalRange * horizontalRange;

        List<CandidateShell> placeShells = new ArrayList<>();
        List<CandidateShell> existingShells = new ArrayList<>();
        for (int x = origin.getX() - horizontalRange; x <= origin.getX() + horizontalRange; x++) {
            for (int y = origin.getY() - verticalRange; y <= origin.getY() + verticalRange; y++) {
                for (int z = origin.getZ() - horizontalRange; z <= origin.getZ() + horizontalRange; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Vec3 posVec = Vec3.atCenterOf(pos);
                    if (posVec.distanceToSqr(center) > radiusSq) {
                        continue;
                    }

                    lastScanned++;
                    collectExistingShell(context, pos, predictedTarget, eyes, existingShells);
                    collectPlaceShells(context, pos, predictedTarget, eyes, placeShells);
                }
            }
        }

        Comparator<CandidateShell> byPotential = Comparator
                .comparingDouble(CandidateShell::estimatedDamage)
                .reversed()
                .thenComparingDouble(CandidateShell::targetDistanceSq)
                .thenComparingDouble(CandidateShell::eyeDistanceSq);
        placeShells.sort(byPotential);
        existingShells.sort(byPotential);

        List<AutoBedData> place = evaluateShells(context, target, placeShells, MAX_PLACE_DAMAGE_EVALUATIONS);
        List<AutoBedData> explode = evaluateShells(context, target, existingShells, MAX_EXISTING_DAMAGE_EVALUATIONS);

        AutoBedData bestPlace = AutoBedDamageRules.selectBest(
                place,
                target,
                context.minDamage(),
                context.faceplaceHealth()
        );
        AutoBedData bestExplode = AutoBedDamageRules.selectBest(
                explode,
                target,
                context.minDamage(),
                context.faceplaceHealth()
        );
        return new ScanResult(bestPlace, bestExplode, lastScanned, lastCandidates, lastSafeCandidates, lastSelfDamageRejects);
    }

    public AutoBedData evaluate(Context context, AutoBedData previous, LivingEntity target) {
        if (previous == null) return null;
        if (previous.existingBed()) {
            CandidateShell existing = collectExistingShell(context, previous.interactPos(), target);
            return evaluateShell(context, target, existing);
        }
        CandidateShell place = collectPlaceShell(context, previous.footPos(), previous.facing(), target);
        return evaluateShell(context, target, place);
    }

    private CandidateShell collectExistingShell(Context context, BlockPos pos, LivingEntity target) {
        if (context == null || context.player() == null || context.level() == null || target == null || pos == null) return null;
        Vec3 predictedTarget = context.resolvePredictedPosition(target, context.predictTicks());
        return collectExistingShell(context, pos, predictedTarget, context.player().getEyePosition());
    }

    private CandidateShell collectPlaceShell(Context context, BlockPos footPos, Direction facing, LivingEntity target) {
        if (context == null || context.player() == null || context.level() == null || target == null || footPos == null || facing == null) return null;
        Vec3 predictedTarget = context.resolvePredictedPosition(target, context.predictTicks());
        return collectPlaceShell(context, footPos, facing, predictedTarget, context.player().getEyePosition());
    }

    private void collectExistingShell(Context context,
                                      BlockPos pos,
                                      Vec3 predictedTarget,
                                      Vec3 eyes,
                                      List<CandidateShell> out) {
        CandidateShell shell = collectExistingShell(context, pos, predictedTarget, eyes);
        if (shell != null) out.add(shell);
    }

    private CandidateShell collectExistingShell(Context context,
                                                BlockPos pos,
                                                Vec3 predictedTarget,
                                                Vec3 eyes) {
        LocalPlayer player = context.player();
        ClientLevel level = context.level();
        if (player == null || level == null || pos == null || predictedTarget == null || eyes == null) return null;
        if (!level.isInWorldBounds(pos)) return null;

        BlockState state = level.getBlockState(pos);
        if (!AutoBedInteractionUtil.isBedBlock(state)) return null;

        BlockPos footPos = AutoBedInteractionUtil.resolveFootPos(state, pos);
        BlockPos headPos = AutoBedInteractionUtil.resolveHeadPos(state, pos);
        Direction facing = AutoBedInteractionUtil.bedFacing(state);
        if (footPos == null || headPos == null || facing == null) return null;

        Vec3 explosionVec = AutoBedInteractionUtil.bedExplosionVec(pos);
        return collectShell(context, footPos, headPos, facing, pos, explosionVec, predictedTarget, eyes, true);
    }

    private void collectPlaceShells(Context context,
                                    BlockPos footPos,
                                    Vec3 predictedTarget,
                                    Vec3 eyes,
                                    List<CandidateShell> out) {
        for (Direction facing : HORIZONTAL) {
            CandidateShell shell = collectPlaceShell(context, footPos, facing, predictedTarget, eyes);
            if (shell != null) out.add(shell);
        }
    }

    private CandidateShell collectPlaceShell(Context context,
                                             BlockPos footPos,
                                             Direction facing,
                                             Vec3 predictedTarget,
                                             Vec3 eyes) {
        if (context == null || context.level() == null || footPos == null || facing == null) return null;
        BlockPos headPos = footPos.relative(facing);
        if (!AutoBedInteractionUtil.canPlaceBedAt(context.level(), footPos, headPos)) {
            return null;
        }

        Vec3 footExplosion = AutoBedInteractionUtil.bedExplosionVec(footPos);
        Vec3 headExplosion = AutoBedInteractionUtil.bedExplosionVec(headPos);
        float footEstimate = estimateMaxPreArmorDamage(predictedTarget, footExplosion);
        float headEstimate = estimateMaxPreArmorDamage(predictedTarget, headExplosion);
        BlockPos interactPos = footEstimate >= headEstimate ? footPos : headPos;
        Vec3 explosionVec = footEstimate >= headEstimate ? footExplosion : headExplosion;
        return collectShell(context, footPos, headPos, facing, interactPos, explosionVec, predictedTarget, eyes, false);
    }

    private CandidateShell collectShell(Context context,
                                        BlockPos footPos,
                                        BlockPos headPos,
                                        Direction facing,
                                        BlockPos interactPos,
                                        Vec3 explosionVec,
                                        Vec3 predictedTarget,
                                        Vec3 eyes,
                                        boolean existingBed) {
        if (context == null || context.player() == null || context.level() == null || footPos == null || headPos == null
                || facing == null || interactPos == null || explosionVec == null || predictedTarget == null || eyes == null) {
            return null;
        }
        ClientLevel level = context.level();
        if (!level.isInWorldBounds(footPos) || !level.isInWorldBounds(headPos) || !level.isInWorldBounds(interactPos)) {
            return null;
        }

        double targetDistanceSq = predictedTarget.distanceToSqr(explosionVec);
        if (targetDistanceSq > TARGET_MAX_DISTANCE_SQ) {
            return null;
        }

        double quickRange = Math.max(0.0f, context.placeRange()) + RANGE_MARGIN;
        double eyeDistanceSq = eyes.distanceToSqr(existingBed ? explosionVec : AutoBedInteractionUtil.bedPairCenter(footPos, headPos));
        if (eyeDistanceSq > quickRange * quickRange) {
            return null;
        }

        float estimatedDamage = estimateMaxPreArmorDamage(predictedTarget, explosionVec);
        if (estimatedDamage < MIN_RAW_DAMAGE) {
            return null;
        }
        if (!(AutoBedDamageRules.shouldOverrideMinDamage(context.target(), estimatedDamage, context.faceplaceHealth())
                || estimatedDamage > context.minDamage())) {
            return null;
        }

        return new CandidateShell(footPos, headPos, facing, interactPos, explosionVec, existingBed, targetDistanceSq, eyeDistanceSq, estimatedDamage);
    }

    private List<AutoBedData> evaluateShells(Context context,
                                             LivingEntity target,
                                             List<CandidateShell> shells,
                                             int maxDamageEvaluations) {
        List<AutoBedData> out = new ArrayList<>();
        if (shells == null || shells.isEmpty()) return out;

        int evaluated = 0;
        int limit = Math.max(1, maxDamageEvaluations);
        for (CandidateShell shell : shells) {
            if (shell == null) continue;
            if (evaluated++ >= limit) break;
            AutoBedData data = evaluateShell(context, target, shell);
            if (data == null) continue;
            out.add(data);
        }
        return out;
    }

    private AutoBedData evaluateShell(Context context, LivingEntity target, CandidateShell shell) {
        if (context == null || target == null || shell == null) return null;
        LocalPlayer player = context.player();
        if (player == null) return null;

        float damage = ExplosionDamageUtil.getExplosionDamage(
                target,
                shell.explosionVec(),
                BED_POWER,
                context.predictTicks(),
                false
        );
        if (damage < MIN_RAW_DAMAGE) return null;
        if (!(AutoBedDamageRules.shouldOverrideMinDamage(target, damage, context.faceplaceHealth())
                || damage > context.minDamage())) {
            return null;
        }
        lastCandidates++;

        float selfDamage = ExplosionDamageUtil.getExplosionDamage(
                player,
                shell.explosionVec(),
                BED_POWER,
                context.selfPredictTicks(),
                false
        );
        boolean overrideDamage = context.shouldOverrideMaxSelfDamage(damage, selfDamage);
        if (selfDamage > context.maxSelfDamage() && !overrideDamage) {
            lastSelfDamageRejects++;
            return null;
        }
        if (!context.isSafe(damage, selfDamage, overrideDamage)) {
            return null;
        }

        if (!shell.existingBed() && context.isBlockedByEntity(shell.footPos(), shell.headPos())) {
            return null;
        }

        BlockHitResult placeInteract = null;
        if (!shell.existingBed()) {
            placeInteract = context.getPlaceInteractResult(shell.footPos(), shell.headPos());
            if (placeInteract == null) return null;
        }

        BlockHitResult explodeInteract = context.getBedInteractResult(shell.interactPos());
        if (explodeInteract == null) {
            return null;
        }

        lastSafeCandidates++;
        return new AutoBedData(
                shell.footPos(),
                shell.headPos(),
                shell.facing(),
                shell.interactPos(),
                placeInteract,
                explodeInteract,
                shell.explosionVec(),
                damage,
                selfDamage,
                overrideDamage,
                shell.existingBed()
        );
    }

    private static float estimateMaxPreArmorDamage(Vec3 entityPos, Vec3 explosionVec) {
        if (entityPos == null || explosionVec == null) return 0.0f;
        double explosionDiameter = BED_POWER * 2.0f;
        double distance = Math.sqrt(entityPos.distanceToSqr(explosionVec));
        if (distance > explosionDiameter) return 0.0f;
        double distanceDecay = 1.0 - distance / explosionDiameter;
        double impact = Math.max(0.0, distanceDecay);
        return (float) (((impact * impact + impact) / 2.0) * 7.0 * explosionDiameter + 1.0);
    }

    public interface Context {
        LocalPlayer player();
        ClientLevel level();
        LivingEntity target();
        Vec3 resolvePredictedPosition(LivingEntity entity, int ticks);
        BlockHitResult getPlaceInteractResult(BlockPos footPos, BlockPos headPos);
        BlockHitResult getBedInteractResult(BlockPos pos);
        boolean isBlockedByEntity(BlockPos footPos, BlockPos headPos);
        boolean shouldOverrideMaxSelfDamage(float damage, float selfDamage);
        boolean isSafe(float damage, float selfDamage, boolean overrideDamage);
        float minDamage();
        float faceplaceHealth();
        float maxSelfDamage();
        int predictTicks();
        int selfPredictTicks();
        float placeRange();
    }

    private record CandidateShell(
            BlockPos footPos,
            BlockPos headPos,
            Direction facing,
            BlockPos interactPos,
            Vec3 explosionVec,
            boolean existingBed,
            double targetDistanceSq,
            double eyeDistanceSq,
            float estimatedDamage
    ) {
    }

    public record ScanResult(
            AutoBedData bestPlace,
            AutoBedData bestExplode,
            int scanned,
            int candidates,
            int safeCandidates,
            int selfDamageRejects
    ) {
    }
}
