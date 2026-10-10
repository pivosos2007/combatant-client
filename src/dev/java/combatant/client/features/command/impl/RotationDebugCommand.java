/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.command.impl;

import combatant.client.events.EventHandler;
import combatant.client.events.Events;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.command.ClientCommand;
import combatant.client.features.command.CommandContext;
import combatant.client.features.command.CommandInfo;
import combatant.client.features.command.CommandOutput;
import combatant.client.util.aiming.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;

/**
 * Logs what actually leaves the client, for checking rotation and packet behaviour against
 * anticheat rules with {@code .omc/scripts/log_check.py}.
 *
 * <p>Lines (in latest.log via stdout):
 * {@code ROTDBG tick=<n> yaw=<f> pitch=<f> dyaw=<f> dpitch=<f> managed=<b>} for every sent
 * rotation, and {@code PKTDBG tick=<n> type=<packet> slot=<i> onGround=<b> y=<f>} for move,
 * attack, use, swing and held-slot packets. Dev builds only; enable with
 * {@code -Dcombatant.debug.rot=true} or toggle with {@code @rotdbg}.</p>
 */
@CommandInfo(
        id = "rotdbg",
        usage = "@rotdbg",
        descriptionKey = "command.rotdbg.description"
)
public final class RotationDebugCommand implements ClientCommand {
    private static final Listener LISTENER = new Listener();
    private static boolean enabled;

    public RotationDebugCommand() {
        if (Boolean.getBoolean("combatant.debug.rot")) setEnabled(true);
    }

    @Override
    public boolean execute(CommandContext ctx) {
        setEnabled(!enabled);
        CommandOutput.success("Rotation/packet debug log " + (enabled ? "on" : "off"));
        return true;
    }

    private static void setEnabled(boolean value) {
        if (value == enabled) return;
        enabled = value;
        if (value) {
            LISTENER.reset();
            Events.BUS.registerIsolated(LISTENER);
        } else {
            Events.BUS.unregister(LISTENER);
        }
    }

    // Minecraft only routes System.out.println into latest.log; printf would go to raw stdout.
    private static void log(String format, Object... args) {
        System.out.println(String.format(java.util.Locale.ROOT, format, args));
    }

    public static final class Listener {
        private float lastYaw = Float.NaN;
        private float lastPitch = Float.NaN;

        void reset() {
            lastYaw = Float.NaN;
            lastPitch = Float.NaN;
        }

        @EventHandler(priority = -10_000)
        private void onSent(PacketEvent.SendPost event) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return;
            int tick = player.tickCount;
            Packet<?> packet = event.getPacket();

            if (packet instanceof ServerboundMovePlayerPacket move) {
                if (move.hasRotation()) {
                    float yaw = move.getYRot(0.0f);
                    float pitch = move.getXRot(0.0f);
                    float dyaw = Float.isNaN(lastYaw) ? 0.0f : Math.abs(Mth.wrapDegrees(yaw - lastYaw));
                    float dpitch = Float.isNaN(lastPitch) ? 0.0f : Math.abs(pitch - lastPitch);
                    lastYaw = yaw;
                    lastPitch = pitch;
                    log("ROTDBG tick=%d yaw=%.4f pitch=%.4f dyaw=%.4f dpitch=%.4f managed=%b",
                            tick, yaw, pitch, dyaw, dpitch, RotationManager.INSTANCE.getActiveRotationTarget() != null);
                }
                log("PKTDBG tick=%d type=move pos=%b rot=%b onGround=%b y=%.5f",
                        tick, move.hasPosition(), move.hasRotation(), move.isOnGround(),
                        move.hasPosition() ? move.getY(0.0) : Double.NaN);
            } else if (packet instanceof ServerboundSetCarriedItemPacket carried) {
                log("PKTDBG tick=%d type=slot slot=%d", tick, carried.getSlot());
            } else if (packet instanceof ServerboundInteractPacket) {
                log("PKTDBG tick=%d type=interact", tick);
            } else if (packet instanceof ServerboundUseItemOnPacket || packet instanceof ServerboundUseItemPacket) {
                log("PKTDBG tick=%d type=use", tick);
            } else if (packet instanceof ServerboundSwingPacket) {
                log("PKTDBG tick=%d type=swing", tick);
            }
        }
    }
}
