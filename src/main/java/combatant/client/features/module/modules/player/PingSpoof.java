/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.network.BlinkManager;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Raises the ping the server measures by holding keep-alive replies before sending them.
 *
 * <p>Held replies go out through {@link BlinkManager#sendSilently}, which skips packet events,
 * so they are not caught and delayed a second time. Delay stays well under the vanilla
 * 15-second keep-alive timeout by construction (max 1000 ms).</p>
 */
@ModuleInfo(
        id = "pingspoof",
        displayName = "PingSpoof",
        category = ModuleCategory.PLAYER, subcategory = ModuleSubcategory.EXPLOIT,
        description = "module.pingspoof.description")
public final class PingSpoof extends Module {

    private final NumberValue<Integer> delayMs = num("delay", 200, 0, 1000);
    private final NumberValue<Integer> jitterMs = num("jitter", 0, 0, 200);
    // Pong answers anticheat transaction pings; delaying them makes you look laggy to the
    // anticheat too, which is sometimes the point and sometimes a ban. Off by default.
    private final BooleanValue delayPongs = bool("delay_pongs", false);

    private final ConcurrentLinkedQueue<Held> held = new ConcurrentLinkedQueue<>();
    private final Minecraft mc = Minecraft.getInstance();

    @Override
    public void onDisable() {
        // Release everything so the server does not time us out after toggling off.
        Held next;
        while ((next = held.poll()) != null) {
            release(next);
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.isCancelled() || !event.isOriginal()) return;
        Packet<?> packet = event.getPacket();
        boolean delay = packet instanceof ServerboundKeepAlivePacket
                || (delayPongs.get() && packet instanceof ServerboundPongPacket);
        if (!delay) return;

        int jitter = jitterMs.get();
        long wait = delayMs.get() + (jitter > 0 ? ThreadLocalRandom.current().nextInt(jitter + 1) : 0);
        event.cancel();
        held.add(new Held(packet, System.currentTimeMillis() + wait, currentConnection()));
    }

    // Tick granularity (50 ms) is fine: the target ping is a rough number anyway.
    @EventHandler
    private void onGameTick(GameTickEvent event) {
        long now = System.currentTimeMillis();
        // Queue is FIFO and delays are near-constant, so stop at the first packet not yet due.
        Held next;
        while ((next = held.peek()) != null && next.sendAtMs() <= now) {
            held.poll();
            release(next);
        }
    }

    private void release(Held packet) {
        // A keep-alive answered on a different connection carries a foreign id, and vanilla
        // servers kick for that. Replies from a closed session are simply dropped.
        Connection connection = currentConnection();
        if (connection != null && connection == packet.connection()) {
            BlinkManager.INSTANCE.sendSilently(packet.packet());
        }
    }

    private Connection currentConnection() {
        ClientPacketListener listener = mc.getConnection();
        return listener != null ? listener.getConnection() : null;
    }

    private record Held(Packet<?> packet, long sendAtMs, Connection connection) {
    }
}
