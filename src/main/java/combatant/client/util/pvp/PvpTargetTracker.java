/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.pvp;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.EventTargetChanged;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.PvpCooldowns;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PvpTargetTracker {
    public static final PvpTargetTracker INSTANCE = new PvpTargetTracker();
    private static final long DEFAULT_GLOW_TTL_MS = 1500L;
    private final Set<UUID> glowingTargets = new HashSet<>();

    private PvpTargetTracker() {
    }

    private static long resolveGlowTtlMs() {
        PvpCooldowns mod = Modules.get(PvpCooldowns.class);
        if (mod == null) return DEFAULT_GLOW_TTL_MS;
        return Math.max(0L, mod.getTargetGlowTtlMs());
    }

    @EventHandler
    public void onTargetChanged(EventTargetChanged event) {
        if (event == null || event.current == null) {
            PvpTargetState.setCurrentTarget(null);
            return;
        }
        if (event.current instanceof Player player) {
            UUID id = player.getUUID();
            PvpTargetState.setCurrentTarget(id);
        } else {
            PvpTargetState.setCurrentTarget(null);
        }
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        UUID selfId = mc.player != null ? mc.player.getUUID() : null;
        long now = Util.getMillis();
        long ttlMs = resolveGlowTtlMs();

        if (ttlMs <= 0L) {
            if (!glowingTargets.isEmpty()) {
                for (UUID prev : glowingTargets) {
                    PvpTargetState.setTargetInPvp(prev, false, 0L);
                }
                glowingTargets.clear();
            }
            return;
        }

        Set<UUID> nowGlowing = new HashSet<>();
        for (Player player : mc.level.players()) {
            if (player == null) continue;
            UUID id = player.getUUID();
            if (id == null) continue;
            if (selfId != null && selfId.equals(id)) continue;
            if (!player.isCurrentlyGlowing()) continue;

            nowGlowing.add(id);
            PvpTargetState.setTargetInPvp(id, true, now + ttlMs);
        }

        for (UUID prev : glowingTargets) {
            if (!nowGlowing.contains(prev)) {
                PvpTargetState.setTargetInPvp(prev, false, 0L);
            }
        }

        glowingTargets.clear();
        glowingTargets.addAll(nowGlowing);
    }
}
