/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.relations;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Finds vanished players the entity check in {@link StaffTracker} cannot: many vanish plugins hide a player from the
 * tab list but still offer the name for {@code /tell} completion. Every interval a completion request is sent; names
 * offered there but missing from the tab list in two probes in a row are reported to {@link StaffTracker} as
 * vanished, so the Admins HUD shows them even out of render distance. Settings are shown on the Admins HUD element.
 */
public final class VanishProbe {

    public static final VanishProbe INSTANCE = new VanishProbe();

    // Request ids for our own probes; large and odd so they do not clash with the game's own.
    private static final int PROBE_ID_BASE = 0x5A170000;
    private static final String PROBE_COMMAND = "/tell ";
    private static final int CONFIRMATIONS = 2;

    private final BooleanValue enabled = new BooleanValue("admins_vanish_probes", true);
    private final NumberValue<Integer> intervalSeconds = new NumberValue<>("admins_vanish_probe_interval", 15, 5, 120);

    // Filled on the network thread, drained on the client tick.
    private final ConcurrentLinkedQueue<List<String>> results = new ConcurrentLinkedQueue<>();
    private final Map<String, Integer> streaks = new HashMap<>();
    private ClientPacketListener connection;
    private int serial;
    private int ticksSinceProbe;
    private volatile int probeId = -1;

    private VanishProbe() {
    }

    public BooleanValue enabledValue() {
        return enabled;
    }

    public NumberValue<Integer> intervalValue() {
        return intervalSeconds;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof ClientboundCommandSuggestionsPacket suggestions)) return;
        if (suggestions.id() != probeId) return;
        List<String> names = new ArrayList<>(suggestions.suggestions().size());
        for (ClientboundCommandSuggestionsPacket.Entry entry : suggestions.suggestions()) names.add(entry.text());
        results.add(names);
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener current = mc.getConnection();
        if (current != connection) {
            connection = current;
            reset();
        }
        if (current == null || mc.player == null || mc.hasSingleplayerServer()) return;
        if (!enabled.get()) {
            if (!streaks.isEmpty()) reset();
            return;
        }

        List<String> latest = null;
        List<String> names;
        while ((names = results.poll()) != null) latest = names;
        if (latest != null) apply(current, latest, mc.player.getGameProfile().name());

        if (++ticksSinceProbe >= intervalSeconds.get() * 20) {
            ticksSinceProbe = 0;
            probeId = PROBE_ID_BASE + (serial++ & 0xFFFF);
            current.send(new ServerboundCommandSuggestionPacket(probeId, PROBE_COMMAND));
        }
    }

    private void apply(ClientPacketListener current, List<String> suggestions, String self) {
        List<String> tabNames = new ArrayList<>();
        for (PlayerInfo info : current.getOnlinePlayers()) tabNames.add(info.getProfile().name());
        StaffTracker.onCompletionProbe(VanishProbeLogic.confirmed(
                VanishProbeLogic.hiddenNames(suggestions, tabNames, self), streaks, CONFIRMATIONS));
    }

    private void reset() {
        results.clear();
        streaks.clear();
        ticksSinceProbe = 0;
        probeId = -1;
        StaffTracker.onCompletionProbe(List.of());
    }
}
