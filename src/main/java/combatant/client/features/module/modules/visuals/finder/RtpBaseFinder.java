/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals.finder;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.StringValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.util.finder.FinderAlerts;
import combatant.client.util.text.ServerChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hops around DonutSMP with {@code /rtp} until the chunks that load around you contain a base. After each teleport
 * it scans the loaded chunks for storage blocks (chests, barrels, shulkers, furnaces, ender chests, enchanting
 * tables) and, optionally, non-dungeon spawners. Nothing found within the scan window means another {@code /rtp};
 * a hit stops the module and alerts through the finder toast, chat, a sound and an optional Discord webhook.
 */
@ModuleInfo(
        id = "rtpbasefinder",
        displayName = "RtpBaseFinder",
        aliases = {"RtpFinder", "BaseHopper"},
        category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.ESP,
        description = "module.rtpbasefinder.description")
public final class RtpBaseFinder extends Module {

    public enum Region {
        RANDOM(null),
        EU_CENTRAL("eu central"),
        EU_WEST("eu west"),
        EAST("east"),
        WEST("west"),
        ASIA("asia"),
        OCEANIA("oceania");

        private final String command;

        Region(String command) {
            this.command = command;
        }

        String command() {
            return command;
        }
    }

    private static final Region[] CONCRETE = {
            Region.EU_CENTRAL, Region.EU_WEST, Region.EAST, Region.WEST, Region.ASIA, Region.OCEANIA};
    private static final double MIN_TELEPORT_DISTANCE_SQ = 100.0 * 100.0;
    private static final int RTP_TIMEOUT_TICKS = 150;
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int SPAWNER_CONFIRM_SCANS = 10;
    private static final long WEBHOOK_TIMEOUT_SECONDS = 10;

    private enum Phase { TELEPORTING, SCANNING }

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Region> region = enumSetting("rtpBaseFinderRegion", "region", Region.RANDOM, Region.values());
    private final BooleanValue findSpawners = bool("rtpBaseFinderSpawners", "spawners", true);
    private final BooleanValue ignoreDungeons = bool("rtpBaseFinderIgnoreDungeons", "ignore_dungeon_spawners", true);
    private final NumberValue<Integer> minStorage = num("rtpBaseFinderMinStorage", "min_storage", 20, 1, 500);
    private final NumberValue<Integer> scanWindow = num("rtpBaseFinderScanWindow", "scan_window_ticks", 100, 20, 600);
    private final BooleanValue alertChat = bool("rtpBaseFinderChat", "chat_alerts", true);
    private final BooleanValue alertSound = bool("rtpBaseFinderSound", "sound_alerts", true);
    private final BooleanValue webhookEnabled = bool("rtpBaseFinderWebhookEnabled", "webhook_enabled", false);
    private final StringValue webhookUrl = text("rtpBaseFinderWebhook", "webhook_url", "");

    private Phase phase;
    private int phaseTicks;
    private Vec3 rtpOrigin;
    private int spawnerScans;
    private int hops;

    @Override
    public void onEnable() {
        phase = Phase.SCANNING;
        phaseTicks = 0;
        rtpOrigin = null;
        spawnerScans = 0;
        hops = 0;
        // Whatever is already loaded around you is scanned first: you may be standing next to a base right now.
    }

    @Override
    public void onDisable() {
        rtpOrigin = null;
        if (hops > 0) Notifier.info(I18n.get("notification.rtpbasefinder.summary", hops));
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return;
        phaseTicks++;

        if (phase == Phase.TELEPORTING) {
            boolean arrived = rtpOrigin != null && player.position().distanceToSqr(rtpOrigin) >= MIN_TELEPORT_DISTANCE_SQ;
            if (arrived) {
                phase = Phase.SCANNING;
                phaseTicks = 0;
                spawnerScans = 0;
            } else if (phaseTicks >= RTP_TIMEOUT_TICKS) {
                sendRtp(player);
            }
            return;
        }

        if (phaseTicks % SCAN_INTERVAL_TICKS == 0 && scan(level, player)) return;
        if (phaseTicks >= scanWindow.get()) sendRtp(player);
    }

