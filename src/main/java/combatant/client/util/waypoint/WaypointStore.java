/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import combatant.client.config.ConfigPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Per-server / per-singleplayer-world waypoint list persisted as JSON below the subsystems config root.
 * All access happens on the client thread.
 */
public final class WaypointStore {
    public static final int MAX_WAYPOINTS = 512;
    public static final int DEFAULT_COLOR = 0xFF55FFFF;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final WaypointStore INSTANCE = new WaypointStore();

    private final List<Waypoint> waypoints = new ArrayList<>();
    private String loadedKey;

    private WaypointStore() {
    }

    public static WaypointStore get() {
        return INSTANCE;
    }

    /** A saved point. {@code dimension} is a dimension identifier such as {@code minecraft:overworld}. */
    public record Waypoint(String name, double x, double y, double z, String dimension, int color, long createdAt) {
    }

    /** Identity of the current server or singleplayer world; null while no world is joined. */
    public static String currentKey(Minecraft mc) {
        if (mc == null || mc.level == null) return null;
        ServerData server = mc.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return "server/" + server.ip.trim().toLowerCase(Locale.ROOT);
        }
        if (mc.getSingleplayerServer() != null) {
            return "singleplayer/" + mc.getSingleplayerServer().getWorldData().getLevelName();
        }
        return "unknown";
    }

    /** Current world's waypoints (unmodifiable view; reloads when the world identity changed). */
    public List<Waypoint> list(Minecraft mc) {
        ensureLoaded(mc);
        return Collections.unmodifiableList(waypoints);
    }

    /** Adds or replaces (case-insensitive name match). Returns false when unavailable or the limit is reached. */
    public boolean add(Minecraft mc, Waypoint waypoint) {
        ensureLoaded(mc);
        if (loadedKey == null || waypoint == null || waypoint.name() == null || waypoint.name().isBlank()) return false;
        int existing = indexOf(waypoint.name());
        if (existing >= 0) {
            waypoints.set(existing, waypoint);
        } else {
            if (waypoints.size() >= MAX_WAYPOINTS) return false;
            waypoints.add(waypoint);
        }
        save();
        return true;
    }

    public boolean remove(Minecraft mc, String name) {
        ensureLoaded(mc);
        if (loadedKey == null || name == null) return false;
        int index = indexOf(name);
        if (index < 0) return false;
        waypoints.remove(index);
        save();
        return true;
    }

    /** @return number of removed waypoints */
    public int clear(Minecraft mc) {
        ensureLoaded(mc);
        if (loadedKey == null) return 0;
        int count = waypoints.size();
        if (count > 0) {
            waypoints.clear();
            save();
        }
        return count;
    }

    /** Returns a name not yet used, derived from the given base ("Death", "Death 2", ...). */
    public String uniqueName(Minecraft mc, String base) {
        ensureLoaded(mc);
        if (indexOf(base) < 0) return base;
        for (int i = 2; ; i++) {
            String candidate = base + " " + i;
            if (indexOf(candidate) < 0) return candidate;
        }
    }

    private int indexOf(String name) {
        for (int i = 0; i < waypoints.size(); i++) {
            if (waypoints.get(i).name().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private void ensureLoaded(Minecraft mc) {
        String key = currentKey(mc);
        if (key == null) {
            // No world joined: keep nothing in memory so stale points never render.
            waypoints.clear();
            loadedKey = null;
            return;
        }
        if (key.equals(loadedKey)) return;
        loadedKey = key;
        waypoints.clear();
        load(key);
    }

    private static Path fileFor(String key) {
        return ConfigPaths.subsystemFile("waypoints/" + key, ".json");
    }

    private void load(String key) {
        Path file = fileFor(key);
        if (!Files.isRegularFile(file)) return;
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) return;
            JsonArray array = root.getAsJsonObject().getAsJsonArray("waypoints");
            if (array == null) return;
            for (JsonElement element : array) {
                if (!element.isJsonObject() || waypoints.size() >= MAX_WAYPOINTS) continue;
                JsonObject o = element.getAsJsonObject();
                if (!o.has("name") || !o.has("x") || !o.has("y") || !o.has("z")) continue;
                waypoints.add(new Waypoint(
                        o.get("name").getAsString(),
                        o.get("x").getAsDouble(),
                        o.get("y").getAsDouble(),
                        o.get("z").getAsDouble(),
                        o.has("dimension") ? o.get("dimension").getAsString() : "minecraft:overworld",
                        o.has("color") ? o.get("color").getAsInt() : DEFAULT_COLOR,
                        o.has("createdAt") ? o.get("createdAt").getAsLong() : 0L));
            }
        } catch (IOException | RuntimeException ignored) {
            // Corrupt or unreadable file: start empty; the next save rewrites it.
            waypoints.clear();
        }
    }

    private void save() {
        if (loadedKey == null) return;
        Path file = fileFor(loadedKey);
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        for (Waypoint w : waypoints) {
            JsonObject o = new JsonObject();
            o.addProperty("name", w.name());
            o.addProperty("x", w.x());
            o.addProperty("y", w.y());
            o.addProperty("z", w.z());
            o.addProperty("dimension", w.dimension());
            o.addProperty("color", w.color());
            o.addProperty("createdAt", w.createdAt());
            array.add(o);
        }
        root.add("waypoints", array);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Persistence is best effort; in-memory state stays authoritative for the session.
        }
    }
}
