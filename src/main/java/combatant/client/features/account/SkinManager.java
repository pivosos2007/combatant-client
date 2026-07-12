/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.account;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import combatant.client.util.player.PlayerSkinResolver;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public enum SkinManager {
    ;

    private static final Map<String, Identifier> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> LOADING = new ConcurrentHashMap<>();
    private static final Executor EXECUTOR = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "Combatant-AltSkinLoader");
        thread.setDaemon(true);
        return thread;
    });

    public static Identifier getSkin(String playerName) {
        String key = normalizeKey(playerName);
        if (key.isEmpty()) {
            return defaultSkin(playerName);
        }

        Identifier cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        if (LOADING.putIfAbsent(key, Boolean.TRUE) == null) {
            CompletableFuture.runAsync(() -> loadSkin(playerName, key), EXECUTOR);
        }

        return defaultSkin(playerName);
    }

    public static void removeSkin(String playerName) {
        String key = normalizeKey(playerName);
        if (key.isEmpty()) {
            return;
        }
        CACHE.remove(key);
        LOADING.remove(key);
    }

    public static void reloadSkin(String playerName) {
        removeSkin(playerName);
        getSkin(playerName);
    }

    public static void clearCache() {
        CACHE.clear();
        LOADING.clear();
    }

    private static void loadSkin(String playerName, String key) {
        try {
            UUID uuid = fetchUuid(playerName);
            if (uuid == null) {
                return;
            }

            Minecraft client = Minecraft.getInstance();
            if (client == null || client.getSkinManager() == null) {
                return;
            }

            GameProfile profile = new GameProfile(uuid, playerName);
            Supplier<PlayerSkin> supplier = client.getSkinManager().createLookup(profile, false);
            if (supplier == null) {
                return;
            }

            PlayerSkin textures = supplier.get();
            Identifier skin = PlayerSkinResolver.resolveAssetId(textures == null ? null : textures.body());
            if (skin != null) {
                CACHE.put(key, skin);
            }
        } catch (Throwable ignored) {
        } finally {
            LOADING.remove(key);
        }
    }

    private static UUID fetchUuid(String playerName) {
        String cleanName = normalizeName(playerName);
        if (cleanName.isEmpty()) {
            return null;
        }

        try {
            HttpURLConnection connection = (HttpURLConnection) URI
                    .create("https://api.mojang.com/users/profiles/minecraft/" + cleanName)
                    .toURL()
                    .openConnection();
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "Combatant");

            if (connection.getResponseCode() != 200) {
                connection.disconnect();
                return null;
            }

            try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (!json.has("id")) {
                    return null;
                }
                return parseUuid(json.get("id").getAsString());
            } finally {
                connection.disconnect();
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String value = raw.trim();
        if (value.length() == 32) {
            value = value.substring(0, 8) + "-" + value.substring(8, 12) + "-" + value.substring(12, 16)
                    + "-" + value.substring(16, 20) + "-" + value.substring(20);
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Identifier defaultSkin(String playerName) {
        String cleanName = normalizeName(playerName);
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + cleanName).getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(uuid, cleanName);
        PlayerSkin textures = DefaultPlayerSkin.get(profile);
        Identifier resolved = PlayerSkinResolver.resolveAssetId(textures == null ? null : textures.body());
        if (resolved != null) {
            return resolved;
        }
        return Identifier.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png");
    }

    private static String normalizeKey(String playerName) {
        String cleanName = normalizeName(playerName);
        return cleanName.isEmpty() ? "" : cleanName.toLowerCase();
    }

    private static String normalizeName(String playerName) {
        return playerName == null ? "" : playerName.trim();
    }
}
