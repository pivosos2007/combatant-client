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
        int cooldownTicks = legacyProtocol ? 0 : 1;
        return AttackPressing.INSTANCE.isCooldownComplete(player, target, legacyProtocol, cooldownTicks)
                || (!legacyProtocol && AttributeSwap.shouldBypassAuraCooldownFor(player, target));
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

        int cooldownTicks = legacyProtocol ? 0 : 1;
        boolean attributeSwapBypass = !legacyProtocol && AttributeSwap.shouldBypassAuraCooldownFor(mc.player, target);
        if (!attributeSwapBypass && !AttackPressing.INSTANCE.isCooldownComplete(mc.player, target, legacyProtocol, cooldownTicks)) {
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
        if (shouldReset) {
            restoreSprintAfterAttack(mc, resolvedMode, true);
        }
        return attacked;
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
