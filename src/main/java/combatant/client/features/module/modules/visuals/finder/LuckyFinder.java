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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * "Follow your luck": every 30-120 seconds drops a marker on a random fully loaded chunk near you, with a chat
 * alert, a sound and a big translucent plate on the surface. The marker disappears again once the chunk falls out
 * of view distance. Markers are only as good as luck: the spot is chosen at random, not detected.
 */
@ModuleInfo(
        id = "luckyfinder",
        displayName = "LuckyFinder",
        aliases = {"LuckFinder"},
        category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.ESP,
        description = "module.luckyfinder.description")
public final class LuckyFinder extends Module {

    private static final long MIN_DELAY_NANOS = 30_000_000_000L;
    private static final long MAX_DELAY_NANOS = 120_000_000_000L;
    private static final long RETRY_NANOS = 1_000_000_000L;
    private static final int PLATE_SIZE = 48;
    private static final double PLATE_THICKNESS = 0.15;
    private static final int ATTEMPTS = 32;
    private static final int FRESH_ATTEMPTS = 24;
    private static final int MAX_PICK_RADIUS = 8;

    private record Marker(int chunkX, int chunkZ, double y) {
    }

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue chatAlerts = bool("chat_alerts", true);
    private final BooleanValue sound = bool("sound", true);
    private final NumberValue<Integer> volume = num("volume", 35, 0, 100);
    private final RGBAColorValue fillColor = color("fill_color", "#4B00FF46");
    private final RGBAColorValue lineColor = color("line_color", "#B400FF46");

    private final Map<Long, Marker> markers = new LinkedHashMap<>();
    private ClientLevel lastLevel;
    private long deadline;
    private boolean armed;

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        markers.clear();
        lastLevel = null;
        armed = false;
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.level == null || mc.player == null) return;
        if (mc.level != lastLevel) {
            markers.clear();
            lastLevel = mc.level;
            armed = false;
        }
        prune();

        long now = System.nanoTime();
        if (!armed) {
            arm(now);
            return;
        }
        if (now - deadline < 0) return;
        if (tryFlag()) {
            arm(now);
        } else {
            deadline = now + RETRY_NANOS;
        }
    }

    private void arm(long now) {
        deadline = now + ThreadLocalRandom.current().nextLong(MIN_DELAY_NANOS, MAX_DELAY_NANOS + 1);
        armed = true;
    }

    private int viewDistance() {
        return mc.options.getEffectiveRenderDistance();
    }

    private void prune() {
        if (markers.isEmpty()) return;
        ChunkPos player = mc.player.chunkPosition();
        int view = viewDistance();
        markers.values().removeIf(marker -> Math.abs((long) marker.chunkX - player.x()) > view
                || Math.abs((long) marker.chunkZ - player.z()) > view);
    }

    private boolean tryFlag() {
        ChunkPos player = mc.player.chunkPosition();
        int radius = Math.max(0, Math.min(MAX_PICK_RADIUS, viewDistance() - 2));
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int chunkX = player.x() + random.nextInt(-radius, radius + 1);
            int chunkZ = player.z() + random.nextInt(-radius, radius + 1);
            long key = ChunkPos.pack(chunkX, chunkZ);
            if (attempt < FRESH_ATTEMPTS && markers.containsKey(key)) continue;

            int surface = highestSurface(chunkX, chunkZ);
            if (surface == Integer.MIN_VALUE) continue;

            markers.put(key, new Marker(chunkX, chunkZ, surface + 0.002));
            FinderAlerts.found(getDisplayName(), "lucky spot", (chunkX << 4) + 8, surface, (chunkZ << 4) + 8,
                    chatAlerts.get(), false);
            if (sound.get() && volume.get() > 0) {
                mc.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0f, volume.get() / 100.0f));
            }
            return true;
        }
        return false;
    }

    /** Top free Y over the chunk and its eight neighbours, or MIN_VALUE if any of them is not loaded yet. */
    private int highestSurface(int chunkX, int chunkZ) {
        ClientLevel level = mc.level;
        LevelChunk[] area = new LevelChunk[9];
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX + dx, chunkZ + dz, false);
                if (chunk == null || chunk.isEmpty()) return Integer.MIN_VALUE;
                area[(dx + 1) * 3 + dz + 1] = chunk;
            }
        }
        int highest = Integer.MIN_VALUE;
        for (LevelChunk chunk : area) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    highest = Math.max(highest, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
                }
            }
        }
        return highest <= level.getMinY() ? Integer.MIN_VALUE : highest;
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || renderer == null || markers.isEmpty() || mc.level != lastLevel) return;

        FinderRender draw = FinderRender.begin(renderer, 1.5f);
        int fill = fillColor.getArgb();
        int line = lineColor.getArgb();
        double half = PLATE_SIZE / 2.0;
        for (Marker marker : markers.values()) {
            double centerX = marker.chunkX * 16.0 + 8.0;
            double centerZ = marker.chunkZ * 16.0 + 8.0;
            draw.box(new AABB(centerX - half, marker.y, centerZ - half,
                    centerX + half, marker.y + PLATE_THICKNESS, centerZ + half), fill, line);
        }
    }
}
