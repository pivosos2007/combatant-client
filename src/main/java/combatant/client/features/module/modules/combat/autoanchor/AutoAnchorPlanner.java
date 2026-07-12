/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autoanchor;

import combatant.client.util.world.ExplosionDamageUtil;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AutoAnchorPlanner {
    private static final float ANCHOR_POWER = 5.0f;
    private static final double TARGET_MAX_DISTANCE_SQ = 144.0;
    private static final float MIN_RAW_DAMAGE = 1.5f;
    private static final int MAX_PLACE_DAMAGE_EVALUATIONS = 64;
    private static final int MAX_EXISTING_DAMAGE_EVALUATIONS = 32;
    private static final double SUPPORT_RANGE_MARGIN = 0.95;

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
                    Vec3 anchorVec = AutoAnchorInteractionUtil.anchorVec(pos);
                    if (anchorVec.distanceToSqr(center) > radiusSq) {
                        continue;
                    }

                    lastScanned++;
                    CandidateShell shell = collectShell(context, pos, anchorVec, target, predictedTarget, eyes);
                    if (shell == null) continue;
                    if (shell.existingAnchor()) {
                        existingShells.add(shell);
                    } else {
                        placeShells.add(shell);
                    }
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

        List<AutoAnchorData> place = evaluateShells(context, target, placeShells, MAX_PLACE_DAMAGE_EVALUATIONS);
        List<AutoAnchorData> explode = evaluateShells(context, target, existingShells, MAX_EXISTING_DAMAGE_EVALUATIONS);

        AutoAnchorData bestPlace = AutoAnchorDamageRules.selectBest(
                place,
                target,
                context.minDamage(),
                context.faceplaceHealth()
        );
        AutoAnchorData bestExplode = AutoAnchorDamageRules.selectBest(
                explode,
                target,
                context.minDamage(),
                context.faceplaceHealth()
        );
        return new ScanResult(bestPlace, bestExplode, lastScanned, lastCandidates, lastSafeCandidates, lastSelfDamageRejects);
    }

    public AutoAnchorData evaluate(Context context, BlockPos pos, LivingEntity target) {
        if (context == null || context.player() == null || context.level() == null || target == null || pos == null) {
            return null;
        }

        Vec3 predictedTarget = context.resolvePredictedPosition(target, context.predictTicks());
        CandidateShell shell = collectShell(
                context,
                pos,
                AutoAnchorInteractionUtil.anchorVec(pos),
                target,
                predictedTarget,
                context.player().getEyePosition()
        );
        return evaluateShell(context, target, shell);
    }

    private CandidateShell collectShell(Context context,
                                        BlockPos pos,
                                        Vec3 anchorVec,
                                        LivingEntity target,
                                        Vec3 predictedTarget,
                                        Vec3 eyes) {
        LocalPlayer player = context.player();
        ClientLevel level = context.level();
        if (player == null || level == null || target == null || pos == null || anchorVec == null || predictedTarget == null || eyes == null) {
            return null;
        }
        if (!level.isInWorldBounds(pos)) {
            return null;
        }

        double targetDistanceSq = predictedTarget.distanceToSqr(anchorVec);
        if (targetDistanceSq > TARGET_MAX_DISTANCE_SQ) {
            return null;
        }

        double quickRange = Math.max(0.0f, context.placeRange()) + SUPPORT_RANGE_MARGIN;
        double eyeDistanceSq = eyes.distanceToSqr(anchorVec);
        if (eyeDistanceSq > quickRange * quickRange) {
            return null;
        }

        float estimatedDamage = estimateMaxPreArmorDamage(predictedTarget, anchorVec);
        if (estimatedDamage < MIN_RAW_DAMAGE) {
            return null;
        }
        if (!(AutoAnchorDamageRules.shouldOverrideMinDamage(target, estimatedDamage, context.faceplaceHealth())
                || estimatedDamage > context.minDamage())) {
            return null;
        }

        BlockState state = level.getBlockState(pos);
        boolean existingAnchor = state.is(Blocks.RESPAWN_ANCHOR);
        int charges = existingAnchor && state.hasProperty(RespawnAnchorBlock.CHARGE)
                ? state.getValue(RespawnAnchorBlock.CHARGE)
                : 0;

        if (!existingAnchor) {
            if (!state.canBeReplaced()) {
                return null;
            }
            if (!context.allowAirPlace() && !AutoAnchorInteractionUtil.hasSolidPlaceSupport(level, pos)) {
                return null;
            }
        }

        return new CandidateShell(pos, anchorVec, existingAnchor, charges, targetDistanceSq, eyeDistanceSq, estimatedDamage);
    }

    private List<AutoAnchorData> evaluateShells(Context context,
                                                LivingEntity target,
                                                List<CandidateShell> shells,
                                                int maxDamageEvaluations) {
        List<AutoAnchorData> out = new ArrayList<>();
        if (shells == null || shells.isEmpty()) return out;

        int evaluated = 0;
        int limit = Math.max(1, maxDamageEvaluations);
        for (CandidateShell shell : shells) {
            if (shell == null) continue;
            if (evaluated++ >= limit) break;

            AutoAnchorData data = evaluateShell(context, target, shell);
            if (data == null) continue;
            out.add(data);
        }
        return out;
    }

    private AutoAnchorData evaluateShell(Context context, LivingEntity target, CandidateShell shell) {
        if (context == null || target == null || shell == null) {
            return null;
        }
        LocalPlayer player = context.player();
        if (player == null) {
            return null;
        }

        float damage = ExplosionDamageUtil.getExplosionDamage(
                target,
                shell.anchorVec(),
                ANCHOR_POWER,
                context.predictTicks(),
                false
        );
        if (damage < MIN_RAW_DAMAGE) {
            return null;
        }
        if (!(AutoAnchorDamageRules.shouldOverrideMinDamage(target, damage, context.faceplaceHealth())
                || damage > context.minDamage())) {
            return null;
        }
        lastCandidates++;

        float selfDamage = ExplosionDamageUtil.getExplosionDamage(
                player,
                shell.anchorVec(),
                ANCHOR_POWER,
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

        if (!shell.existingAnchor() && context.isBlockedByEntity(shell.pos())) {
            return null;
        }

        BlockHitResult interact = context.getInteractResult(shell.pos(), shell.existingAnchor());
        if (interact == null) {
            return null;
        }

        lastSafeCandidates++;
        return new AutoAnchorData(
                shell.pos(),
                interact,
                damage,
                selfDamage,
                overrideDamage,
                shell.existingAnchor(),
                shell.charges()
        );
    }

    private static float estimateMaxPreArmorDamage(Vec3 entityPos, Vec3 anchorVec) {
        if (entityPos == null || anchorVec == null) return 0.0f;
        double explosionDiameter = ANCHOR_POWER * 2.0;
        double distance = Math.max(0.0, Math.sqrt(entityPos.distanceToSqr(anchorVec)) - 1.5);
        if (distance > explosionDiameter) return 0.0f;

        double distanceDecay = 1.0 - distance / explosionDiameter;
        return (float) (((distanceDecay * distanceDecay + distanceDecay) / 2.0) * 7.0 * explosionDiameter + 1.0);
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

    public int lastSelfDamageRejects() {
        return lastSelfDamageRejects;
    }

    public interface Context {
        LocalPlayer player();

        ClientLevel level();

        Vec3 resolvePredictedPosition(LivingEntity entity, int ticks);

        BlockHitResult getInteractResult(BlockPos pos, boolean existingAnchor);

        boolean isBlockedByEntity(BlockPos pos);

        boolean shouldOverrideMaxSelfDamage(float damage, float selfDamage);

        boolean isSafe(float damage, float selfDamage, boolean overrideDamage);

        boolean allowAirPlace();

        float placeRange();

        float minDamage();

        float faceplaceHealth();

        float maxSelfDamage();

        int predictTicks();

        int selfPredictTicks();
    }

    private record CandidateShell(
            BlockPos pos,
            Vec3 anchorVec,
            boolean existingAnchor,
            int charges,
            double targetDistanceSq,
            double eyeDistanceSq,
            float estimatedDamage
    ) {
    }

    public record ScanResult(
            AutoAnchorData bestPlace,
            AutoAnchorData bestExplode,
            int scanned,
            int candidates,
            int safeCandidates,
            int selfDamageRejects
    ) {
    }
}
