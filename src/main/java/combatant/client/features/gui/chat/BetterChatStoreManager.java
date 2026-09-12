/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import combatant.client.features.gui.chat.rich.BetterChatMessage;
import combatant.client.features.gui.chat.actions.ChatMessageActions;
import combatant.client.features.gui.chat.rich.BetterChatMessageJson;
import combatant.client.features.gui.chat.rich.ItemNode;
import combatant.client.features.gui.chat.rich.TextNode;
import combatant.client.config.ConfigPaths;
import combatant.client.features.gui.hud.draggable.impl.BetterChat;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.text.LegacyTextUtil;
import combatant.client.util.text.TextJsonUtil;
import combatant.client.util.chat.ChatSpamHeuristics;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Per-world chat storage + async Gson persistence.
 */
public enum BetterChatStoreManager {
    ;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Map<String, BetterChatStore> STORES = new HashMap<>();
    private static final Map<String, BetterChatHoverCache> HOVER_CACHES = new HashMap<>();
    private static final Map<String, CompletableFuture<?>> SAVE_JOBS = new HashMap<>();
    private static final Map<String, CompletableFuture<?>> CACHE_SAVE_JOBS = new HashMap<>();
    private static final Map<String, Long> LAST_SAVED_REVISIONS = new HashMap<>();
    private static final Map<String, Long> REQUESTED_SAVE_REVISIONS = new HashMap<>();
    private static String currentKey = null;
    private static BetterChatStore active = null;
    private static BetterChatHoverCache activeCache = null;

    public static BetterChatStore getActiveStore(Minecraft mc) {
        String key = key(mc);
        if (key == null) return null;
        if (!Objects.equals(key, currentKey)) {
            switchStore(key);
        }
        return active;
    }

    public static BetterChatHoverCache getActiveCache() {
        return activeCache;
    }

    /**
     * @return false only when BetterChat anti-spam deliberately suppresses the message.
     */
    public static boolean addMessage(Component text) {
        Component converted = LegacyTextUtil.convertLegacyCodes(text);
        ChatMessageActions.copyBinding(text, converted);
        return addMessage(BetterChatMessage.text(converted));
    }

    public static boolean addMessage(BetterChatMessage message) {
        Minecraft mc = Minecraft.getInstance();
        BetterChatStore store = getActiveStore(mc);
        if (store == null) return true;

        BetterChatMessage safeMessage = message == null ? BetterChatMessage.empty() : message;
        BetterChat cfg = BetterChat.get();
        if (cfg != null && cfg.antiSpam()
                && ChatSpamHeuristics.isLikelyGibberish(safeMessage.accessibleComponent())) {
            return false;
        }

        boolean stackDuplicates = cfg == null || cfg.stackDuplicates();
        store.add(safeMessage, System.currentTimeMillis(), stackDuplicates);
        boolean cacheChanged = captureHovers(safeMessage, activeCache);
        BetterChatRenderer.onNewMessage();
        boolean historyOn = cfg == null || cfg.historyEnabled();
        if (historyOn) {
            scheduleSave(key(mc), store);
        }
        if (cacheChanged) {
            scheduleCacheSave(key(mc), activeCache);
        }
        return true;
    }

    public static void clearActive() {
        if (active != null) {
            active.clear();
            BetterChatRenderer.clearMessageCaches();
            BetterChatSerializedLineCache.clear();
            scheduleSave(currentKey, active);
        }
    }

    /**
     * Persist current active store without clearing it.
     */
    public static void flushActive() {
        if (currentKey != null && active != null) {
            saveSync(currentKey, active);
        }
    }

    private static void switchStore(String key) {
        BetterChatRenderer.resetScroll();
        BetterChatRenderer.clearMessageCaches();
        BetterChatSerializedLineCache.clear();
        currentKey = key;
        BetterChatHoverCache cache = cacheFor(key);
        activeCache = cache;
        active = STORES.computeIfAbsent(key, k -> loadSync(k, cache));
    }

    private static BetterChatStore loadSync(String key, BetterChatHoverCache cache) {
        File f = fileFor(key);
        if (!f.exists()) return new BetterChatStore();
        try (FileReader reader = new FileReader(f, StandardCharsets.UTF_8)) {
            JsonArray arr = GSON.fromJson(reader, JsonArray.class);
            BetterChatStore store = new BetterChatStore();
            if (arr != null) {
                for (var el : arr) {
                    if (!el.isJsonObject()) continue;
                    JsonObject obj = el.getAsJsonObject();
                    String json = obj.has("text") ? obj.get("text").getAsString() : null;
                    long ts = obj.has("time") ? obj.get("time").getAsLong() : System.currentTimeMillis();
                    int repeatCount = obj.has("count") ? Math.max(1, obj.get("count").getAsInt()) : 1;
                    if (obj.has("nodes") && obj.get("nodes").isJsonArray()) {
                        BetterChatMessage message = BetterChatMessageJson.decode(obj.getAsJsonArray("nodes"));
                        store.add(message, ts, false, repeatCount);
                        captureHovers(message, cache);
                    } else if (json != null) {
                        var txt = TextJsonUtil.fromJson(json);
                        if (txt != null) {
                            store.add(txt, ts, false, repeatCount);
                            captureHovers(txt, cache);
                        }
                    }
                }
            }
            DebugLog.info("[BetterChat] Loaded %d messages for %s", store.snapshot().size(), key);
            return store;
        } catch (Exception e) {
            DebugLog.error("BetterChat load failed for %s", e, key);
            return new BetterChatStore();
        }
    }

