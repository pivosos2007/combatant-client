/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.chat;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import combatant.client.config.ConfigPaths;
import combatant.client.config.persist.AtomicFileStore;
import combatant.client.features.gui.hud.draggable.impl.BetterChat;
import combatant.client.util.logging.DebugLog;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-key cache for ShowItem/ShowEntity hover payloads to persist across sessions.
 * Stores compact deduped entries; keyed by current chat store key (dimension/server).
 */
public final class BetterChatHoverCache {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private static volatile CachedOps cachedOps;

    private record CachedOps(HolderLookup.Provider lookup, RegistryOps<com.google.gson.JsonElement> ops) {
    }
    private final Object saveLock = new Object();
    private final Map<String, ItemEntry> itemCache = new HashMap<>();
    private final Map<String, EntityEntry> entityCache = new HashMap<>();
    private final Map<String, ItemStack> decodedItemCache = new HashMap<>();
    private final Map<String, ItemLookup> itemLookupByName = new HashMap<>();
    private final Map<String, EntityLookup> entityLookupByName = new HashMap<>();
    private final java.util.Set<String> loadedItemKeys = new java.util.HashSet<>();
    private final java.util.Set<String> loadedEntityKeys = new java.util.HashSet<>();
    private boolean loadedFromDisk = false;
    private boolean itemLookupIndexBuilt = false;
    private boolean entityLookupIndexBuilt = false;
    private BetterChatHoverCache() {
    }

