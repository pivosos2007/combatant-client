/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.combat;

import combatant.client.util.click.AttackPressing;
import combatant.client.features.module.modules.combat.AttributeSwap;
import combatant.client.util.player.simulation.PlayerSimulationCache;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Selects attack ticks using charge readiness, predicted critical descent and target reach. */
public final class CombatAttackPlanner {
    public static final CombatAttackPlanner INSTANCE = new CombatAttackPlanner();
    private static final int AIRBORNE_LOOKAHEAD = 4;
    private static final int JUMP_LOOKAHEAD = 7;
    private static final double RANGE_MARGIN = 0.15;
    private LocalPlayer committedPlayer;
    private int committedTargetId = Integer.MIN_VALUE;
    private int expectedCritAge = -1;
    private int lastCommittedAge = -1;
    private int committedAtAge = -1;

    private CombatAttackPlanner() {
    }

    public record Plan(int attackTick, boolean critical, boolean startJump) {
        public boolean attackNow() {
            return attackTick == 0 && !startJump;
        }
        public boolean hasAttackWithin(int ticks) {
            return attackTick >= 0 && attackTick <= Math.max(0, ticks);
        }
    }

    /** Retains a predicted jump-critical window until landing or two ticks after its expected attack tick. */
    public void commitJump(LocalPlayer player, LivingEntity target, int predictedAttackTick) {
        if (player == null || target == null || predictedAttackTick <= 0) return;
        committedPlayer = player;
        committedTargetId = target.getId();
        committedAtAge = player.tickCount;
        expectedCritAge = player.tickCount + predictedAttackTick;
        lastCommittedAge = expectedCritAge + 2;
    }

    public void clearCommitment() {
        committedPlayer = null;
        committedTargetId = Integer.MIN_VALUE;
        expectedCritAge = -1;
        lastCommittedAge = -1;
        committedAtAge = -1;
    }

    private boolean isAwaitingJumpCrit(LocalPlayer player, LivingEntity target) {
        if (player != committedPlayer || target.getId() != committedTargetId) return false;
        if (player.tickCount > lastCommittedAge) {
            clearCommitment();
            return false;
        }
        if (player.onGround() && player.tickCount > committedAtAge + 1) {
            clearCommitment();
            return false;
        }
        return !player.onGround();
    }

    /** Returns a candidate attack tick. Attack execution rechecks the current strike conditions. */
    public Plan plan(LocalPlayer player, LivingEntity target, double reach,
                     boolean naturalCritAllowed, boolean ignoreSprint,
                     double forcedJumpMotion) {
        if (player == null || target == null || !target.isAlive() || target.isRemoved()) {
            return new Plan(-1, false, false);
        }

        AttackPressing pressing = AttackPressing.INSTANCE;
        int lookahead = AttackPressing.ATTACK_LOOKAHEAD_TICKS;
        AttackPressing.StrikeReadiness now = pressing.getStrikeReadiness(player, target, lookahead);
        boolean readyNow = pressing.canHitTarget(target, lookahead)
                && (now.canStrike() || AttributeSwap.shouldBypassAuraCooldownFor(player, target));
        if (readyNow && (now == AttackPressing.StrikeReadiness.SMASH
                || now == AttackPressing.StrikeReadiness.FINISHER)) {
            return new Plan(0, false, false);
        }

        if (naturalCritAllowed && (!player.isSprinting() || ignoreSprint)) {
            if (isCriticalNow(player) && readyNow && readyForCriticalAt(player, target, 0)) {
                clearCommitment();
                return new Plan(0, true, false);
            }

            boolean grounded = player.onGround();
            PlayerSimulationCache.SimulatedPlayerCache simulation = grounded
                    ? PlayerSimulationCache.getSimulationForLocalPlayerWithForcedJump(forcedJumpMotion)
                    : PlayerSimulationCache.getSimulationForLocalPlayer();
            if (simulation != null) {
                int maxTicks = grounded ? JUMP_LOOKAHEAD : AIRBORNE_LOOKAHEAD;
                for (int tick = 1; tick <= maxTicks; tick++) {
                    PlayerSimulationCache.SimulatedPlayerSnapshot snapshot = simulation.getSnapshotAt(tick);
                    if (!isCriticalSnapshot(snapshot) || !inPredictedReach(player, target, snapshot, reach, tick)) {
                        continue;
                    }
                    if (readyForCriticalAt(player, target, tick)) {
                        return new Plan(tick, true, grounded);
                    }
                }
            }
            if (isAwaitingJumpCrit(player, target)) {
                return new Plan(Math.max(1, expectedCritAge - player.tickCount), true, false);
            }
        }

        return new Plan(readyNow ? 0 : nextNormalTick(player, target), false, false);
    }

    /** Requires attack strength above {@code 0.9} at {@code ticks + 0.5} for predicted criticals. */
    private static boolean readyForCriticalAt(LocalPlayer player, LivingEntity target, int ticks) {
        return AttackPressing.INSTANCE.canHitTarget(target, ticks)
                && player.getAttackStrengthScale(ticks + 0.5f) > 0.9f;
    }

    private static int nextNormalTick(LocalPlayer player, LivingEntity target) {
        for (int tick = 1; tick <= JUMP_LOOKAHEAD; tick++) {
            if (AttackPressing.INSTANCE.getStrikeReadiness(player, target,
                    tick + AttackPressing.ATTACK_LOOKAHEAD_TICKS).canStrike()) {
                return tick;
            }
        }
        return -1;
    }

    private static boolean isCriticalNow(LocalPlayer player) {
        return !player.onGround() && player.fallDistance > 0.0f
                && player.getDeltaMovement().y <= 0.0;
    }

    private static boolean isCriticalSnapshot(PlayerSimulationCache.SimulatedPlayerSnapshot snapshot) {
        return snapshot != null && !snapshot.onGround()
                && snapshot.fallDistance() > 0.0 && snapshot.velocity().y <= 0.0;
    }

    private static boolean inPredictedReach(LocalPlayer player, LivingEntity target,
                                            PlayerSimulationCache.SimulatedPlayerSnapshot snapshot,
                                            double reach, int tick) {
        Vec3 eyes = snapshot.pos().add(0.0, player.getEyeHeight(), 0.0);
        Vec3 targetMotion = target.getDeltaMovement();
        AABB box = target.getBoundingBox().move(targetMotion.scale(Math.min(tick, 3)));
        double dx = Math.max(Math.max(box.minX - eyes.x, 0.0), eyes.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - eyes.y, 0.0), eyes.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - eyes.z, 0.0), eyes.z - box.maxZ);
        double maxReach = Math.max(0.0, reach - RANGE_MARGIN);
        return dx * dx + dy * dy + dz * dz <= maxReach * maxReach;
    }
}
