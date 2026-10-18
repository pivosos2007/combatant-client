/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.LivingEntity;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.AttributeSwap;
import combatant.client.features.module.modules.movement.Sprint;
import combatant.client.util.click.AttackPressing;
import combatant.client.util.click.ClickScheduler;

import java.util.function.BooleanSupplier;

/**
 * Shared combat strike orchestration layer.
 * Keeps sprint reset, pressing gate and attack execution out of KillAura itself.
 */
public final class CombatStrikeController {

    public static final CombatStrikeController INSTANCE = new CombatStrikeController();

    private LocalPlayer lastAttacker;
    private int lastModernAttackAge = -1;
    // Avoid repeatedly sending undercharged finishers before server feedback.
    private static final int FINISHER_RETRY_TICKS = 4;
    private LocalPlayer lastFinisherAttacker;
    private LivingEntity lastFinisherTarget;
    private int lastFinisherAttackAge = -1;
    private float lastFinisherTargetHealth;

    private enum StrikeDecision { NONE, NORMAL, FINISHER }

    private CombatStrikeController() {
    }

    public boolean isAttackReady(LocalPlayer player,
                                 LivingEntity target,
                                 boolean legacyProtocol,
                                 BooleanSupplier criticalGate) {
        if (player == null || target == null) {
            return false;
        }
        if (criticalGate != null && !criticalGate.getAsBoolean()) {
            return false;
        }
        return resolveStrike(player, target, legacyProtocol) != StrikeDecision.NONE;
    }

    public boolean tryAttack(Minecraft mc,
                             LivingEntity target,
                             boolean legacyProtocol,
                             int clickCount,
                             SprintResetMode sprintMode,
                             boolean allowSprintReset,
                             BooleanSupplier criticalGate) {
        if (mc == null || mc.player == null || target == null || clickCount <= 0) {
            return false;
        }

        StrikeDecision decision = resolveStrike(mc.player, target, legacyProtocol);
        if (decision == StrikeDecision.NONE) {
            return false;
        }

        SprintResetMode resolvedMode = resolveSprintResetMode(sprintMode);
        boolean shouldReset = allowSprintReset
                && resolvedMode != SprintResetMode.NONE
                && shouldResetSprintForAttack(mc);
        if (shouldReset && !prepareSprintForAttack(mc, target, resolvedMode)) {
            return false;
        }

        if (criticalGate != null && !criticalGate.getAsBoolean()) {
            return false;
        }

        boolean attacked = executeAttacksInternal(mc, target, clickCount, legacyProtocol, false);
        if (attacked && !legacyProtocol) {
            lastAttacker = mc.player;
            lastModernAttackAge = mc.player.tickCount;
            if (decision == StrikeDecision.FINISHER) {
                lastFinisherAttacker = mc.player;
                lastFinisherTarget = target;
                lastFinisherAttackAge = mc.player.tickCount;
                lastFinisherTargetHealth = target.getHealth() + target.getAbsorptionAmount();
            }
        }
        if (shouldReset) {
            restoreSprintAfterAttack(mc, resolvedMode, true);
        }
        return attacked;
    }

    private StrikeDecision resolveStrike(LocalPlayer player, LivingEntity target, boolean legacyProtocol) {
        if (player == null || target == null || !target.isAlive() || target.isRemoved()) {
            return StrikeDecision.NONE;
        }
        if (legacyProtocol) return StrikeDecision.NORMAL;

        // One modern attack per tick, regardless of which module requested it.
        if (lastAttacker == player && lastModernAttackAge == player.tickCount) {
            return StrikeDecision.NONE;
        }

        int lookahead = AttackPressing.ATTACK_LOOKAHEAD_TICKS;
        if (!AttackPressing.INSTANCE.canHitTarget(target, lookahead)) {
            return StrikeDecision.NONE;
        }
        AttackPressing.StrikeReadiness readiness = AttackPressing.INSTANCE.getStrikeReadiness(
                player, target, lookahead);

        if (readiness == AttackPressing.StrikeReadiness.WAIT) {
            return AttributeSwap.shouldBypassAuraCooldownFor(player, target)
                    ? StrikeDecision.NORMAL : StrikeDecision.NONE;
        }
        if (readiness != AttackPressing.StrikeReadiness.FINISHER) {
            return StrikeDecision.NORMAL;
        }

        // A predicted kill is a single deliberate attempt, not permission to
        // spam one attack packet every tick while the server processes it.
        if (lastFinisherAttacker == player && lastFinisherTarget == target && lastFinisherAttackAge >= 0) {
            // No confirmed health change: the previous early finisher may have
            // missed, been blocked or is still awaiting server confirmation.
            // Wait for a normally charged attack instead of retrying guesses.
            float health = target.getHealth() + target.getAbsorptionAmount();
            if (Math.abs(health - lastFinisherTargetHealth) < 0.01f
                    || player.tickCount - lastFinisherAttackAge < FINISHER_RETRY_TICKS) {
                return StrikeDecision.NONE;
            }
        }
        return StrikeDecision.FINISHER;
    }