    public static BetterChatHoverCache load(String key) {
        File f = fileFor(key);
        BetterChatHoverCache cache = new BetterChatHoverCache();
        AtomicFileStore.ReadResult result = AtomicFileStore.readRecovering(f.toPath(), BetterChatHoverCache::validCache);
        if (!result.found()) return cache;
        try {
            JsonObject obj = GSON.fromJson(new String(result.bytes(), StandardCharsets.UTF_8), JsonObject.class);
            cache.loadedFromDisk = true;
            if (obj != null && obj.has("items")) {
                JsonArray arr = obj.getAsJsonArray("items");
                arr.forEach(el -> {
                    if (!el.isJsonObject()) return;
                    JsonObject o = el.getAsJsonObject();
                    String keyId = o.has("k") ? o.get("k").getAsString() : null;
                    String id = o.has("id") ? o.get("id").getAsString() : null;
                    String comp = o.has("comp") ? o.get("comp").getAsString() : null;
                    if (keyId != null && id != null && comp != null) {
                        cache.itemCache.put(keyId, new ItemEntry(id, comp));
                        cache.loadedItemKeys.add(keyId);
                    }
                });
            }
            if (obj != null && obj.has("entities")) {
                JsonArray arr = obj.getAsJsonArray("entities");
                arr.forEach(el -> {
                    if (!el.isJsonObject()) return;
                    JsonObject o = el.getAsJsonObject();
                    String keyId = o.has("k") ? o.get("k").getAsString() : null;
                    String name = o.has("name") ? o.get("name").getAsString() : "";
                    String type = o.has("type") ? o.get("type").getAsString() : "";
                    String uuid = o.has("uuid") ? o.get("uuid").getAsString() : null;
                    if (keyId != null && uuid != null && !name.isEmpty()) {
                        try {
                            Identifier tid = Identifier.tryParse(type);
                            if (tid != null) {
                                var et = BuiltInRegistries.ENTITY_TYPE.getValue(tid);
                                if (et != net.minecraft.world.entity.EntityTypes.PLAYER) {
                                    String def = et.getDescription().getString().trim();
                                    if (!def.isEmpty() && def.equalsIgnoreCase(name.trim())) {
                                        return;
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        }
                        cache.entityCache.put(keyId, new EntityEntry(name, type, uuid));
                        cache.loadedEntityKeys.add(keyId);
                    }
                });
            }
        } catch (Exception e) {
            DebugLog.error("BetterChatHoverCache load failed for %s", e, key);
        }
        return cache;
    }

    private static boolean validCache(byte[] bytes) {
        try {
            JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) return false;
            JsonObject obj = parsed.getAsJsonObject();
            return (!obj.has("items") || obj.get("items").isJsonArray())
                    && (!obj.has("entities") || obj.get("entities").isJsonArray());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static File fileFor(String key) {
        Minecraft mc = Minecraft.getInstance();
        File dir = mc.gameDirectory.toPath()
                .resolve(ConfigPaths.root())
                .resolve("betterchat")
                .resolve("cache")
                .toFile();
        return new File(dir, key + ".json");
    }

    private static HolderLookup.Provider lookup() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        ClientPacketListener net = mc.getConnection();
        if (net != null) return net.registryAccess();
        if (mc.level != null) return mc.level.registryAccess();
        return null;
    }

    private static RegistryOps<com.google.gson.JsonElement> ops() {
        HolderLookup.Provider lookup = lookup();
        if (lookup == null) {
            return null;
        }
        CachedOps cached = cachedOps;
        if (cached != null && cached.lookup() == lookup) {
            return cached.ops();
        }
        synchronized (BetterChatHoverCache.class) {
            cached = cachedOps;
            if (cached == null || cached.lookup() != lookup) {
                cached = new CachedOps(lookup, RegistryOps.create(JsonOps.INSTANCE, lookup));
                cachedOps = cached;
            }
            return cached.ops();
        }
    }

    private static String normalizeLookup(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase();
    }

    public void save(String key) {
        synchronized (saveLock) {
            try {
                File f = fileFor(key);
                Map<String, ItemEntry> itemsSnapshot;
                Map<String, EntityEntry> entitiesSnapshot;
                synchronized (this) {
                    itemsSnapshot = new HashMap<>(itemCache);
                    entitiesSnapshot = new HashMap<>(entityCache);
                }
                JsonObject root = new JsonObject();
                JsonArray items = new JsonArray();
                for (var e : itemsSnapshot.entrySet()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("k", e.getKey());
                    o.addProperty("id", e.getValue().getId());
                    o.addProperty("comp", e.getValue().componentsJson());
                    items.add(o);
                }
                JsonArray ents = new JsonArray();
                for (var e : entitiesSnapshot.entrySet()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("k", e.getKey());
                    o.addProperty("name", e.getValue().name());
                    o.addProperty("type", e.getValue().typeId());
                    o.addProperty("uuid", e.getValue().uuid());
                    ents.add(o);
                }
                root.add("items", items);
                root.add("entities", ents);
                AtomicFileStore.write(f.toPath(), GSON.toJson(root).getBytes(StandardCharsets.UTF_8),
                        BetterChatHoverCache::validCache);
            } catch (Exception e) {
                DebugLog.error("BetterChatHoverCache save failed for %s", e, key);
            }
        }
    }

    public synchronized boolean putItem(String keyId, ItemStack stack) {
        BetterChat cfg = BetterChat.get();
        if (cfg != null && !cfg.cacheItems()) return false;
        if (keyId == null || stack == null || stack.isEmpty()) return false;
        try {
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            RegistryOps<com.google.gson.JsonElement> ops = ops();
            if (ops == null) return false;
            var encoded = ItemStack.CODEC.encodeStart(ops, stack.copy());
            var res = encoded.result();
            if (res.isEmpty()) return false;
            String compJson = res.get().toString();
            if (compJson.length() > 20000) return false;
            ItemEntry entry = new ItemEntry(itemId, compJson);
            ItemEntry prev = itemCache.put(keyId, entry);
            decodedItemCache.put(keyId, stack.copy());
            itemLookupIndexBuilt = false;
            clamp(cacheLimit());
            return !entry.equals(prev);
        } catch (Exception e) {
            DebugLog.error("BetterChatHoverCache putItem failed", e);
            return false;
        }
    }

    public synchronized boolean putEntity(String keyId, HoverEvent.EntityTooltipInfo content) {
        BetterChat cfg = BetterChat.get();
        if (cfg != null && !cfg.cacheEntities()) return false;
        if (keyId == null || content == null) return false;

        String name = content.name.map(Component::getString).orElse("").trim();
        if (name.isEmpty()) return false;

        if (content.type != net.minecraft.world.entity.EntityTypes.PLAYER) {
            String def = content.type.getDescription().getString().trim();
            if (!def.isEmpty() && def.equalsIgnoreCase(name)) {
                return false;
            }
        }

        String type = BuiltInRegistries.ENTITY_TYPE.getKey(content.type).toString();
        String uuid = content.uuid.toString();
        EntityEntry entry = new EntityEntry(name, type, uuid);
        EntityEntry prev = entityCache.put(keyId, entry);
        entityLookupIndexBuilt = false;
        clamp(cacheLimit());
        return !entry.equals(prev);
    }

    public ItemStack getItem(String keyId) {
        BetterChat cfg = BetterChat.get();
        if (cfg != null && !cfg.cacheItems()) return ItemStack.EMPTY;
        ItemEntry e = itemCache.get(keyId);
        if (e == null) return ItemStack.EMPTY;
        ItemStack cached = decodedItemCache.get(keyId);
        if (cached != null && !cached.isEmpty()) {
            return cached.copy();
        }
        try {
            RegistryOps<com.google.gson.JsonElement> ops = ops();
            if (ops == null) return ItemStack.EMPTY;
            var parsed = ItemStack.CODEC.parse(ops, JsonParser.parseString(e.componentsJson()));
            if (parsed.result().isPresent()) {
                ItemStack decoded = parsed.result().get();
                decodedItemCache.put(keyId, decoded.copy());
                return decoded;
            }
        } catch (Exception ex) {
            DebugLog.error("BetterChatHoverCache getItem decode failed", ex);
        }
        return ItemStack.EMPTY;
    }

    public HoverEvent.EntityTooltipInfo getEntity(String keyId) {
        BetterChat cfg = BetterChat.get();
        if (cfg != null && !cfg.cacheEntities()) return null;
        EntityEntry e = entityCache.get(keyId);
        if (e == null) return null;
        try {
            return new HoverEvent.EntityTooltipInfo(
                    BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.tryParse(e.typeId())),
                    UUID.fromString(e.uuid()),
                    e.name().isEmpty() ? null : Component.literal(e.name())
            );
        } catch (Exception ex) {
            DebugLog.error("BetterChatHoverCache getEntity build failed", ex);
            return null;
        }
    }

    private void clamp(int limit) {
        int max = Math.max(64, limit);
        while (itemCache.size() > max) {
            var it = itemCache.keySet().iterator();
            if (it.hasNext()) {
                String key = it.next();
                it.remove();
                decodedItemCache.remove(key);
                itemLookupIndexBuilt = false;
            }
        }
        while (entityCache.size() > max) {
            var it = entityCache.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
                entityLookupIndexBuilt = false;
            }
        }
    }

    private int cacheLimit() {
        BetterChat cfg = BetterChat.get();
        return cfg != null ? cfg.cacheLimit() : 512;
    }

    public ItemStack recoverItemById(String itemId) {
        if (itemId == null) return ItemStack.EMPTY;
        BetterChat cfg = BetterChat.get();
        if (cfg != null && !cfg.cacheItems()) return ItemStack.EMPTY;
        for (var entry : itemCache.entrySet()) {
            if (itemId.equals(entry.getValue().getId())) {
                return getItem(entry.getKey());
            }
        }
        return ItemStack.EMPTY;
    }

    public ItemStack getItemByKeyOrId(String keyId, String itemId) {
        ItemStack byKey = getItem(keyId);
        if (!byKey.isEmpty()) return byKey;
        return recoverItemById(itemId);
    }

    public boolean hasItemId(String itemId) {
        if (itemId == null) return false;
        return itemCache.values().stream().anyMatch(e -> itemId.equals(e.getId()));
    }

    public boolean wasLoadedFromDisk() {
        return loadedFromDisk;
    }

    public boolean wasItemKeyLoaded(String keyId) {
        return loadedItemKeys.contains(keyId);
    }

    public boolean wasEntityKeyLoaded(String keyId) {
        return loadedEntityKeys.contains(keyId);
    }

    public ItemLookup findItemByDisplayNameWithMeta(String displayName) {
        if (displayName == null || displayName.isEmpty()) return new ItemLookup(ItemStack.EMPTY, false);
        BetterChat cfg = BetterChat.get();
        if (cfg != null && !cfg.cacheItems()) return new ItemLookup(ItemStack.EMPTY, false);
        String needle = normalizeLookup(displayName);
        if (needle.isEmpty()) return new ItemLookup(ItemStack.EMPTY, false);
        ensureItemLookupIndex();
        ItemLookup lookup = itemLookupByName.get(needle);
        if (lookup == null || lookup.stack().isEmpty()) {
            return new ItemLookup(ItemStack.EMPTY, false);
        }
        return new ItemLookup(lookup.stack().copy(), lookup.loadedFromFile());
    }

    public ItemStack findItemByDisplayName(String displayName) {
        return findItemByDisplayNameWithMeta(displayName).stack();
    }

    public EntityLookup findEntityByNameWithMeta(String name) {
        if (name == null || name.isEmpty()) return new EntityLookup(null, false);
        BetterChat cfg = BetterChat.get();
        if (cfg != null && !cfg.cacheEntities()) return new EntityLookup(null, false);
        String needle = normalizeLookup(name);
        if (needle.isEmpty()) return new EntityLookup(null, false);
        ensureEntityLookupIndex();
        EntityLookup lookup = entityLookupByName.get(needle);
        return lookup != null ? lookup : new EntityLookup(null, false);
    }

    public HoverEvent.EntityTooltipInfo findEntityByName(String name) {
        return findEntityByNameWithMeta(name).content();
    }

    private void ensureItemLookupIndex() {
        if (itemLookupIndexBuilt) return;
        itemLookupByName.clear();
        for (var kv : itemCache.entrySet()) {
            String key = kv.getKey();
            ItemStack stack = getItem(key);
            if (stack.isEmpty()) continue;
            String normalized = normalizeLookup(stack.getHoverName().getString());
            if (normalized.isEmpty() || itemLookupByName.containsKey(normalized)) continue;
            boolean loaded = loadedFromDisk && loadedItemKeys.contains(key);
            itemLookupByName.put(normalized, new ItemLookup(stack.copy(), loaded));
        }
        itemLookupIndexBuilt = true;
    }

    private void ensureEntityLookupIndex() {
        if (entityLookupIndexBuilt) return;
        entityLookupByName.clear();
        for (var kv : entityCache.entrySet()) {
            String key = kv.getKey();
            EntityEntry entry = kv.getValue();
            String normalized = normalizeLookup(entry.name());
            if (normalized.isEmpty() || entityLookupByName.containsKey(normalized)) continue;
            try {
                HoverEvent.EntityTooltipInfo content = new HoverEvent.EntityTooltipInfo(
                        BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.tryParse(entry.typeId())),
                        UUID.fromString(entry.uuid()),
                        entry.name().isEmpty() ? null : Component.literal(entry.name())
                );
                boolean loaded = loadedFromDisk && loadedEntityKeys.contains(key);
                entityLookupByName.put(normalized, new EntityLookup(content, loaded));
            } catch (Exception ignored) {
            }
        }
        entityLookupIndexBuilt = true;
    }

    public record ItemEntry(String id, String componentsJson) {
        public String getId() {
            return id;
        }
    }

    public record EntityEntry(String name, String typeId, String uuid) {
    }

    public record ItemLookup(ItemStack stack, boolean loadedFromFile) {
    }

    public record EntityLookup(HoverEvent.EntityTooltipInfo content, boolean loadedFromFile) {
    }
}
