/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import com.google.gson.*;
import combatant.client.config.ConfigPaths;
import combatant.client.util.logging.DebugLog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

enum ColorPresetStore {
    ;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = ConfigPaths.root().resolve("color_presets.json");
    private static final int MAX_PRESETS = 24;
    private static final List<Integer> PRESETS = new ArrayList<>();
    private static boolean loaded;

    static List<Integer> presets() {
        ensureLoaded();
        return Collections.unmodifiableList(PRESETS);
    }

    static void add(int argb) {
        ensureLoaded();
        int normalized = argb;
        PRESETS.removeIf(c -> c == normalized);
        PRESETS.add(0, normalized);
        while (PRESETS.size() > MAX_PRESETS) PRESETS.remove(PRESETS.size() - 1);
        save();
    }

    static boolean removeClosest(int argb) {
        ensureLoaded();
        if (PRESETS.isEmpty()) return false;
        int bestIndex = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < PRESETS.size(); i++) {
            int distance = colorDistance(PRESETS.get(i), argb);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        if (bestIndex < 0) return false;
        PRESETS.remove(bestIndex);
        save();
        return true;
    }

    static boolean removeExact(int argb) {
        ensureLoaded();
        boolean removed = PRESETS.removeIf(c -> c == argb);
        if (removed) save();
        return removed;
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        PRESETS.clear();
        PRESETS.add(0xFFFFFFFF);
        PRESETS.add(0xFFFF4F6D);
        PRESETS.add(0xFFFFA94D);
        PRESETS.add(0xFFFFE066);
        PRESETS.add(0xFF69DB7C);
        PRESETS.add(0xFF4DABF7);
        PRESETS.add(0xFF845EF7);
        PRESETS.add(0xFFFF6BCB);
        if (!Files.isRegularFile(FILE)) {
            save();
            return;
        }
        try {
            String text = Files.readString(FILE);
            JsonElement root = JsonParser.parseString(text);
            JsonArray arr = root.isJsonArray() ? root.getAsJsonArray()
                    : root.isJsonObject() && root.getAsJsonObject().has("presets") ? root.getAsJsonObject().getAsJsonArray("presets")
                    : null;
            if (arr == null) return;
            PRESETS.clear();
            for (JsonElement el : arr) {
                if (el == null || !el.isJsonPrimitive()) continue;
                Integer parsed = parseColor(el.getAsString());
                if (parsed == null) continue;
                if (!PRESETS.contains(parsed)) PRESETS.add(parsed);
                if (PRESETS.size() >= MAX_PRESETS) break;
            }
            if (PRESETS.isEmpty()) PRESETS.add(0xFFFFFFFF);
        } catch (Throwable t) {
            DebugLog.warn("[Combatant][ColorPresets] Failed to load %s: %s", FILE, t.getMessage());
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (int color : PRESETS) {
                arr.add(String.format("#%08X", color));
            }
            root.add("presets", arr);
            Files.writeString(FILE, GSON.toJson(root));
        } catch (Throwable t) {
            DebugLog.warn("[Combatant][ColorPresets] Failed to save %s: %s", FILE, t.getMessage());
        }
    }

    private static Integer parseColor(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        try {
            long v = Long.parseUnsignedLong(s, 16);
            if (s.length() == 6) return (int) (0xFF000000L | v);
            if (s.length() == 8) return (int) v;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int colorDistance(int a, int b) {
        int da = ((a >>> 24) & 0xFF) - ((b >>> 24) & 0xFF);
        int dr = ((a >>> 16) & 0xFF) - ((b >>> 16) & 0xFF);
        int dg = ((a >>> 8) & 0xFF) - ((b >>> 8) & 0xFF);
        int db = (a & 0xFF) - (b & 0xFF);
        return da * da + dr * dr + dg * dg + db * db;
    }
}
