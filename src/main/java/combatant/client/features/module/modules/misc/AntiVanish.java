/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Spots vanished players two ways: names the server offers for tab completion that are missing
 * from the tab list, and players whose tab entry was removed while their entity stays loaded.
 */
@ModuleInfo(
        id = "antivanish",
        displayName = "AntiVanish",
        category = ModuleCategory.MISC, subcategory = ModuleSubcategory.UTILITY,
        description = "module.antivanish.description")
public final class AntiVanish extends Module {

    // Request ids for our own probes; large and odd so they do not clash with the game's own.
    private static final int PROBE_ID_BASE = 0x5A170000;
    private static final String PROBE_COMMAND = "/tell ";
    // A name must show up in this many consecutive probes before it is reported.
    private static final int CONFIRMATIONS = 2;
    private static final int REMOVAL_GRACE_TICKS = 10;

    private final Minecraft mc = Minecraft.getInstance();
    private final BooleanValue tabCompletionProbes = bool("tab_completion_probes", true);
    private final NumberValue<Integer> probeIntervalSeconds = num("probe_interval", 15, 5, 120);
    private final BooleanValue tabListDesync = bool("tab_list_desync", true);
    private final BooleanValue chatNotifications = bool("chat_notifications", true);
    private final BooleanValue soundAlerts = bool("sound_alerts", true);

    // Filled on the network thread, drained on the client tick.
    private final ConcurrentLinkedQueue<List<String>> probeResults = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<PendingRemoval> removals = new ConcurrentLinkedQueue<>();
    private final List<PendingRemoval> pending = new ArrayList<>();
    private final java.util.Map<String, Integer> streaks = new java.util.HashMap<>();
    private final Set<String> reported = new HashSet<>();
    private int probeSerial;
    private int ticksSinceProbe;
    private int probeId = -1;

    private record PendingRemoval(UUID id, String name, int[] ticksLeft) {
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        probeResults.clear();
        removals.clear();
        pending.clear();
        streaks.clear();
        reported.clear();
        ticksSinceProbe = 0;
        probeId = -1;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled()) return;
        Object packet = event.getPacket();
        if (packet instanceof ClientboundCommandSuggestionsPacket suggestions) {
            if (suggestions.id() != probeId) return;
            List<String> names = new ArrayList<>(suggestions.suggestions().size());
            for (ClientboundCommandSuggestionsPacket.Entry entry : suggestions.suggestions()) names.add(entry.text());
            probeResults.add(names);
        } else if (packet instanceof ClientboundPlayerInfoRemovePacket remove && tabListDesync.get()) {
            ClientPacketListener connection = mc.getConnection();
            if (connection == null) return;
            for (UUID id : remove.profileIds()) {
                PlayerInfo info = connection.getPlayerInfo(id);
                if (info != null) removals.add(new PendingRemoval(id, info.getProfile().name(), new int[]{REMOVAL_GRACE_TICKS}));
            }
        }
    }

    @Override
    public void onTick() {
        ClientPacketListener connection = mc.getConnection();
        if (!isEnabled() || mc.player == null || mc.level == null || connection == null) return;

        drainRemovals(connection);
        drainProbes(connection);

        if (tabCompletionProbes.get() && ++ticksSinceProbe >= probeIntervalSeconds.get() * 20) {
            ticksSinceProbe = 0;
            probeId = PROBE_ID_BASE + (probeSerial++ & 0xFFFF);
            connection.send(new ServerboundCommandSuggestionPacket(probeId, PROBE_COMMAND));
        }
    }

    private void drainRemovals(ClientPacketListener connection) {
        PendingRemoval added;
        while ((added = removals.poll()) != null) pending.add(added);

        for (Iterator<PendingRemoval> it = pending.iterator(); it.hasNext(); ) {
            PendingRemoval removal = it.next();
            if (--removal.ticksLeft()[0] > 0) continue;
            it.remove();
            Entity entity = mc.level.getEntity(removal.id());
            boolean loaded = entity != null && !entity.isRemoved();
            boolean backInList = connection.getPlayerInfo(removal.id()) != null;
            if (AntiVanishLogic.desyncAfterRemoval(loaded, backInList)) {
                alert(removal.name(), "left the tab list but is still in the world");
            }
        }
    }

    private void drainProbes(ClientPacketListener connection) {
        List<String> names;
        List<String> latest = null;
        while ((names = probeResults.poll()) != null) latest = names;
        if (latest == null) return;

        List<String> tabNames = new ArrayList<>();
        for (PlayerInfo info : connection.getOnlinePlayers()) tabNames.add(info.getProfile().name());
        Set<String> hidden = AntiVanishLogic.hiddenNames(latest, tabNames, mc.player.getGameProfile().name());

        // Names that stopped appearing (back in the tab list or gone) are forgotten so they can be re-reported.
        streaks.keySet().removeIf(key -> !containsIgnoreCase(hidden, key));
        reported.removeIf(key -> !containsIgnoreCase(hidden, key));
        for (String name : hidden) {
            String key = name.toLowerCase(Locale.ROOT);
            int streak = streaks.merge(key, 1, Integer::sum);
            if (streak >= CONFIRMATIONS && !reported.contains(key)) {
                alert(name, "is offered by tab completion but not in the tab list");
            }
        }
    }

    private static boolean containsIgnoreCase(Set<String> names, String lowerKey) {
        for (String name : names) if (name.toLowerCase(Locale.ROOT).equals(lowerKey)) return true;
        return false;
    }

    private void alert(String name, String reason) {
        reported.add(name.toLowerCase(Locale.ROOT));
        String text = "Possible vanished player: " + name + " " + reason;
        Notifier.warning(text);
        if (chatNotifications.get()) CommandOutput.send(text);
        if (soundAlerts.get()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 0.8f));
        }
    }
}
