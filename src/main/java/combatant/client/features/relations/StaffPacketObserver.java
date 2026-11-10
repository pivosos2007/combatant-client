/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.relations;

import combatant.client.events.EventHandler;
import combatant.client.events.impl.ClientEntitiesRemovedEvent;
import combatant.client.events.impl.ClientEntityAddedEvent;
import combatant.client.events.impl.ClientPlayerInfoUpdatedEvent;
import combatant.client.events.impl.ClientPlayerInfoRemovedEvent;
import combatant.client.util.pvp.opponents.TotemPopCounter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import java.util.UUID;

/** Keeps player-list and entity-presence tracking separate from packet injection code. */
public enum StaffPacketObserver {
    INSTANCE;

    @EventHandler
    private void onEntityAdded(ClientEntityAddedEvent event) {
        var packet = event.getPacket();

        Minecraft mc = Minecraft.getInstance();
        ClientLevel world = mc.level;
        if (world == null) return;

        if (packet.getType() != net.minecraft.world.entity.EntityTypes.PLAYER) return;

        Entity entity = world.getEntity(packet.getId());
        if (!(entity instanceof Player player)) return;

        StaffTracker.onEntityAppear(player.getUUID(), player.getGameProfile().name());
    }

    @EventHandler
    private void onEntitiesRemoved(ClientEntitiesRemovedEvent event) {
        var packet = event.getPacket();

        Minecraft mc = Minecraft.getInstance();
        ClientLevel world = mc.level;
        if (world == null) return;

        for (int id : packet.entityIds()) {

            Entity e = world.getEntity(id);
            if (!(e instanceof Player player)) continue;

            StaffTracker.onEntityDisappear(player.getUUID());
        }
    }

    @EventHandler
    private void onPlayerInfoUpdated(ClientPlayerInfoUpdatedEvent event) {
        var packet = event.getPacket();

        var actions = packet.actions();
        boolean listedAffects = actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED)
                || actions.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER);

        boolean gmAffects = actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE)
                || actions.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER);

        for (var entry : packet.entries()) {

            var uuid = entry.profileId();
            String name = entry.profile() != null ? entry.profile().name() : null;

            Boolean listed = listedAffects ? entry.listed() : null;
            var gm = gmAffects ? entry.gameMode() : null;

            // Use the complete component from the player-list entry.
            var displayName = entry.displayName();
            if (displayName == null && name != null) {
                displayName = net.minecraft.network.chat.Component.literal(name);
            }

            StaffTracker.onTabUpdate(uuid, name, displayName, listed, gm);
        }
    }

    @EventHandler
    private void onPlayerInfoRemoved(ClientPlayerInfoRemovedEvent event) {
        var packet = event.getPacket();

        for (UUID id : packet.profileIds()) {

            StaffTracker.onTabUpdate(id, null, null, false, null);
            TotemPopCounter.onPlayerLogout(id);
            // Preserve vanish classification when the server removes a player.
            StaffTracker.onEntityDisappear(id);
        }
    }
}
