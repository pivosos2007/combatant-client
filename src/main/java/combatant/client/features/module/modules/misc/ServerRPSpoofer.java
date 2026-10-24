/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket.Action;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Never downloads or applies a server resource pack. The pack push is swallowed and the server is told the
 * pack was accepted immediately and loaded after a delay, so required-pack checks pass without the pack.
 * The packet hook lives in {@code ResourcePackSpoofMixin}.
 */
@ModuleInfo(
        id = "serverrpspoofer",
        displayName = "ServerRPSpoofer",
        aliases = {"RPSpoofer", "ResourcePackSpoofer", "NoServerPack"},
        category = ModuleCategory.MISC,
        subcategory = ModuleSubcategory.UTILITY,
        description = "module.serverrpspoofer.description")
public final class ServerRPSpoofer extends Module {

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "combatant-rp-spoof");
        t.setDaemon(true);
        return t;
    });

    private final NumberValue<Integer> delayMs = num("rpSpoofDelayMs", "delay_ms", 800, 0, 10_000);
    private final BooleanValue sendDownloaded = bool("rpSpoofSendDownloaded", "send_downloaded", true);

    /**
     * Called by the packet-handler mixin. Returns true when the push was swallowed and must not be processed
     * by vanilla.
     */
    public boolean intercept(ClientboundResourcePackPushPacket packet, Connection connection) {
        if (!isEnabled() || connection == null) return false;
        UUID id = packet.id();
        connection.send(new ServerboundResourcePackPacket(id, Action.ACCEPTED));
        SCHEDULER.schedule(() -> Minecraft.getInstance().execute(() -> {
            if (!connection.isConnected()) return;
            if (sendDownloaded.get()) connection.send(new ServerboundResourcePackPacket(id, Action.DOWNLOADED));
            connection.send(new ServerboundResourcePackPacket(id, Action.SUCCESSFULLY_LOADED));
        }), delayMs.get(), TimeUnit.MILLISECONDS);
        return true;
    }
}
