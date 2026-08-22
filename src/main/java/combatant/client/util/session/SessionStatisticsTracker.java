/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.session;

import combatant.client.events.EventHandler;
import combatant.client.events.impl.AttackEntityEvent;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.util.player.PlayerSpeedUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.util.Util;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared, client-session statistics source.
 *
 * <p>Kill credit is based on the last server-confirmed damage source for a player, rather than proximity or a
 * local attack attempt. A play connection is counted when Minecraft obtains a new live client packet listener;
 * server-specific rounds intentionally are not guessed from scoreboards or chat text.</p>
 */
public final class SessionStatisticsTracker {
    public static final SessionStatisticsTracker INSTANCE = new SessionStatisticsTracker();

    private static final int MAX_SPEED_SAMPLES = 100;
    private static final long TARGET_RETENTION_MS = 30_000L;

    private final Minecraft mc = Minecraft.getInstance();
    private final float[] speedSamples = new float[MAX_SPEED_SAMPLES];
    private final Map<UUID, TrackedTarget> trackedTargets = new LinkedHashMap<>();

    private long sessionStartedNanos = System.nanoTime();
    private Object activeConnection;
    private int speedSampleStart;
    private int speedSampleCount;
    private int gamesPlayed;
    private int kills;
    private int deaths;
    private boolean playerWasDead;

    private SessionStatisticsTracker() {
    }

    /** Records the candidate immediately; actual kill attribution still requires a server damage event. */
    @EventHandler
    private synchronized void onAttackEntity(AttackEntityEvent event) {
        if (event == null || mc == null || mc.player == null || event.getPlayer() != mc.player) return;
        if (!(event.getTarget() instanceof Player target) || target == mc.player) return;
        trackedTargets.computeIfAbsent(target.getUUID(), ignored -> new TrackedTarget(target));
    }

    /**
     * Captures the server-confirmed last damage source for every tracked player. A later hit from somebody else or
     * the environment clears our credit before the death transition is observed.
     */
    @EventHandler
    private synchronized void onPacketReceive(PacketEvent.Receive event) {
        if (event == null || mc == null || mc.player == null || mc.level == null) return;
        if (!(event.getPacket() instanceof ClientboundDamageEventPacket packet)) return;

        Entity damaged = mc.level.getEntity(packet.entityId());
        if (!(damaged instanceof Player target) || target == mc.player) return;

        Entity sourceEntity = null;
        try {
            DamageSource source = packet.getSource(mc.level);
            if (source != null) {
                sourceEntity = source.getEntity();
                if (sourceEntity == null) sourceEntity = source.getDirectEntity();
            }
        } catch (RuntimeException ignored) {
            // Late or missing entity references mean the hit cannot safely be credited to the local player.
        }

        TrackedTarget tracked = trackedTargets.computeIfAbsent(target.getUUID(), ignored -> new TrackedTarget(target));
        tracked.target = target;
        tracked.lastDamageBySelf = sourceEntity == mc.player;
        tracked.lastDamageAtMs = Util.getMillis();
    }

    @EventHandler
    private synchronized void onGameTick(GameTickEvent event) {
        if (mc == null) return;

        Object connection = mc.getConnection();
        if (connection == null) {
            activeConnection = null;
        } else if (connection != activeConnection) {
            activeConnection = connection;
            gamesPlayed++;
            trackedTargets.clear();
            playerWasDead = false;
        }

        if (mc.player == null || mc.level == null) {
            playerWasDead = false;
            trackedTargets.clear();
            return;
        }

        boolean dead = !mc.player.isAlive() || mc.player.getHealth() <= 0.0f;
        if (dead && !playerWasDead) {
            deaths++;
        }
        playerWasDead = dead;

        sampleSpeed(PlayerSpeedUtil.getBps(mc.player));
        updateKillCredits(Util.getMillis());
    }

    public synchronized Snapshot snapshot() {
        List<Float> speeds = new ArrayList<>(speedSampleCount);
        float sum = 0.0f;
        for (int i = 0; i < speedSampleCount; i++) {
            float speed = sampleAt(i);
            speeds.add(speed);
            sum += speed;
        }
        float average = speedSampleCount > 0 ? sum / speedSampleCount : 0.0f;
        double kd = deaths == 0 ? kills : kills / (double) deaths;
        long elapsedMs = Math.max(0L, (System.nanoTime() - sessionStartedNanos) / 1_000_000L);
        return new Snapshot(elapsedMs, gamesPlayed, kills, deaths, kd, average, List.copyOf(speeds));
    }

    /** Resets accumulated values without pretending that the current connection is a newly joined game. */
    public synchronized void reset() {
        sessionStartedNanos = System.nanoTime();
        speedSampleStart = 0;
        speedSampleCount = 0;
        gamesPlayed = 0;
        kills = 0;
        deaths = 0;
        playerWasDead = mc != null && mc.player != null
                && (!mc.player.isAlive() || mc.player.getHealth() <= 0.0f);
        trackedTargets.clear();
    }

    private void updateKillCredits(long nowMs) {
        Iterator<Map.Entry<UUID, TrackedTarget>> iterator = trackedTargets.entrySet().iterator();
        while (iterator.hasNext()) {
            TrackedTarget tracked = iterator.next().getValue();
            if (tracked.lastDamageAtMs <= 0L || nowMs - tracked.lastDamageAtMs > TARGET_RETENTION_MS) {
                iterator.remove();
                continue;
            }
            Player target = tracked.target;
            if (!target.isAlive() || target.getHealth() <= 0.0f) {
                if (tracked.lastDamageBySelf) {
                    kills++;
                }
                iterator.remove();
            }
        }
    }

    private void sampleSpeed(float bps) {
        float sample = Float.isFinite(bps) ? Math.max(0.0f, bps) : 0.0f;
        if (speedSampleCount < MAX_SPEED_SAMPLES) {
            int index = (speedSampleStart + speedSampleCount) % MAX_SPEED_SAMPLES;
            speedSamples[index] = sample;
            speedSampleCount++;
            return;
        }
        speedSamples[speedSampleStart] = sample;
        speedSampleStart = (speedSampleStart + 1) % MAX_SPEED_SAMPLES;
    }

    private float sampleAt(int chronologicalIndex) {
        int index = (speedSampleStart + chronologicalIndex) % MAX_SPEED_SAMPLES;
        return speedSamples[index];
    }

    public record Snapshot(long elapsedMs,
                           int gamesPlayed,
                           int kills,
                           int deaths,
                           double kd,
                           float averageBps,
                           List<Float> speedSamples) {
    }

    private static final class TrackedTarget {
        private Player target;
        private boolean lastDamageBySelf;
        private long lastDamageAtMs;

        private TrackedTarget(Player target) {
            this.target = target;
        }
    }
}
