/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.click;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import combatant.client.util.combat.LethalStrikeCalculator;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.util.player.inventory.InventorySwap;

/**
 * Shared modern attack gate logic.
 * Tracks outgoing swing/slot packets and exposes a single readiness check.
 */
public final class AttackPressing {

    public static final AttackPressing INSTANCE = new AttackPressing();
    // A modern attack is evaluated against the next server combat tick, not
    // just the already-rendered client tick. This is a bounded lookahead, not
    // permission to ignore the weapon's attack speed.
    public static final int ATTACK_LOOKAHEAD_TICKS = 1;
    private static final float READY_COOLDOWN_PROGRESS = 0.9F;
    private static final float SMASH_COOLDOWN_PROGRESS = 0.9F;

    public enum StrikeReadiness {
        WAIT, FULL, SMASH, FINISHER;

        public boolean canStrike() {
            return this != WAIT;
        }
    }
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

        return getStrikeReadiness(player, target, Math.max(0, ticks)).canStrike();
    }

    public boolean willCooldownComplete(LocalPlayer player, boolean legacyProtocol, int ticks) {
        return willCooldownComplete(player, null, legacyProtocol, ticks);
    }

    public boolean willCooldownComplete(LocalPlayer player, LivingEntity target, boolean legacyProtocol, int ticks) {
        if (player == null) return false;
        if (legacyProtocol) return true;
        return getStrikeReadiness(player, target, Math.max(0, ticks)).canStrike();
    }

    /** A wounded entity ignores weaker follow-up damage during its hurt window. */
    public boolean canHitTarget(LivingEntity target, int lookaheadTicks) {
        return target == null || (target.isAlive()
                && !target.isRemoved()
                && target.isAttackable()
                && !target.isInvulnerable()
                && target.hurtTime <= Math.max(0, lookaheadTicks));
    }

    /**
     * The same readiness classification is used by planning and execution.
     * A low-health finisher may be earlier than a fully charged attack, but
     * only if the predicted NON-critical damage is already lethal.
     */
    public StrikeReadiness getStrikeReadiness(LocalPlayer player, LivingEntity target, int lookaheadTicks) {
        if (player == null || !canHitTarget(target, lookaheadTicks)) return StrikeReadiness.WAIT;

        int safeTicks = Math.max(0, lookaheadTicks);
        float strength = player.getAttackStrengthScale(safeTicks);
        if (strength > READY_COOLDOWN_PROGRESS) return StrikeReadiness.FULL;

        if (player.getMainHandItem().is(Items.MACE)
                && MaceItem.canSmashAttack(player)
                && player.getDeltaMovement().y < 0.0
                && strength >= SMASH_COOLDOWN_PROGRESS) {
            return StrikeReadiness.SMASH;
        }

        // Never speculate about a future critical hit to justify an early swing.
        if (target != null && LethalStrikeCalculator.canKillNow(player, target, safeTicks, false)) {
            return StrikeReadiness.FINISHER;
        }
        return StrikeReadiness.WAIT;
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

        if (event.getPacket() instanceof ServerboundPunchPacket
                || event.getPacket() instanceof ServerboundSetCarriedItemPacket) {
            recalculate();
        }
    }
}