    public int resolveClickCount(ClickScheduler clicker, boolean legacyProtocol) {
        if (!legacyProtocol) {
            return 1;
        }

        if (clicker == null) {
            return 0;
        }

        int clickCount = clicker.getClicksAt(0);
        if (clickCount <= 0) {
            return 0;
        }

        return clickCount;
    }

    public boolean prepareSprintForAttack(Minecraft mc,
                                          LivingEntity target,
                                          SprintResetMode mode) {
        if (mc == null || mc.player == null || target == null) {
            return true;
        }

        if (!shouldResetSprintForAttack(mc)) {
            return true;
        }

        return switch (mode) {
            case DEFAULT -> true;
            case LEGIT -> SprintController.INSTANCE.prepareLegitAttack(mc, mc.player, 2);
            case PACKET -> {
                AttackUtil.sendStopSprinting(mc, mc.player);
                yield true;
            }
            case NONE -> true;
        };
    }

    public boolean shouldResetSprintForAttack(Minecraft mc) {
        if (mc == null || mc.player == null) {
            return false;
        }

        if (mc.player.isInWater() || mc.player.isUnderWater() || mc.player.isSwimming()) {
            return false;
        }

        if (mc.player.isFallFlying()) {
            return false;
        }

        return SprintController.INSTANCE.isServerSprinting(mc.player) || mc.player.isSprinting();
    }

    public void restoreSprintAfterAttack(Minecraft mc, SprintResetMode mode, boolean shouldReset) {
        if (!shouldReset || mc == null || mc.player == null) {
            return;
        }

        switch (mode) {
            case DEFAULT, NONE -> {
            }
            case LEGIT -> {
                // Legit reset: no forced instant sprint restore.
                // Sprint comes back naturally from input/autosprint after short block window.
            }
            case PACKET -> {
                if (mc.getConnection() != null) {
                    mc.getConnection().send(
                            new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_SPRINTING)
                    );
                }
            }
        }
    }

    public SprintResetMode resolveSprintResetMode(SprintResetMode mode) {
        if (mode != SprintResetMode.DEFAULT) {
            return mode;
        }

        Sprint sprint = Modules.get(Sprint.class);
        if (sprint == null || !sprint.isEnabled()) {
            return SprintResetMode.LEGIT;
        }

        return sprint.resolveCombatResetMode();
    }

    public void executeAttacks(Minecraft mc, LivingEntity target, int clickCount, boolean legacyProtocol) {
        executeAttacksInternal(mc, target, clickCount, legacyProtocol, false);
    }

    public void executeAttacks(Minecraft mc,
                               LivingEntity target,
                               int clickCount,
                               boolean legacyProtocol,
                               boolean keepSprint) {
        executeAttacksInternal(mc, target, clickCount, legacyProtocol, keepSprint);
    }

    private boolean executeAttacksInternal(Minecraft mc,
                                           LivingEntity target,
                                           int clickCount,
                                           boolean legacyProtocol,
                                           boolean keepSprint) {
        if (mc == null || mc.player == null || target == null || clickCount <= 0) {
            return false;
        }

        boolean attacked = false;
        for (int i = 0; i < clickCount; i++) {
            if (!target.isAlive() || target.isRemoved()) {
                break;
            }

            attacked |= ProtocolAttackExecutor.attackOnce(mc, mc.player, target, legacyProtocol, keepSprint);
        }
        return attacked;
    }

    public enum SprintResetMode {
        DEFAULT,
        LEGIT,
        PACKET,
        NONE
    }
}
