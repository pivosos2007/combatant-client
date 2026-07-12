/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public enum PlayerSkinResolver {
    ;

    private static final Map<UUID, Identifier> CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_RESOLVE_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, Supplier<PlayerSkin>> SUPPLIERS = new ConcurrentHashMap<>();
    private static final long DEFAULT_RECHECK_MS = 1500L;
    private static final long RESOLVED_RECHECK_MS = 30000L;

    public static Identifier resolvePlayerSkin(AbstractClientPlayer player) {
        if (player == null) return null;

        return resolveProfileSkin(player.getGameProfile());
    }

    public static Identifier resolveProfileSkin(GameProfile profile) {
        if (profile == null) return null;

        UUID uuid = profile.id();
        if (uuid == null) return null;

        Identifier cached = CACHE.get(uuid);
        long now = System.currentTimeMillis();
        Long lastResolve = LAST_RESOLVE_MS.get(uuid);
        if (cached != null && lastResolve != null) {
            long interval = isRuntimeSkin(cached) ? RESOLVED_RECHECK_MS : DEFAULT_RECHECK_MS;
            if (now - lastResolve < interval) {
                return cached;
            }
        }
        LAST_RESOLVE_MS.put(uuid, now);

        Identifier resolved = null;
        Identifier defaultId = null;
        Identifier entryId = null;
        Identifier providerId = null;

        try {
            PlayerSkin fallback = DefaultPlayerSkin.get(profile);
            defaultId = resolveAssetId(fallback != null ? fallback.body() : null);
        } catch (Throwable ignored) {
        }

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getConnection() != null) {
                PlayerInfo entry = mc.getConnection().getPlayerInfo(uuid);
                if (entry != null) {
                    PlayerSkin textures = entry.getSkin();
                    entryId = resolveAssetId(textures != null ? textures.body() : null);
                }
            }
            if (mc != null && mc.getSkinManager() != null) {
                boolean requireSecure = !mc.isLocalPlayer(uuid);
                Supplier<PlayerSkin> supplier = SUPPLIERS.computeIfAbsent(uuid, id ->
                        mc.getSkinManager().createLookup(profile, requireSecure));
                if (supplier != null) {
                    PlayerSkin textures = supplier.get();
                    providerId = resolveAssetId(textures != null ? textures.body() : null);
                }
            }
        } catch (Throwable ignored) {
        }

        if (entryId != null && (!entryId.equals(defaultId))) {
            resolved = entryId;
        } else if (providerId != null) {
            resolved = providerId;
        } else if (entryId != null) {
            resolved = entryId;
        } else {
            resolved = defaultId;
        }

        if (resolved != null) {
            if (cached == null || !cached.equals(resolved)) {
                CACHE.put(uuid, resolved);
            }
            LAST_RESOLVE_MS.put(uuid, now);
            return resolved;
        }

        if (cached != null) {
            return cached;
        }

        Identifier steve = Identifier.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png");
        CACHE.put(uuid, steve);
        LAST_RESOLVE_MS.put(uuid, now);
        return steve;
    }

    private static boolean isRuntimeSkin(Identifier id) {
        if (id == null) return false;
        String path = id.getPath();
        return path.startsWith("skins/") || path.startsWith("skin/");
    }

    public static Identifier resolveAssetId(ClientAsset asset) {
        if (asset == null) return null;

        Identifier id = asset.id();
        if (id == null && asset instanceof ClientAsset.Texture tex) {
            id = tex.texturePath();
        }
        return normalizeSkinId(id);
    }

    public static Identifier normalizeSkinId(Identifier id) {
        if (id == null) return null;

        String path = id.getPath();

        // runtime skins (minecraft:skins/<hash>)
        if (path.startsWith("skins/") || path.startsWith("skin/")
                || path.startsWith("capes/") || path.startsWith("cape/")) {
            return id;
        }

        if (!path.startsWith("textures/")) {
            path = "textures/" + path;
        }
        if (!path.endsWith(".png")) {
            path = path + ".png";
        }

        return Identifier.fromNamespaceAndPath(id.getNamespace(), path);
    }

    /* optional utils */

    public static void clear(UUID uuid) {
        if (uuid != null) {
            CACHE.remove(uuid);
            LAST_RESOLVE_MS.remove(uuid);
            SUPPLIERS.remove(uuid);
        }
    }

    public static void clearAll() {
        CACHE.clear();
        LAST_RESOLVE_MS.clear();
        SUPPLIERS.clear();
    }
}