    private static void scheduleSave(String key, BetterChatStore store) {
        if (key == null || store == null) return;
        long revision = store.revision();
        synchronized (BetterChatStoreManager.class) {
            long lastSaved = LAST_SAVED_REVISIONS.getOrDefault(key, -1L);
            if (revision <= lastSaved) {
                return;
            }
            REQUESTED_SAVE_REVISIONS.put(key, revision);
            CompletableFuture<?> inFlight = SAVE_JOBS.get(key);
            if (inFlight != null && !inFlight.isDone()) {
                return;
            }
            SAVE_JOBS.put(key, CompletableFuture.runAsync(() -> drainSaveQueue(key, store)));
        }
    }

    /**
     * Flushes all pending saves and writes current state synchronously (used on shutdown).
     */
    public static void flushAll() {
        // wait for pending async saves
        for (CompletableFuture<?> f : new ArrayList<>(SAVE_JOBS.values())) {
            try {
                f.join();
            } catch (Throwable ignored) {
            }
        }
        for (CompletableFuture<?> f : new ArrayList<>(CACHE_SAVE_JOBS.values())) {
            try {
                f.join();
            } catch (Throwable ignored) {
            }
        }
        // save active store synchronously
        if (currentKey != null && active != null) {
            saveSync(currentKey, active);
        }
        // save caches synchronously
        for (var entry : HOVER_CACHES.entrySet()) {
            try {
                entry.getValue().save(entry.getKey());
            } catch (Exception ignored) {
            }
        }
    }

    private static void saveSync(String key, BetterChatStore store) {
        try {
            SavePayload payload = captureSavePayload(store);
            File f = fileFor(key);
            f.getParentFile().mkdirs();
            JsonArray arr = new JsonArray();
            for (ChatLine line : payload.lines()) {
                JsonObject obj = new JsonObject();
                String json = BetterChatSerializedLineCache.text(line);
                obj.addProperty("text", json);
                if (!line.rawMessage().isTextOnly()) {
                    obj.add("nodes", BetterChatMessageJson.encode(line.rawMessage()));
                }
                obj.addProperty("time", line.timestampMs());
                if (line.repeatCount() > 1) obj.addProperty("count", line.repeatCount());
                arr.add(obj);
            }
            try (FileWriter writer = new FileWriter(f, StandardCharsets.UTF_8)) {
                GSON.toJson(arr, writer);
            }
            synchronized (BetterChatStoreManager.class) {
                LAST_SAVED_REVISIONS.put(key, payload.revision());
            }
        } catch (Exception e) {
            DebugLog.error("BetterChat save failed for %s", e, key);
        }
    }

    private static void drainSaveQueue(String key, BetterChatStore store) {
        try {
            while (true) {
                long requested;
                long saved;
                synchronized (BetterChatStoreManager.class) {
                    requested = REQUESTED_SAVE_REVISIONS.getOrDefault(key, -1L);
                    saved = LAST_SAVED_REVISIONS.getOrDefault(key, -1L);
                    if (requested <= saved) {
                        SAVE_JOBS.remove(key);
                        return;
                    }
                }
                saveSync(key, store);
            }
        } finally {
            synchronized (BetterChatStoreManager.class) {
                CompletableFuture<?> current = SAVE_JOBS.get(key);
                if (current != null && current.isDone()) {
                    SAVE_JOBS.remove(key);
                }
            }
        }
    }

    private static SavePayload captureSavePayload(BetterChatStore store) {
        synchronized (store) {
            return new SavePayload(store.revision(), store.snapshot());
        }
    }

    private static File fileFor(String key) {
        return betterChatFile(key + ".json");
    }

    private static File betterChatFile(String fileName) {
        Minecraft mc = Minecraft.getInstance();
        return mc.gameDirectory.toPath()
                .resolve(ConfigPaths.root())
                .resolve("betterchat")
                .resolve(fileName)
                .toFile();
    }

    private static BetterChatHoverCache cacheFor(String key) {
        return HOVER_CACHES.computeIfAbsent(key, BetterChatHoverCache::load);
    }

