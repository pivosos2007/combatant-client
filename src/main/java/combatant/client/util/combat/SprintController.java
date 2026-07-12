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
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.SprintControlEvent;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.movement.Sprint;
import combatant.client.mixins.accessors.LocalPlayerAccessor;

/**
 * Shared sprint state/controller layer.
 * <p>
 * AutoSprint is applied only as PlayerInput.sprint through Source.INPUT.
 * Movement/network hooks may only force sprint false when vanilla would stop it
 * or when legit attack reset blocks sprint for a few ticks.
 */
public final class SprintController {

    public static final SprintController INSTANCE = new SprintController();
    private static final int MAX_LEGIT_BLOCK_TICKS = 3;
    private boolean serverSprinting;
    private int blockSprintUntilAge = Integer.MIN_VALUE;
    private int requestedSprintInputUntilAge = Integer.MIN_VALUE;

    private SprintController() {
    }

    public void requestLegitStop(Minecraft mc, LocalPlayer player, int blockTicks) {
        if (mc == null || player == null) {
            return;
        }

        int safeBlockTicks = Math.max(1, Math.min(MAX_LEGIT_BLOCK_TICKS, blockTicks));

        serverSprinting = false;
        blockSprintUntilAge = Math.max(blockSprintUntilAge, player.tickCount + safeBlockTicks);
        requestedSprintInputUntilAge = Integer.MIN_VALUE;

        player.setSprinting(false);
        AttackUtil.sendStopSprinting(mc, player, true);
    }

    public boolean prepareLegitAttack(Minecraft mc, LocalPlayer player, int blockTicks) {
        if (player == null) {
            return true;
        }

        boolean localSprinting = player.isSprinting();
        boolean remoteSprinting = isServerSprinting(player);

        if (!localSprinting && !remoteSprinting) {
            return true;
        }

        requestLegitStop(mc, player, blockTicks);
        return false;
    }

    public boolean isServerSprinting(LocalPlayer player) {
        return player != null
                && serverSprinting
                && !player.isFallFlying()
                && !player.isInWater();
    }

    public void clearSprintBlock() {
        blockSprintUntilAge = Integer.MIN_VALUE;
    }

    public boolean isSprintBlocked() {
        return shouldBlockSprint();
    }

    public boolean canStartSprinting(LocalPlayer player) {
        if (player == null) {
            return false;
        }

        if (shouldBlockSprint()) {
            return false;
        }

        if (shouldStopSprintingForControl(player)) {
            return false;
        }

        return ((LocalPlayerAccessor) player).combatant$canStartSprinting();
    }

    /**
     * Compatibility for old callers.
     * Does not call setSprinting(true). It requests sprint input instead.
     */
    public boolean requestStartSprinting(Minecraft mc, LocalPlayer player, boolean hasForwardMotion) {
        if (mc == null || player == null || !hasForwardMotion) {
            return false;
        }

        if (shouldBlockSprint()) {
            return false;
        }

        if (shouldStopSprintingForControl(player)) {
            return false;
        }

        if (player.input == null || !player.input.hasForwardImpulse()) {
            return false;
        }

        requestedSprintInputUntilAge = Math.max(requestedSprintInputUntilAge, player.tickCount + 1);
        return true;
    }

    public boolean requestStartSprinting(Minecraft mc,
                                         LocalPlayer player,
                                         boolean hasForwardMotion,
                                         int ticks) {
        if (mc == null || player == null || !hasForwardMotion) {
            return false;
        }

        if (shouldBlockSprint() || shouldStopSprintingForControl(player)) {
            return false;
        }

        if (player.input == null || !player.input.hasForwardImpulse()) {
            return false;
        }

        int safeTicks = Math.max(1, ticks);
        requestedSprintInputUntilAge = Math.max(requestedSprintInputUntilAge, player.tickCount + safeTicks);
        return true;
    }

    @EventHandler
    private void onSprintControl(SprintControlEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || player.input == null) {
            return;
        }

        if (shouldBlockSprint() || shouldStopSprintingForControl(player)) {
            event.setSprint(false);
            return;
        }

        if (event.getSource() == SprintControlEvent.Source.INPUT) {
            if (shouldPressSprintInput(player, event.shouldSprint())) {
                event.setSprint(true);
            }
        }
    }

    private boolean shouldPressSprintInput(LocalPlayer player, boolean original) {
        if (player == null || player.input == null) {
            return original;
        }

        Sprint sprint = Modules.get(Sprint.class);
        if (sprint != null && sprint.isEnabled()) {
            if (sprint.shouldSuppressExternalVulcanSprintForControl()) {
                return false;
            }
            if (sprint.shouldAvoidForceSprintForSwimStart(player)) {
                return original;
            }
            if (player.input.hasForwardImpulse()) {
                return true;
            }
        }

        if (isSprintInputRequested(player) && player.input.hasForwardImpulse()) {
            return true;
        }

        return original;
    }

    private boolean isSprintInputRequested(LocalPlayer player) {
        if (player == null) {
            requestedSprintInputUntilAge = Integer.MIN_VALUE;
            return false;
        }

        if (requestedSprintInputUntilAge == Integer.MIN_VALUE) {
            return false;
        }

        if (player.tickCount > requestedSprintInputUntilAge) {
            requestedSprintInputUntilAge = Integer.MIN_VALUE;
            return false;
        }

        return true;
    }

    private boolean shouldVanillaStopSprinting(LocalPlayer player) {
        LocalPlayerAccessor accessor = (LocalPlayerAccessor) player;

        if (player.isSwimming()) {
            return accessor.combatant$shouldStopSwimSprinting();
        }

        return accessor.combatant$shouldStopSprinting();
    }

    public boolean shouldStopSprintingForControl(LocalPlayer player) {
        if (player == null) {
            return false;
        }

        if (shouldBlockSprint()) {
            return true;
        }

        if (!shouldVanillaStopSprinting(player)) {
            return false;
        }

        return !player.horizontalCollision || shouldStopAtHorizontalObstacle();
    }

    private boolean shouldStopAtHorizontalObstacle() {
        Sprint sprint = Modules.get(Sprint.class);
        return sprint == null || !sprint.isEnabled() || sprint.isLegitResetMode();
    }

    @EventHandler(priority = 3000)
    private void onPacketSend(PacketEvent.Send event) {
        if (!(event.getPacket() instanceof ServerboundPlayerCommandPacket command)) {
            return;
        }

        if (command.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING && shouldBlockSprint()) {
            event.cancel();
        }
    }

    @EventHandler
    private void onPacketSendPost(PacketEvent.SendPost event) {
        if (!(event.getPacket() instanceof ServerboundPlayerCommandPacket command)) {
            return;
        }

        switch (command.getAction()) {
            case START_SPRINTING -> {
                serverSprinting = true;
                clearSprintBlock();
            }
            case STOP_SPRINTING -> serverSprinting = false;
            default -> {
            }
        }
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player != null && mc.level != null) {
            return;
        }

        serverSprinting = false;
        clearSprintBlock();
        requestedSprintInputUntilAge = Integer.MIN_VALUE;
    }

    private boolean shouldBlockSprint() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null) {
            clearSprintBlock();
            return false;
        }

        if (blockSprintUntilAge == Integer.MIN_VALUE) {
            return false;
        }

        if (player.tickCount >= blockSprintUntilAge) {
            clearSprintBlock();
            return false;
        }

        return true;
    }
}
