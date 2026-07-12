/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.network.protocol.status.ServerboundStatusRequestPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import combatant.client.events.EventHandler;
import combatant.client.events.Events;
import combatant.client.events.impl.BlinkPacketEvent;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Packet queue/flush backend adapted from LiquidBounce's BlinkManager.
 */
public final class BlinkManager {
    public static final BlinkManager INSTANCE = new BlinkManager();

    private static final ThreadLocal<Boolean> SILENT_PACKET_HANDLING = ThreadLocal.withInitial(() -> false);

    private final ConcurrentLinkedQueue<PacketSnapshot> packetQueue = new ConcurrentLinkedQueue<>();

    private BlinkManager() {
    }

    public static boolean isSilentlyHandlingPackets() {
        return SILENT_PACKET_HANDLING.get();
    }

    private static void runSilently(Runnable action) {
        boolean previous = SILENT_PACKET_HANDLING.get();
        SILENT_PACKET_HANDLING.set(true);
        try {
            action.run();
        } finally {
            SILENT_PACKET_HANDLING.set(previous);
        }
    }

    private static void sendPacketSilently(Packet<?> packet) {
        Connection connection = connection();
        if (connection != null && connection.isConnected()) {
            connection.send(packet);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void handlePacketSilently(Packet<?> packet) {
        Connection connection = connection();
        PacketListener listener = connection != null ? connection.getPacketListener() : null;
        if (listener == null) {
            return;
        }

        ((Packet) packet).handle(listener);
    }

    private static Connection connection() {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener networkHandler = mc != null ? mc.getConnection() : null;
        return networkHandler != null ? networkHandler.getConnection() : null;
    }

    private static boolean hasOpenConnection() {
        Connection connection = connection();
        return connection != null && connection.isConnected();
    }

    private static boolean shouldAlwaysPass(Packet<?> packet) {
        return packet instanceof ClientIntentionPacket
                || packet instanceof ServerboundStatusRequestPacket
                || packet instanceof ServerboundPingRequestPacket
                || packet instanceof ServerboundChatPacket
                || packet instanceof ServerboundChatCommandPacket
                || packet instanceof ServerboundPongPacket
                || packet instanceof ServerboundKeepAlivePacket
                || packet instanceof ServerboundAcceptTeleportationPacket
                || packet instanceof ServerboundChunkBatchReceivedPacket
                || packet instanceof ServerboundConfigurationAcknowledgedPacket
                || packet instanceof ServerboundChatAckPacket
                || packet instanceof ClientboundSystemChatPacket
                || packet instanceof ServerboundResourcePackPacket
                || isPlayerHurtSound(packet);
    }

    private static boolean isPlayerHurtSound(Packet<?> packet) {
        return packet instanceof ClientboundSoundPacket sound && sound.getSound().value() == SoundEvents.PLAYER_HURT
                || packet instanceof ClientboundSoundEntityPacket entitySound
                && entitySound.getSound().value() == SoundEvents.PLAYER_HURT;
    }

    private static boolean shouldFlushAndPass(Packet<?> packet, TransferOrigin origin) {
        if (packet instanceof ClientboundPlayerPositionPacket
                || packet instanceof ClientboundDisconnectPacket
                || packet instanceof ClientboundLoginDisconnectPacket) {
            return true;
        }
        return origin == TransferOrigin.INCOMING
                && packet instanceof ClientboundSetHealthPacket health
                && health.getHealth() <= 0.0f;
    }

    public ConcurrentLinkedQueue<PacketSnapshot> getPacketQueue() {
        return packetQueue;
    }

    public boolean isLagging() {
        return !packetQueue.isEmpty();
    }

    public List<Vec3> getQueuedMovePositions() {
        List<Vec3> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        double currentX = mc != null && mc.player != null ? mc.player.getX() : 0.0;
        double currentY = mc != null && mc.player != null ? mc.player.getY() : 0.0;
        double currentZ = mc != null && mc.player != null ? mc.player.getZ() : 0.0;

        for (PacketSnapshot snapshot : packetQueue) {
            if (snapshot.packet() instanceof ServerboundMovePlayerPacket move && move.hasPosition()) {
                currentX = move.getX(currentX);
                currentY = move.getY(currentY);
                currentZ = move.getZ(currentZ);
                out.add(new Vec3(currentX, currentY, currentZ));
            }
        }
        return out;
    }

    @EventHandler(priority = -10000)
    public void onTick(GameTickEvent event) {
        if (!hasOpenConnection()) {
            packetQueue.clear();
            return;
        }

        if (fireEvent(null, TransferOrigin.OUTGOING) == Action.FLUSH) {
            flush(TransferOrigin.OUTGOING);
        }
        if (fireEvent(null, TransferOrigin.INCOMING) == Action.FLUSH) {
            flush(TransferOrigin.INCOMING);
        }
    }

    @EventHandler(priority = -10000)
    public void onPacket(PacketEvent event) {
        if (event == null || event.isCancelled() || !event.isOriginal()) {
            return;
        }

        TransferOrigin origin = event.getOrigin();
        if (origin == null) {
            return;
        }

        Packet<?> packet = event.getPacket();
        Action action = fireEvent(packet, origin);
        if (action == Action.FLUSH) {
            flush(origin);
            return;
        }
        if (action == Action.PASS || shouldAlwaysPass(packet)) {
            return;
        }
        if (shouldFlushAndPass(packet, origin)) {
            flush(origin);
            return;
        }

        event.cancel();
        packetQueue.add(new PacketSnapshot(packet, origin, System.currentTimeMillis()));
    }

    public void flush(TransferOrigin origin) {
        flush(snapshot -> snapshot.origin() == origin);
    }

    public void sendSilently(Packet<?> packet) {
        if (packet == null) {
            return;
        }
        runSilently(() -> sendPacketSilently(packet));
    }

    public void flush(Predicate<PacketSnapshot> flushWhen) {
        if (flushWhen == null) {
            return;
        }

        packetQueue.removeIf(snapshot -> {
            if (!flushWhen.test(snapshot)) {
                return false;
            }
            flushSnapshot(snapshot);
            return true;
        });
    }

    public void flushMovePackets(int movePacketCount) {
        int counter = 0;
        var iterator = packetQueue.iterator();
        while (iterator.hasNext()) {
            PacketSnapshot snapshot = iterator.next();
            Packet<?> packet = snapshot.packet();

            if (packet instanceof ServerboundMovePlayerPacket move && move.hasPosition()) {
                counter++;
            }

            flushSnapshot(snapshot);
            iterator.remove();

            if (counter >= movePacketCount) {
                break;
            }
        }
    }

    public void cancelOutgoingMovement() {
        Minecraft mc = Minecraft.getInstance();
        List<Vec3> positions = getQueuedMovePositions();
        if (mc.player != null && !positions.isEmpty()) {
            mc.player.setPos(positions.get(0));
        }

        for (PacketSnapshot snapshot : packetQueue) {
            if (snapshot.origin() == TransferOrigin.OUTGOING && snapshot.packet() instanceof ServerboundMovePlayerPacket) {
                continue;
            }
            flushSnapshot(snapshot);
        }
        packetQueue.clear();
    }

    public boolean isAboveTime(long delayMs) {
        PacketSnapshot first = packetQueue.peek();
        return first != null && System.currentTimeMillis() - first.timestamp() >= delayMs;
    }

    public <T extends Packet<?>> void rewrite(Class<T> packetClass, Consumer<T> action) {
        if (packetClass == null || action == null) {
            return;
        }
        for (PacketSnapshot snapshot : packetQueue) {
            if (packetClass.isInstance(snapshot.packet())) {
                action.accept(packetClass.cast(snapshot.packet()));
            }
        }
    }

    private Action fireEvent(Packet<?> packet, TransferOrigin origin) {
        BlinkPacketEvent event = new BlinkPacketEvent(packet, origin);
        Events.BUS.post(event);
        return event.getAction();
    }

    private void flushSnapshot(PacketSnapshot snapshot) {
        if (snapshot == null || snapshot.packet() == null) {
            return;
        }
        runSilently(() -> {
            if (snapshot.origin() == TransferOrigin.OUTGOING) {
                sendPacketSilently(snapshot.packet());
            } else {
                handlePacketSilently(snapshot.packet());
            }
        });
    }

    public enum Action {
        FLUSH(0),
        PASS(1),
        QUEUE(2);

        private final int priority;

        Action(int priority) {
            this.priority = priority;
        }

        public int getPriority() {
            return priority;
        }
    }
}
