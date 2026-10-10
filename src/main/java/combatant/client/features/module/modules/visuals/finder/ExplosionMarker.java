/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals.finder;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.finder.FinderAlerts;
import combatant.client.util.finder.FinderRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

@ModuleInfo(
        id = "explosionmarker",
        displayName = "ExplosionMarker",
        aliases = {"TntMarker", "TntExplosionMarker"},
        category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.ESP,
        description = "module.explosionmarker.description")
public final class ExplosionMarker extends Module {
    private static final int MAX_MARKERS = 160;
    private static final long MERGE_WINDOW_MS = 1100L;
    private static final long APPEAR_MS = 260L;

    private final NumberValue<Integer> rememberSeconds = num("remember_seconds", 30, 1, 120);
    private final NumberValue<Integer> alertCooldownSeconds = num("alert_cooldown", 30, 0, 600);
    private final NumberValue<Integer> minExplosions = num("min_explosions", 1, 1, 50);
    private final BooleanValue tracers = bool("tracers", true);
    private final BooleanValue chatAlerts = bool("chat_alerts", true);
    private final BooleanValue soundAlerts = bool("sound_alerts", false);
    private final RGBAColorValue fillColor = color("fill_color", "#50FF3C3C");
    private final RGBAColorValue lineColor = color("line_color", "#FFFF3C3C");

    private final Minecraft mc = Minecraft.getInstance();
    private final ConcurrentLinkedQueue<ExplosionSample> incoming = new ConcurrentLinkedQueue<>();
    private final List<Blast> blasts = new ArrayList<>();
    private final Map<Long, Long> lastAlerts = new HashMap<>();
    private ClientLevel lastLevel;

    @Override
    public void onDisable() {
        incoming.clear();
        blasts.clear();
        lastAlerts.clear();
        lastLevel = null;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundExplodePacket explosion) {
            incoming.add(new ExplosionSample(explosion.center(), explosion.radius(), explosion.blockCount()));
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        if (mc.level != lastLevel) {
            incoming.clear();
            blasts.clear();
            lastAlerts.clear();
            lastLevel = mc.level;
        }
        long now = System.currentTimeMillis();
        ExplosionSample sample;
        while ((sample = incoming.poll()) != null) {
            Blast match = null;
            for (Blast blast : blasts) {
                double distance = Math.max(1.5, Math.min(5.0, Math.max(blast.radius, sample.radius()) * 0.45));
                if (now - blast.lastSeenMs <= MERGE_WINDOW_MS
                        && blast.center.distanceToSqr(sample.center()) <= distance * distance) {
                    match = blast;
                    break;
                }
            }
            if (match == null) {
                if (blasts.size() >= MAX_MARKERS) blasts.remove(0);
                match = new Blast(sample.center(), Math.max(0.5f, sample.radius()), now);
                blasts.add(match);
            }
            match.center = sample.center();
            match.radius = Math.max(match.radius, Math.max(0.5f, sample.radius()));
            match.blockCount = Math.max(match.blockCount, sample.blockCount());
            match.count++;
            match.lastSeenMs = now;
            if (match.count >= minExplosions.get()) {
                long chunk = ChunkPos.pack(Mth.floor(match.center.x) >> 4, Mth.floor(match.center.z) >> 4);
                long prev = lastAlerts.getOrDefault(chunk, Long.MIN_VALUE);
                if (prev == Long.MIN_VALUE || now - prev >= alertCooldownSeconds.get() * 1000L) {
                    lastAlerts.put(chunk, now);
                    FinderAlerts.found(getDisplayName(), match.count > 1 ? "Explosions x" + match.count : "Explosion",
                            Mth.floor(match.center.x), Mth.floor(match.center.y), Mth.floor(match.center.z),
                            chatAlerts.get(), soundAlerts.get());
                }
            }
        }
        long rememberMs = rememberSeconds.get() * 1000L;
        blasts.removeIf(blast -> now - blast.lastSeenMs >= rememberMs);
        lastAlerts.entrySet().removeIf(entry -> now - entry.getValue() > Math.max(rememberMs, alertCooldownSeconds.get() * 1000L));
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || renderer == null || blasts.isEmpty()) return;
        FinderRender draw = FinderRender.begin(renderer, 1.5f);
        long now = System.currentTimeMillis();
        long rememberMs = rememberSeconds.get() * 1000L;
        for (Blast blast : blasts) {
            if (blast.count < minExplosions.get()) continue;
            float age = Mth.clamp((now - blast.createdMs) / (float) APPEAR_MS, 0.0f, 1.0f);
            float appear = age * age * (3.0f - 2.0f * age);
            float remain = Mth.clamp((blast.lastSeenMs + rememberMs - now) / (float) Math.min(2200L, rememberMs), 0.0f, 1.0f);
            float fade = remain * remain * (3.0f - 2.0f * remain);
            float opacity = appear * fade;
            if (opacity <= 0.001f) continue;

            Vec3 center = blast.center;
            double radius = blast.radius * (0.76 + 0.24 * appear);
            int primary = alpha(lineColor.getArgb(), opacity);
            int subtle = alpha(fillColor.getArgb(), opacity * 0.68f);
            int secondary = alpha(lineColor.getArgb(), opacity * 0.28f);
            draw.ring(center, radius, primary);
            draw.ring(center, radius * 0.96, subtle);
            draw.ring(center, radius * 1.04, secondary);
            draw.verticalRing(center, radius, true, secondary);
            draw.verticalRing(center, radius, false, secondary);
            double core = 0.12 + 0.08 * (1.0 - appear);
            draw.box(new AABB(center.x - core, center.y - core, center.z - core,
                    center.x + core, center.y + core, center.z + core), subtle, primary);
            if (tracers.get()) draw.tracer(center, alpha(lineColor.getArgb(), opacity * 0.54f));
        }
    }

    private static int alpha(int argb, float scale) {
        int a = Mth.clamp(Math.round((argb >>> 24) * scale), 0, 255);
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    private record ExplosionSample(Vec3 center, float radius, int blockCount) {
    }

    private static final class Blast {
        private Vec3 center;
        private float radius;
        private int blockCount;
        private int count;
        private long lastSeenMs;
        private final long createdMs;

        private Blast(Vec3 center, float radius, long now) {
            this.center = center;
            this.radius = radius;
            this.createdMs = now;
            this.lastSeenMs = now;
        }
    }
}
