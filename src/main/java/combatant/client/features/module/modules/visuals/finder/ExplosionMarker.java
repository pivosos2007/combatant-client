/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals.finder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Marks chunks where explosions happened (TNT raids, crystal fights, creeper farms) and
 * announces the first one per chunk. Port of WaterClient's TNT Explosion Marker, reading
 * {@code ClientboundExplodePacket.center()} directly instead of reflecting over fields.
 */
@ModuleInfo(
        id = "explosionmarker",
        displayName = "ExplosionMarker",
        aliases = {"TntMarker", "TntExplosionMarker"},
        category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.ESP,
        description = "module.explosionmarker.description")
public final class ExplosionMarker extends Module {

    private final NumberValue<Integer> rememberSeconds = num("remember_seconds", 300, 10, 3600);
    // A TNT cannon produces dozens of explosions per volley; one alert per chunk per window.
    private final NumberValue<Integer> alertCooldownSeconds = num("alert_cooldown", 30, 0, 600);
    // Creeper farms and single crystals are noise when hunting raids; require a volley.
    private final NumberValue<Integer> minExplosions = num("min_explosions", 1, 1, 50);
    private final BooleanValue tracers = bool("tracers", true);
    private final BooleanValue chatAlerts = bool("chat_alerts", true);
    private final BooleanValue soundAlerts = bool("sound_alerts", false);
    private final RGBAColorValue fillColor = color("fill_color", "#50FF3C3C");
    private final RGBAColorValue lineColor = color("line_color", "#FFFF3C3C");

    private final Minecraft mc = Minecraft.getInstance();
    private final ConcurrentLinkedQueue<Vec3> incoming = new ConcurrentLinkedQueue<>();
    private final Map<Long, Blast> blasts = new LinkedHashMap<>();
    private ClientLevel lastLevel;

    @Override
    public void onDisable() {
        incoming.clear();
        blasts.clear();
        lastLevel = null;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundExplodePacket explosion) {
            incoming.add(explosion.center());
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        if (mc.level != lastLevel) {
            blasts.clear();
            lastLevel = mc.level;
        }
        long now = System.currentTimeMillis();

        Vec3 center;
        while ((center = incoming.poll()) != null) {
            long key = ChunkPos.pack(Mth.floor(center.x) >> 4, Mth.floor(center.z) >> 4);
            Blast blast = blasts.computeIfAbsent(key, k -> new Blast());
            blast.center = center;
            blast.count++;
            blast.lastSeenMs = now;
            if (blast.count >= minExplosions.get()
                    && now - blast.lastAlertMs >= alertCooldownSeconds.get() * 1000L) {
                blast.lastAlertMs = now;
                String what = blast.count > 1 ? "Explosions x" + blast.count : "Explosion";
                FinderAlerts.found(getDisplayName(), what, Mth.floor(center.x), Mth.floor(center.y), Mth.floor(center.z),
                        chatAlerts.get(), soundAlerts.get());
            }
        }

        long keepMs = rememberSeconds.get() * 1000L;
        blasts.values().removeIf(blast -> now - blast.lastSeenMs > keepMs);
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || renderer == null || blasts.isEmpty()) return;
        FinderRender draw = FinderRender.begin(renderer, 1.5f);
        int min = minExplosions.get();
        for (Map.Entry<Long, Blast> entry : blasts.entrySet()) {
            if (entry.getValue().count < min) continue;
            Vec3 c = entry.getValue().center;
            draw.chunkPlate(ChunkPos.getX(entry.getKey()), ChunkPos.getZ(entry.getKey()), c.y,
                    fillColor.getArgb(), lineColor.getArgb());
            draw.box(new AABB(c.x - 0.5, c.y - 0.5, c.z - 0.5, c.x + 0.5, c.y + 0.5, c.z + 0.5),
                    0, lineColor.getArgb());
            if (tracers.get()) draw.tracer(c, lineColor.getArgb());
        }
    }

    private static final class Blast {
        private Vec3 center;
        private int count;
        private long lastSeenMs;
        private long lastAlertMs;
    }
}
