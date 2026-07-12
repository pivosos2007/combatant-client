/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.theme;

import com.google.gson.*;
import combatant.client.config.ConfigPaths;
import combatant.client.config.profile.ConfigProfileStorage;
import combatant.client.util.logging.DebugLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

final class ThemeStore {

    private static final Path LEGACY_FILE = ConfigPaths.root().resolve("clickgui_themes.json");
    private static final Path THEME_ROOT = ConfigPaths.root().resolve("themes");
    private static final Path CUSTOM_DIR = THEME_ROOT.resolve("custom");
    private static final Path STATE_FILE = THEME_ROOT.resolve("state.json");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final ThemeStore INSTANCE = new ThemeStore();

    private final List<Themes.ThemeEntry> themes = new ArrayList<>();
    private final List<Themes.ThemeEntry> customThemes = new ArrayList<>();
    private final Map<String, Themes.ThemeEntry> themesById = new LinkedHashMap<>();
    private String currentId = Themes.THEME_CLASSIC;

    private ThemeStore() {
        load();
    }

    public static ThemeStore get() {
        return INSTANCE;
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT).trim();
    }

    private static JsonObject gradientToJson(Themes.GradientSpec spec) {
        Themes.GradientSpec out = spec != null ? spec : new Themes.GradientSpec(false, 0xFFFFFFFF, 0xFFFFFFFF, 90f);
        JsonObject obj = new JsonObject();
        obj.addProperty("enabled", out.enabled());
        obj.addProperty("start", colorToString(out.start()));
        obj.addProperty("end", colorToString(out.end()));
        obj.addProperty("angle", out.angleDeg());
        return obj;
    }

    private static int parseColor(JsonObject obj, String key, int fallback) {
        if (obj == null || !obj.has(key)) return fallback;
        return parseColor(obj.get(key), fallback);
    }

    private static int parseColor(JsonElement raw, int fallback) {
        if (raw == null) return fallback;
        if (raw.isJsonPrimitive()) {
            var prim = raw.getAsJsonPrimitive();
            if (prim.isNumber()) return prim.getAsInt();
            if (prim.isString()) return parseColorString(prim.getAsString(), fallback);
        }
        return fallback;
    }

    static int parseColorString(String s, int fallback) {
        if (s == null || s.isBlank()) return fallback;
        String str = s.trim();
        if (str.startsWith("0x") || str.startsWith("0X")) str = str.substring(2);
        if (str.startsWith("#")) str = str.substring(1);
        try {
            long v = Long.parseUnsignedLong(str, 16);
            if (str.length() <= 6) {
                return (int) (0xFF000000L | v);
            }
            return (int) v;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static String colorToString(int argb) {
        return String.format("#%08X", argb);
    }

    private static JsonObject getObject(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) return null;
        JsonElement el = obj.get(key);
        return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
    }

    private static JsonArray getArray(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) return null;
        JsonElement el = obj.get(key);
        return el != null && el.isJsonArray() ? el.getAsJsonArray() : null;
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        if (obj == null || key == null || !obj.has(key)) return fallback;
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) return fallback;
        var prim = el.getAsJsonPrimitive();
        if (prim.isBoolean()) return prim.getAsBoolean();
        if (prim.isString()) return Boolean.parseBoolean(prim.getAsString());
        return fallback;
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        if (obj == null || key == null || !obj.has(key)) return fallback;
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) return fallback;
        var prim = el.getAsJsonPrimitive();
        return prim.isString() ? prim.getAsString() : fallback;
    }

    private static float getFloat(JsonObject obj, String key, float fallback) {
        if (obj == null || key == null || !obj.has(key)) return fallback;
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) return fallback;
        var prim = el.getAsJsonPrimitive();
        if (prim.isNumber()) return prim.getAsFloat();
        if (prim.isString()) {
            try {
                return Float.parseFloat(prim.getAsString());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    public List<Themes.ThemeEntry> getThemes() {
        return Collections.unmodifiableList(themes);
    }

    public List<Themes.ThemeEntry> getCustomThemes() {
        return Collections.unmodifiableList(customThemes);
    }

    public String getCurrentId() {
        return currentId;
    }

    public void setCurrentId(String id) {
        if (id == null || id.isBlank()) return;
        String key = normalizeId(id);
        if (key.equals(currentId)) return;
        currentId = key;
        saveState();
    }

    public Themes.ThemeEntry getById(String id) {
        if (id == null) return null;
        return themesById.get(normalizeId(id));
    }

    public boolean isCustomId(String id) {
        String key = normalizeId(id);
        if (key.isEmpty()) return false;
        for (Themes.ThemeEntry entry : customThemes) {
            if (entry.getId().equals(key)) return true;
        }
        return false;
    }

    public String uniqueCustomId(String requestedName) {
        String base = ConfigProfileStorage.sanitizeFileName(requestedName == null || requestedName.isBlank() ? "custom_theme" : requestedName);
        if (base.isBlank()) base = "custom_theme";
        String candidate = normalizeId(base);
        int i = 2;
        while (Themes.presetEntry(candidate) != null || themesById.containsKey(candidate)) {
            candidate = normalizeId(base + "_" + i++);
        }
        return candidate;
    }

    public Themes.ThemeEntry saveCustom(Themes.ThemeEntry entry) {
        if (entry == null) return null;
        String id = normalizeId(entry.getId());
        if (id.isBlank()) return null;
        if (Themes.presetEntry(id) != null) {
            id = uniqueCustomId(entry.name());
        }
        Themes.ThemeEntry normalized = new Themes.ThemeEntry(
                id,
                entry.name() == null || entry.name().isBlank() ? id : entry.name().trim(),
                false,
                entry.theme(),
                entry.windowGradient(),
                entry.headerGradient(),
                entry.surfaceGradient(),
                entry.cardGradient(),
                entry.strokeGradient()
        );

        boolean replaced = false;
        for (int i = 0; i < customThemes.size(); i++) {
            if (customThemes.get(i).getId().equals(id)) {
                customThemes.set(i, normalized);
                replaced = true;
                break;
            }
        }
        if (!replaced) customThemes.add(normalized);
        customThemes.sort(Comparator.comparing(Themes.ThemeEntry::name, String.CASE_INSENSITIVE_ORDER));
        rebuildMergedIndex();
        saveCustomFile(normalized);
        return normalized;
    }

    public boolean deleteCustom(String id) {
        String key = normalizeId(id);
        if (key.isBlank()) return false;
        boolean removed = customThemes.removeIf(entry -> entry.getId().equals(key));
        if (!removed) return false;
        try {
            Files.deleteIfExists(customFile(key));
        } catch (IOException e) {
            DebugLog.error("ClickGuiThemeStore: delete custom theme failed", e);
        }
        if (key.equals(currentId)) currentId = Themes.THEME_CLASSIC;
        rebuildMergedIndex();
        saveState();
        return true;
    }

    private void load() {
        currentId = loadCurrentId();
        loadCustomThemes();
        migrateLegacyCustomThemes();
        retireLegacyFile();
        rebuildMergedIndex();
        if (getById(currentId) == null) {
            currentId = Themes.THEME_CLASSIC;
            saveState();
        }
    }

    private String loadCurrentId() {
        if (Files.exists(STATE_FILE)) {
            try {
                JsonElement parsed = JsonParser.parseString(Files.readString(STATE_FILE));
                if (parsed != null && parsed.isJsonObject()) {
                    String current = getString(parsed.getAsJsonObject(), "current", null);
                    if (current != null && !current.isBlank()) return normalizeId(current);
                }
            } catch (Exception e) {
                DebugLog.error("ClickGuiThemeStore: state load failed", e);
            }
        }
        if (Files.exists(LEGACY_FILE)) {
            try {
                JsonElement parsed = JsonParser.parseString(Files.readString(LEGACY_FILE));
                if (parsed != null && parsed.isJsonObject()) {
                    String current = getString(parsed.getAsJsonObject(), "current", null);
                    if (current != null && !current.isBlank()) return normalizeId(current);
                }
            } catch (Exception ignored) {
            }
        }
        return Themes.THEME_CLASSIC;
    }

    private void loadCustomThemes() {
        customThemes.clear();
        if (!Files.isDirectory(CUSTOM_DIR)) return;
        try (var stream = Files.list(CUSTOM_DIR)) {
            stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> {
                        try {
                            JsonElement parsed = JsonParser.parseString(Files.readString(path));
                            if (parsed == null || !parsed.isJsonObject()) return;
                            Themes.ThemeEntry entry = parseEntry(parsed.getAsJsonObject(), false);
                            if (entry == null) return;
                            if (Themes.presetEntry(entry.getId()) != null) return;
                            customThemes.removeIf(existing -> existing.getId().equals(entry.getId()));
                            customThemes.add(entry);
                        } catch (Exception e) {
                            DebugLog.error("ClickGuiThemeStore: custom theme load failed %s", e, path.toAbsolutePath());
                        }
                    });
        } catch (IOException e) {
            DebugLog.error("ClickGuiThemeStore: custom theme list failed", e);
        }
        customThemes.sort(Comparator.comparing(Themes.ThemeEntry::name, String.CASE_INSENSITIVE_ORDER));
    }

    private void migrateLegacyCustomThemes() {
        if (!Files.exists(LEGACY_FILE)) return;
        boolean changed = false;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(LEGACY_FILE));
            if (parsed == null || !parsed.isJsonObject()) return;
            JsonArray arr = getArray(parsed.getAsJsonObject(), "themes");
            if (arr == null) return;
            rebuildMergedIndex();
            for (JsonElement item : arr) {
                if (item == null || !item.isJsonObject()) continue;
                Themes.ThemeEntry entry = parseEntry(item.getAsJsonObject(), false);
                if (entry == null) continue;
                if (Themes.presetEntry(entry.getId()) != null) continue;
                if (isCustomId(entry.getId())) continue;
                saveCustom(entry);
                changed = true;
            }
        } catch (Exception e) {
            DebugLog.error("ClickGuiThemeStore: legacy custom migration failed", e);
        }
        if (changed) saveState();
    }


    private void retireLegacyFile() {
        if (!Files.exists(LEGACY_FILE)) return;
        try {
            Files.deleteIfExists(LEGACY_FILE);
            saveState();
        } catch (IOException e) {
            DebugLog.error("ClickGuiThemeStore: legacy file cleanup failed", e);
        }
    }

    private void rebuildMergedIndex() {
        themes.clear();
        themesById.clear();
        for (Themes.ThemeEntry preset : Themes.presetEntries()) {
            themes.add(preset);
            themesById.put(preset.getId(), preset);
        }
        for (Themes.ThemeEntry custom : customThemes) {
            if (custom == null) continue;
            themes.add(custom);
            themesById.put(custom.getId(), custom);
        }
    }

    private Themes.ThemeEntry parseEntry(JsonObject obj, boolean allowBuiltin) {
        String id = getString(obj, "id", null);
        if (id == null || id.isBlank()) return null;
        String key = normalizeId(id);
        Themes.ThemeEntry preset = Themes.presetEntry(key);
        if (preset != null && !allowBuiltin) return null;

        String name = getString(obj, "name", preset != null ? preset.name() : key);
        boolean builtin = allowBuiltin && getBoolean(obj, "builtin", preset != null && preset.builtin());

        JsonObject colors = getObject(obj, "colors");
        Themes.Theme base = preset != null ? preset.theme() : Theme.classic();
        int windowBg = parseColor(colors, "windowBg", base.windowBg());
        int windowHeader = parseColor(colors, "windowHeader", base.windowHeader());
        int windowStroke = parseColor(colors, "windowStroke", base.windowStroke());
        int surface = parseColor(colors, "surface", base.surface());
        int surfaceHover = parseColor(colors, "surfaceHover", base.surfaceHover());
        int cardEnabled = parseColor(colors, "cardEnabled", base.cardEnabled());
        int cardDisabled = parseColor(colors, "cardDisabled", base.cardDisabled());
        int textPrimary = parseColor(colors, "textPrimary", base.textPrimary());
        int textMuted = parseColor(colors, "textMuted", base.textMuted());
        int accent = parseColor(colors, "accent", base.accent());
        int accentSoft = parseColor(colors, "accentSoft", base.accentSoft());
        int strokeSoft = parseColor(colors, "strokeSoft", base.strokeSoft());

        Themes.Theme theme = new Themes.Theme(
                windowBg,
                windowHeader,
                windowStroke,
                surface,
                surfaceHover,
                cardEnabled,
                cardDisabled,
                textPrimary,
                textMuted,
                accent,
                accentSoft,
                strokeSoft
        );

        JsonObject gradients = getObject(obj, "gradients");
        Themes.GradientSpec windowGradient = parseGradient(
                getObject(gradients, "window"),
                preset != null ? preset.windowGradient() : new Themes.GradientSpec(false, windowBg, windowBg, 90f),
                windowBg
        );
        Themes.GradientSpec headerGradient = parseGradient(
                getObject(gradients, "header"),
                preset != null ? preset.headerGradient() : new Themes.GradientSpec(false, windowHeader, windowHeader, 90f),
                windowHeader
        );
        Themes.GradientSpec surfaceGradient = parseGradient(
                getObject(gradients, "surface"),
                preset != null ? preset.surfaceGradient() : new Themes.GradientSpec(false, surface, surface, 90f),
                surface
        );
        Themes.GradientSpec cardGradient = parseGradient(
                getObject(gradients, "card"),
                preset != null ? preset.cardGradient() : new Themes.GradientSpec(false, cardEnabled, cardEnabled, 90f),
                cardEnabled
        );
        Themes.GradientSpec strokeGradient = parseGradient(
                getObject(gradients, "stroke"),
                preset != null ? preset.strokeGradient() : new Themes.GradientSpec(false, windowStroke, windowStroke, 90f),
                windowStroke
        );

        return new Themes.ThemeEntry(
                key,
                name != null && !name.isBlank() ? name.trim() : key,
                builtin,
                theme,
                windowGradient,
                headerGradient,
                surfaceGradient,
                cardGradient,
                strokeGradient
        );
    }

    private Themes.GradientSpec parseGradient(JsonObject map,
                                              Themes.GradientSpec fallback,
                                              int baseColor) {
        if (map == null || map.size() == 0) return fallback;
        boolean enabled = getBoolean(map, "enabled", fallback.enabled());
        int start = parseColor(map, "start", fallback.start() != 0 ? fallback.start() : baseColor);
        int end = parseColor(map, "end", fallback.end() != 0 ? fallback.end() : baseColor);
        float angle = normalizeAngle(getFloat(map, "angle", fallback.angleDeg()));
        return new Themes.GradientSpec(enabled, start, end, angle);
    }

    private static float normalizeAngle(float angle) {
        if (!Float.isFinite(angle)) return 90f;
        angle %= 360f;
        if (angle < 0f) angle += 360f;
        return angle;
    }

    private void saveState() {
        try {
            Files.createDirectories(THEME_ROOT);
            JsonObject root = new JsonObject();
            root.addProperty("current", currentId);
            Files.writeString(STATE_FILE, GSON.toJson(root) + "\n");
        } catch (IOException e) {
            DebugLog.error("ClickGuiThemeStore: state save failed", e);
        }
    }

    private void saveCustomFile(Themes.ThemeEntry entry) {
        try {
            Files.createDirectories(CUSTOM_DIR);
            Files.writeString(customFile(entry.getId()), GSON.toJson(entryToJson(entry)) + "\n");
        } catch (IOException e) {
            DebugLog.error("ClickGuiThemeStore: custom theme save failed", e);
        }
    }

    private Path customFile(String id) {
        return CUSTOM_DIR.resolve(ConfigProfileStorage.sanitizeFileName(id) + ".json");
    }

    static JsonObject entryToJson(Themes.ThemeEntry entry) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", entry.getId());
        obj.addProperty("name", entry.name());
        obj.addProperty("builtin", false);

        JsonObject colors = new JsonObject();
        Themes.Theme theme = entry.theme();
        colors.addProperty("windowBg", colorToString(theme.windowBg()));
        colors.addProperty("windowHeader", colorToString(theme.windowHeader()));
        colors.addProperty("windowStroke", colorToString(theme.windowStroke()));
        colors.addProperty("surface", colorToString(theme.surface()));
        colors.addProperty("surfaceHover", colorToString(theme.surfaceHover()));
        colors.addProperty("cardEnabled", colorToString(theme.cardEnabled()));
        colors.addProperty("cardDisabled", colorToString(theme.cardDisabled()));
        colors.addProperty("textPrimary", colorToString(theme.textPrimary()));
        colors.addProperty("textMuted", colorToString(theme.textMuted()));
        colors.addProperty("accent", colorToString(theme.accent()));
        colors.addProperty("accentSoft", colorToString(theme.accentSoft()));
        colors.addProperty("strokeSoft", colorToString(theme.strokeSoft()));
        obj.add("colors", colors);

        JsonObject gradients = new JsonObject();
        gradients.add("window", gradientToJson(entry.windowGradient()));
        gradients.add("header", gradientToJson(entry.headerGradient()));
        gradients.add("surface", gradientToJson(entry.surfaceGradient()));
        gradients.add("card", gradientToJson(entry.cardGradient()));
        gradients.add("stroke", gradientToJson(entry.strokeGradient()));
        obj.add("gradients", gradients);
        return obj;
    }
}