    private static String key(Minecraft mc) {
        // IMPORTANT: Do not split history by dimension. One store + one cache per save/server.
        if (mc == null) return null;

        if (mc.hasSingleplayerServer()) {
            // Singleplayer worlds stored under singleplayer/<level>
            String levelName = mc.getSingleplayerServer() != null ? mc.getSingleplayerServer().getWorldData().getLevelName() : "world";
            return "singleplayer/" + sanitizeKeyPart(levelName);
        }

        ServerData info = mc.getCurrentServer();
        if (info != null) {
            // Multiplayer servers stored under servers/<host> (ignore port)
            String host = stripPort(info.ip);
            return "servers/" + sanitizeKeyPart(host);
        }

        // Fallback
        return "singleplayer/world";
    }

    private static String stripPort(String address) {
        if (address == null) return "unknown";
        String s = address.trim();
        if (s.isEmpty()) return "unknown";

        // IPv6 with brackets: [addr]:port
        if (s.startsWith("[")) {
            int end = s.indexOf(']');
            if (end > 1) {
                return s.substring(1, end);
            }
            return s;
        }

        int lastColon = s.lastIndexOf(':');
        if (lastColon > 0 && lastColon + 1 < s.length()) {
            String tail = s.substring(lastColon + 1);
            boolean digits = true;
            for (int i = 0; i < tail.length(); i++) {
                char c = tail.charAt(i);
                if (c < '0' || c > '9') {
                    digits = false;
                    break;
                }
            }
            if (digits) {
                return s.substring(0, lastColon);
            }
        }

        return s;
    }

    /**
     * Sanitizes a key part so it is safe as a file path component.
     */
    private static String sanitizeKeyPart(String s) {
        if (s == null) return "unknown";
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return "unknown";
        StringBuilder out = null;
        int length = trimmed.length();
        for (int i = 0; i < length; i++) {
            char c = trimmed.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '.'
                    || c == '_'
                    || c == '-';
            if (ok) {
                if (out != null) {
                    out.append(c);
                }
                continue;
            }
            if (out == null) {
                out = new StringBuilder(length);
                out.append(trimmed, 0, i);
            }
            out.append('_');
        }
        return out == null ? trimmed : out.toString();
    }

    private static boolean captureHovers(Component text, BetterChatHoverCache cache) {
        if (text == null || cache == null) return false;
        final boolean[] changed = {false};
        text.visit((style, string) -> {
            if (style != null) {
                HoverEvent hover = style.getHoverEvent();
                if (hover instanceof HoverEvent.ShowItem(net.minecraft.world.item.ItemStackTemplate itemTemplate)) {
                    ItemStack item = itemTemplate.create();
                    String keyId = hoverItemKey(item);
                    if (cache.putItem(keyId, item)) {
                        changed[0] = true;
                    }
                } else if (hover instanceof HoverEvent.ShowEntity(HoverEvent.EntityTooltipInfo entity)) {
                    String keyId = hoverEntityKey(entity);
                    if (cache.putEntity(keyId, entity)) {
                        changed[0] = true;
                    }
                }
            }
            return Optional.empty();
        }, Style.EMPTY);
        return changed[0];
    }

    private static boolean captureHovers(BetterChatMessage message, BetterChatHoverCache cache) {
        if (message == null || cache == null) return false;
        boolean changed = false;
        for (var node : message.nodes()) {
            if (node instanceof ItemNode item) {
                ItemStack stack = item.stack();
                if (!stack.isEmpty()) changed |= cache.putItem(hoverItemKey(stack), stack);
            } else if (node instanceof TextNode text) {
                changed |= captureHovers(text.component(), cache);
            }
        }
        return changed;
    }

    public static String hoverItemKey(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        int compHash = stack.getComponents().hashCode();
        return "item:" + id + ":" + stack.getCount() + ":" + compHash;
    }

    public static String hoverEntityKey(HoverEvent.EntityTooltipInfo content) {
        return "ent:" + content.uuid;
    }

    private static void scheduleCacheSave(String key, BetterChatHoverCache cache) {
        if (key == null || cache == null) return;
        CACHE_SAVE_JOBS.compute(key, (k, prev) -> {
            CompletableFuture<?> base = prev == null
                    ? CompletableFuture.completedFuture(null)
                    : prev.exceptionally(ex -> null);
            return base.thenRunAsync(() -> cache.save(k));
        });
    }

    public static BetterChatRenderer.Layout loadLayout() {
        return new BetterChatRenderer.Layout(16f, 120f);
    }

    public static void saveLayoutAsync(float x, float yBottom) {
    }

    public static boolean hasSavedLayout() {
        return false;
    }

    /**
     * Returns saved screen height used for the layout (framebuffer pixels), or -1 if unknown.
     */
    public static float getLayoutScreenHeight() {
        return -1f;
    }

    /**
     * Update in-memory layout cache (used every render to keep flush/save consistent).
     */
    public static void cacheLayout(float x, float yBottom, float screenH) {
    }

    private record SavePayload(long revision, List<ChatLine> lines) {
    }
}
