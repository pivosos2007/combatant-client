/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.placement;

import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.events.impl.WorldChangeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;

import java.util.function.BooleanSupplier;

/**
 * Minecraft side of {@link PlacementConfirmation}: remembers the prediction sequence of the last
 * block interaction sent, takes server acknowledgements from the {@code handleBlockChangedAck}
 * mixin and resolves tickets once per tick.
 *
 * <p>Resolution runs at the head of {@code RotationUpdateEvent PRE}, ahead of the automation
 * runtime, so actions ticking right after see the verdict of the previous placement.
 */
public final class PlacementTracker {

    public static final PlacementTracker INSTANCE = new PlacementTracker();

    /** Above {@code AutomationRuntime}'s 1000. */
    private static final int RESOLVE_PRIORITY = 2000;

    private final PlacementConfirmation confirmation = new PlacementConfirmation();
    private long tick;
    private int capturedSequence = -1;

    private PlacementTracker() {
    }

    /** Timeout used when a placement does not configure one: twice the ping in ticks plus slack. */
    public static int timeoutForPing(int pingMillis) {
        return Math.max(10, Math.max(0, pingMillis) / 50 * 2 + 6);
    }

    public int defaultTimeoutTicks() {
        LocalPlayer player = Minecraft.getInstance().player;
        ClientPacketListener connection = player != null ? player.connection : null;
        PlayerInfo info = connection != null ? connection.getPlayerInfo(player.getUUID()) : null;
        return timeoutForPing(info != null ? info.getLatency() : 0);
    }

    /** Call right before the interaction is sent; the next use packet's sequence is remembered. */
    public void beginCapture() {
        capturedSequence = -1;
    }

    /**
     * Starts waiting for the server's verdict on the interaction sent since {@link #beginCapture()}.
     *
     * @param timeoutTicks ticks to wait for the acknowledgement; 0 derives it from ping
     * @param satisfied    true once the world shows the intended result
     */
    public PlacementConfirmation.Ticket track(int timeoutTicks, BooleanSupplier satisfied) {
        int timeout = timeoutTicks > 0 ? timeoutTicks : defaultTimeoutTicks();
        return confirmation.track(capturedSequence, tick, timeout, satisfied);
    }

    public int pendingCount() {
        return confirmation.pendingCount();
    }

    /** Called by the {@code handleBlockChangedAck} mixin on the main thread, after the world was updated. */
    public void onAcknowledged(int sequence) {
        confirmation.onAcknowledged(sequence);
    }

    @EventHandler(priority = -10_000)
    public void onPacketSend(PacketEvent.Send event) {
        if (event.isCancelled()) return;
        if (event.getPacket() instanceof ServerboundUseItemOnPacket packet) {
            capturedSequence = packet.sequence();
        }
    }

    @EventHandler(priority = RESOLVE_PRIORITY)
    public void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE) return;
        tick++;
        if (Minecraft.getInstance().level == null) {
            confirmation.clear();
            return;
        }
        confirmation.resolve(tick);
    }

    @EventHandler(priority = 1000)
    public void onWorldChange(WorldChangeEvent event) {
        confirmation.clear();
        capturedSequence = -1;
    }
}
