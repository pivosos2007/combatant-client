/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.click;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.util.combat.LethalStrikeCalculator;
import combatant.client.util.player.inventory.InventorySwap;

/**
 * Shared modern attack gate logic.
 * Tracks outgoing swing/slot packets and exposes a single readiness check.
 */
public final class AttackPressing {

    public static final AttackPressing INSTANCE = new AttackPressing();
    private static final float READY_COOLDOWN_PROGRESS = 0.9F;
    private static final long MINIMUM_COOLDOWN_MS = 500L;
    private long lastClickTime = System.currentTimeMillis();
    private int clickCount = 0;

    private AttackPressing() {
    }

    public boolean isCooldownComplete(LocalPlayer player, boolean legacyProtocol, int ticks) {
        return isCooldownComplete(player, null, legacyProtocol, ticks);
    }

    public boolean isCooldownComplete(LocalPlayer player, LivingEntity target, boolean legacyProtocol, int ticks) {
        if (player == null) return false;
        if (legacyProtocol) return true;

        int safeTicks = Math.max(0, ticks);
        boolean isMace = player.getMainHandItem().is(Items.MACE);
        boolean cooldownReady = isMace
                || player.getAttackStrengthScale(safeTicks) > READY_COOLDOWN_PROGRESS
                || LethalStrikeCalculator.canKillNow(player, target, safeTicks);
        boolean minimumDelayPassed = lastClickPassed() >= MINIMUM_COOLDOWN_MS;
        return cooldownReady && minimumDelayPassed;
    }

    public boolean willCooldownComplete(LocalPlayer player, boolean legacyProtocol, int ticks) {
        return willCooldownComplete(player, null, legacyProtocol, ticks);
    }

    public boolean willCooldownComplete(LocalPlayer player, LivingEntity target, boolean legacyProtocol, int ticks) {
        if (player == null) return false;
        if (legacyProtocol) return true;

        int safeTicks = Math.max(0, ticks);
        boolean isMace = player.getMainHandItem().is(Items.MACE);
        boolean cooldownReady = isMace
                || player.getAttackStrengthScale(safeTicks) > READY_COOLDOWN_PROGRESS
                || LethalStrikeCalculator.canKillNow(player, target, safeTicks);
        boolean minimumDelayPassed = lastClickPassed() + safeTicks * 50L >= MINIMUM_COOLDOWN_MS;
        return cooldownReady && minimumDelayPassed;
    }

    public long lastClickPassed() {
        return System.currentTimeMillis() - lastClickTime;
    }

    public int clickCount() {
        return clickCount;
    }

    public void recalculate() {
        lastClickTime = System.currentTimeMillis();
        clickCount++;
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (InventorySwap.INSTANCE.isInternalSwap()) {
            return;
        }

        if (event.getPacket() instanceof ServerboundSwingPacket
                || event.getPacket() instanceof ServerboundSetCarriedItemPacket) {
            recalculate();
        }
    }
}