    private void sendRtp(LocalPlayer player) {
        Region chosen = region.get() == Region.RANDOM ? CONCRETE[ThreadLocalRandom.current().nextInt(CONCRETE.length)] : region.get();
        ServerChat.command("rtp " + chosen.command());
        rtpOrigin = player.position();
        phase = Phase.TELEPORTING;
        phaseTicks = 0;
        hops++;
    }

    /** Scans every loaded chunk in view distance; returns true (and stops the module) when a base was found. */
    private boolean scan(ClientLevel level, LocalPlayer player) {
        int storage = 0;
        BlockPos spawner = null;
        ChunkPos center = player.chunkPosition();
        int radius = mc.options.renderDistance().get();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(center.x() + dx, center.z() + dz, false);
                if (chunk == null) continue;
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    BlockEntity entity = entry.getValue();
                    if (isStorage(entity)) {
                        storage++;
                    } else if (findSpawners.get() && entity instanceof SpawnerBlockEntity spawnerEntity
                            && !(ignoreDungeons.get() && isDungeonSpawner(level, spawnerEntity))) {
                        spawner = entry.getKey();
                    }
                }
            }
        }

        spawnerScans = spawner != null ? spawnerScans + 1 : 0;
        if (spawner != null && spawnerScans >= SPAWNER_CONFIRM_SCANS) {
            found(player, "spawner", spawner.getX(), spawner.getY(), spawner.getZ());
            return true;
        }
        if (storage >= minStorage.get()) {
            found(player, "base", player.getBlockX(), player.getBlockY(), player.getBlockZ());
            return true;
        }
        return false;
    }

    private static boolean isStorage(BlockEntity entity) {
        return entity instanceof ChestBlockEntity || entity instanceof EnderChestBlockEntity
                || entity instanceof ShulkerBoxBlockEntity || entity instanceof AbstractFurnaceBlockEntity
                || entity instanceof BarrelBlockEntity || entity instanceof EnchantingTableBlockEntity;
    }

    /** Naturally generated dungeons carry spider or cave spider spawners; those are not player bases. */
    private static boolean isDungeonSpawner(ClientLevel level, SpawnerBlockEntity spawner) {
        Entity display = spawner.getSpawner().getOrCreateDisplayEntity(level, spawner.getBlockPos());
        return display != null && (display.getType() == EntityTypes.SPIDER || display.getType() == EntityTypes.CAVE_SPIDER);
    }

    private void found(LocalPlayer player, String kind, int x, int y, int z) {
        String what = I18n.get("notification.rtpbasefinder.kind." + kind);
        FinderAlerts.found(getDisplayName(), what, x, y, z, alertChat.get(), alertSound.get());
        Notifier.success(I18n.get("notification.rtpbasefinder.found", what, x, y, z));
        if (webhookEnabled.get()) sendWebhook(player, what, x, y, z);
        setEnabled(false);
    }

    private void sendWebhook(LocalPlayer player, String what, int x, int y, int z) {
        String url = webhookUrl.get() == null ? "" : webhookUrl.get().trim();
        if (url.isEmpty()) return;
        ServerData server = mc.getCurrentServer();
        String text = "**RtpBaseFinder** | " + player.getGameProfile().name()
                + (server != null ? " @ " + server.ip : "")
                + "\nFound: " + what + "\nPosition: " + x + " " + y + " " + z;
        String body = "{\"content\":\"" + escapeJson(text) + "\"}";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(WEBHOOK_TIMEOUT_SECONDS))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(error -> null);
        } catch (IllegalArgumentException invalidUrl) {
            Notifier.warning(I18n.get("notification.rtpbasefinder.bad_webhook"));
        }
    }

    private static String escapeJson(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }
}
