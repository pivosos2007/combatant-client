/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config;

import com.google.gson.*;
import de.marhali.json5.*;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import combatant.client.config.values.ConfigValue;
import combatant.client.features.gui.hud.AbstractHudElement;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.module.Module;
import combatant.client.features.relations.EntityFilters;
import combatant.client.util.logging.DebugLog;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public enum ConfigSerializer {
    ;

    private static final Path CONFIG_DIR = ConfigPaths.root();
    private static final long SAVE_DEBOUNCE_MS = 220L;
    private static final ScheduledExecutorService SAVE_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Combatant-ConfigSaver");
                t.setDaemon(true);
                return t;
            });
    private static final Object SAVE_LOCK = new Object();
    private static final Map<Path, String> PENDING_TEXT = new HashMap<>();
    private static final Map<Path, ScheduledFuture<?>> PENDING_TASKS = new HashMap<>();
    private static final Map<Path, Long> PENDING_TOKENS = new HashMap<>();
    // One Json5 instance for utilities (player relations, filters, debug config, etc.)
    private static final Json5 JSON5 = Json5.builder(builder -> builder
            .trailingComma()  // allow trailing commas
            .indentFactor(2)  // pretty output
            .quoteSingle()    // use " quotes
            .build()
    );
    // Gson serializer for modules (strict JSON)
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static long SAVE_TOKEN_SEQ = 0L;

    // =============================================================
    //   Bulk load / save helpers
    // =============================================================

    @SuppressWarnings("unused") // kept for bulk-loading singletons via ClassGraph
    public static void loadAll() {
        try (ScanResult sc = new ClassGraph()
                .enableClassInfo()
                .acceptPackages("combatant.client")
                .scan()) {

            for (ClassInfo ci : sc.getClassesImplementing(ConfigObject.class)) {
                Class<?> cls = ci.loadClass();
                ConfigObject inst = singletonConfigObjectFor(cls);
                if (inst != null) {
                    load(inst);
                }
            }

        } catch (Exception e) {
            DebugLog.error("ConfigSerializer.loadAll failed", e);
        }
    }

    public static void saveAll() {
        try (ScanResult sc = new ClassGraph()
                .enableClassInfo()
                .acceptPackages("combatant.client")
                .scan()) {

            for (ClassInfo ci : sc.getClassesImplementing(ConfigObject.class)) {
                Class<?> cls = ci.loadClass();
                ConfigObject inst = singletonConfigObjectFor(cls);
                if (inst != null) {
                    save(inst);
                }
            }

        } catch (Exception e) {
            DebugLog.error("ConfigSerializer.saveAll failed", e);
        }
    }

    private static ConfigObject singletonConfigObjectFor(Class<?> cls) throws IllegalAccessException {
        if (cls == null) return null;

        for (Field field : cls.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!ConfigObject.class.isAssignableFrom(field.getType())) continue;

            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof ConfigObject configObject) {
                return configObject;
            }
        }

        return null;
    }

    // =============================================================
    //   Dispatch
    // =============================================================

    public static void save(ConfigObject obj) {
        if (obj instanceof HudGlobalConfig) return;
        saveNow(obj);
    }

    public static void requestSave(ConfigObject obj) {
        if (obj == null || obj instanceof HudGlobalConfig) return;
        PendingWrite write;
        try {
            write = buildWrite(obj);
        } catch (Exception e) {
            DebugLog.error("Failed to serialize config %s", e, fileOf(obj).toAbsolutePath());
            return;
        }
        if (write == null) return;

        long token;
        synchronized (SAVE_LOCK) {
            token = ++SAVE_TOKEN_SEQ;
            PENDING_TEXT.put(write.path, write.text);
            PENDING_TOKENS.put(write.path, token);

            ScheduledFuture<?> prev = PENDING_TASKS.get(write.path);
            if (prev != null) prev.cancel(false);

            ScheduledFuture<?> task = SAVE_EXECUTOR.schedule(
                    () -> flushPending(write.path, token),
                    SAVE_DEBOUNCE_MS,
                    TimeUnit.MILLISECONDS
            );
            PENDING_TASKS.put(write.path, task);
        }
    }

    public static void load(ConfigObject obj) {
        if (obj instanceof HudGlobalConfig) return;
        if (isModule(obj)) {
            loadModule(obj);
        } else if (isJsonConfig(obj)) {
            loadJson(obj);
        } else {
            loadJson5(obj);
        }
    }

    private static boolean isModule(ConfigObject obj) {
        return obj instanceof Module;
    }

    private static boolean isJsonConfig(ConfigObject obj) {
        return obj instanceof JsonConfigObject;
    }

    private static String configNameOf(ConfigObject obj) {
        String name = null;
        if (obj instanceof ConfigNameProvider provider) {
            name = provider.getConfigName();
        }
        if (name == null || name.isBlank()) {
            name = obj.getClass().getSimpleName();
        }
        return sanitizeConfigName(name);
    }

    private static String sanitizeConfigName(String name) {
        if (name == null) return "config";
        String base = name.trim().toLowerCase(Locale.ROOT);
        if (base.isEmpty()) return "config";
        StringBuilder out = new StringBuilder(base.length());
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                out.append(c);
            } else {
                out.append('_');
            }
        }
        return out.toString();
    }

    private static Path namedJsonFile(String configName) {
        return CONFIG_DIR.resolve(sanitizeConfigName(configName) + ".json");
    }

    private static Path namedJson5File(String configName) {
        return CONFIG_DIR.resolve(sanitizeConfigName(configName) + ".json5");
    }

    private static Path moduleFile(ConfigObject obj) {
        String category = "uncategorized";
        if (obj instanceof Module module && module.getCategory() != null) {
            category = sanitizeConfigName(module.getCategory().name());
        }
        return CONFIG_DIR.resolve("modules")
                .resolve(category)
                .resolve(configNameOf(obj) + ".json");
    }

    private static Path jsonFile(ConfigObject obj) {
        if (obj instanceof DraggableHudElement) {
            return CONFIG_DIR.resolve("hud")
                    .resolve("draggable")
                    .resolve(configNameOf(obj) + ".json");
        }
        if (obj instanceof AbstractHudElement) {
            return CONFIG_DIR.resolve("hud")
                    .resolve("nondraggable")
                    .resolve(configNameOf(obj) + ".json");
        }
        return namedJsonFile(configNameOf(obj));
    }

    private static Path utilFile(ConfigObject obj) {
        return isJsonConfig(obj)
                ? jsonFile(obj)
                : namedJson5File(configNameOf(obj));
    }

    private static Path fileOf(ConfigObject obj) {
        return isModule(obj) ? moduleFile(obj) : utilFile(obj);
    }

    // =============================================================
    //   Module (Gson JSON)
    // =============================================================

    private static PendingWrite buildModuleWrite(ConfigObject obj) {
        try {
            JsonObject root = new JsonObject();
            for (ConfigValue<?> v : obj.getConfigValues()) {
                root.add(v.getName(), toJsonElement(v.toJson()));
            }

            String text = GSON.toJson(root) + "\n";
            Path out = moduleFile(obj);
            return new PendingWrite(out, text);
        } catch (Exception e) {
            DebugLog.error("Failed to serialize config %s", e, fileOf(obj).toAbsolutePath());
            return null;
        }
    }

    private static void loadModule(ConfigObject obj) {
        try {
            Path path = moduleFile(obj);

            if (!Files.exists(path)) {
                DebugLog.config("Config defaults created: %s", path.toAbsolutePath());
                save(obj);
                return;
            }

            DebugLog.config("Config loading: %s", path.toAbsolutePath());

            String txt = Files.readString(path);
            JsonElement parsed = GSON.fromJson(txt, JsonElement.class);
            if (!(parsed instanceof JsonObject root)) return;

            Module module = (Module) obj;

            JsonElement legacySettings = root.get("settings");
            if (legacySettings != null) {
                module.applyLegacySettings(unwrapJsonElement(legacySettings));
            }

            boolean usedLegacyAlias = false;
            for (ConfigValue<?> v : obj.getConfigValues()) {
                JsonLookup lookup = findJsonElement(obj, root, v.getName());
                if (lookup.element() != null) {
                    v.fromJson(unwrapJsonElement(lookup.element()));
                    usedLegacyAlias |= lookup.alias();
                }
            }

            module.syncSettingsAfterLoad();
            if (usedLegacyAlias) {
                requestSave(obj);
            }

        } catch (Exception e) {
            DebugLog.error("Failed to load config %s", e, fileOf(obj).toAbsolutePath());
        }
    }

    // =============================================================
    //   JSON (Gson) for non-Module configs
    // =============================================================

    private static PendingWrite buildJsonWrite(ConfigObject obj) {
        try {
            JsonObject root = new JsonObject();
            for (ConfigValue<?> v : obj.getConfigValues()) {
                root.add(v.getName(), toJsonElement(v.toJson()));
            }

            String text = GSON.toJson(root) + "\n";
            Path out = jsonFile(obj);
            return new PendingWrite(out, text);
        } catch (Exception e) {
            DebugLog.error("Failed to serialize config %s", e, fileOf(obj).toAbsolutePath());
            return null;
        }
    }

    private static void loadJson(ConfigObject obj) {
        try {
            Path path = jsonFile(obj);
            if (!Files.exists(path)) {
                DebugLog.config("Config defaults created: %s", path.toAbsolutePath());
                save(obj);
                return;
            }

            DebugLog.config("Config loading: %s", path.toAbsolutePath());

            String txt = Files.readString(path);
            JsonElement parsed = GSON.fromJson(txt, JsonElement.class);
            if (!(parsed instanceof JsonObject root)) return;

            boolean missing = false;
            boolean usedLegacyAlias = false;
            for (ConfigValue<?> v : obj.getConfigValues()) {
                JsonLookup lookup = findJsonElement(obj, root, v.getName());
                if (lookup.element() != null) {
                    v.fromJson(unwrapJsonElement(lookup.element()));
                    usedLegacyAlias |= lookup.alias();
                } else {
                    missing = true;
                }
            }

            if (missing || usedLegacyAlias) {
                requestSave(obj);
            }

        } catch (Exception e) {
            DebugLog.error("Failed to load config %s", e, fileOf(obj).toAbsolutePath());
        }
    }

    // =============================================================
    //   Utilities (Json5)
    // =============================================================

    private static PendingWrite buildJson5Write(ConfigObject obj) {
        try {
            Json5Object root = new Json5Object();

            for (ConfigValue<?> v : obj.getConfigValues()) {
                Object raw = v.toJson();
                root.add(v.getName(), toJson5Element(raw));
            }

            String text = JSON5.serialize(root);

            String sb = text + "\n";

            Path out = utilFile(obj);
            if (obj instanceof EntityFilters) {
                DebugLog.config("[EntityFilters][SAVE] to=%s data=%s", out.toAbsolutePath(), text);
            }
            return new PendingWrite(out, sb);
        } catch (Exception e) {
            DebugLog.error("Failed to serialize config %s", e, fileOf(obj).toAbsolutePath());
            return null;
        }
    }

    private static void loadJson5(ConfigObject obj) {
        try {
            Path path = utilFile(obj);
            if (!Files.exists(path)) {
                DebugLog.config("Config defaults created: %s", path.toAbsolutePath());
                save(obj);
                return;
            }

            DebugLog.config("Config loading: %s", path.toAbsolutePath());

            String txt = Files.readString(path);
            Json5Element parsed = JSON5.parse(txt);

            if (!(parsed instanceof Json5Object root)) {
                return;
            }

            Map<String, Json5Element> map = root.asMap();

            boolean missing = false;
            boolean usedLegacyAlias = false;
            for (ConfigValue<?> v : obj.getConfigValues()) {
                Json5Lookup lookup = findJson5Element(obj, map, v.getName());
                if (lookup.element() != null) {
                    v.fromJson(unwrapJson5(lookup.element()));
                    usedLegacyAlias |= lookup.alias();
                } else {
                    missing = true;
                }
            }

            if (obj instanceof Module module) {
                module.syncSettingsAfterLoad();
            }
            if (missing || usedLegacyAlias) {
                requestSave(obj);
            }

        } catch (Exception e) {
            DebugLog.error("Failed to load config %s", e, fileOf(obj).toAbsolutePath());
        }
    }


    private static JsonLookup findJsonElement(ConfigObject obj, JsonObject root, String valueName) {
        if (root == null || valueName == null) return JsonLookup.missing();
        JsonElement direct = root.get(valueName);
        if (direct != null && !direct.isJsonNull()) {
            return new JsonLookup(direct, false);
        }
        if (obj instanceof ConfigValueAliasProvider provider) {
            List<String> aliases = provider.getLegacyValueNames(valueName);
            if (aliases != null) {
                for (String alias : aliases) {
                    if (alias == null || alias.isBlank()) continue;
                    JsonElement legacy = root.get(alias);
                    if (legacy != null && !legacy.isJsonNull()) {
                        return new JsonLookup(legacy, true);
                    }
                }
            }
        }
        return JsonLookup.missing();
    }

    private static Json5Lookup findJson5Element(ConfigObject obj, Map<String, Json5Element> map, String valueName) {
        if (map == null || valueName == null) return Json5Lookup.missing();
        Json5Element direct = map.get(valueName);
        if (direct != null) {
            return new Json5Lookup(direct, false);
        }
        if (obj instanceof ConfigValueAliasProvider provider) {
            List<String> aliases = provider.getLegacyValueNames(valueName);
            if (aliases != null) {
                for (String alias : aliases) {
                    if (alias == null || alias.isBlank()) continue;
                    Json5Element legacy = map.get(alias);
                    if (legacy != null) {
                        return new Json5Lookup(legacy, true);
                    }
                }
            }
        }
        return Json5Lookup.missing();
    }

    private static JsonElement toJsonElement(Object v) {
        if (v == null) return JsonNull.INSTANCE;
        if (v instanceof JsonElement el) return el;
        if (v instanceof Boolean b) return new JsonPrimitive(b);
        if (v instanceof Number n) return new JsonPrimitive(n);
        if (v instanceof CharSequence s) return new JsonPrimitive(s.toString());
        if (v instanceof Map<?, ?> map) {
            JsonObject obj = new JsonObject();
            for (var e : map.entrySet()) {
                obj.add(String.valueOf(e.getKey()), toJsonElement(e.getValue()));
            }
            return obj;
        }
        if (v instanceof Iterable<?> it) {
            JsonArray arr = new JsonArray();
            for (Object el : it) arr.add(toJsonElement(el));
            return arr;
        }
        return new JsonPrimitive(String.valueOf(v));
    }

    private static Json5Element toJson5Element(Object v) {
        if (v == null) {
            return Json5Primitive.fromNull();
        }
        if (v instanceof Json5Element el) {
            return el;
        }
        if (v instanceof Boolean b) {
            return Json5Primitive.fromBoolean(b);
        }
        if (v instanceof Number n) {
            return Json5Primitive.fromNumber(n);
        }
        if (v instanceof CharSequence s) {
            return Json5Primitive.fromString(s.toString());
        }
        if (v instanceof Map<?, ?> map) {
            Json5Object obj = new Json5Object();
            for (var e : map.entrySet()) {
                String key = String.valueOf(e.getKey());
                obj.add(key, toJson5Element(e.getValue()));
            }
            return obj;
        }
        if (v instanceof Iterable<?> it) {
            Json5Array arr = new Json5Array();
            for (Object el : it) {
                arr.add(toJson5Element(el));
            }
            return arr;
        }

        return Json5Primitive.fromString(String.valueOf(v));
    }

    // =============================================================
    //   Helpers
    // =============================================================

    private static Object unwrapJsonElement(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonPrimitive()) {
            JsonPrimitive p = el.getAsJsonPrimitive();
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) return p.getAsNumber();
            return p.getAsString();
        }
        if (el.isJsonArray()) {
            List<Object> out = new ArrayList<>();
            for (JsonElement child : el.getAsJsonArray()) {
                out.add(unwrapJsonElement(child));
            }
            return out;
        }
        if (el.isJsonObject()) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (var e : el.getAsJsonObject().entrySet()) {
                out.put(e.getKey(), unwrapJsonElement(e.getValue()));
            }
            return out;
        }
        return null;
    }

    private static Object unwrapJson5(Json5Element el) {
        if (el == null || el.isJson5Null()) return null;
        if (el.isJson5Primitive()) {
            Json5Primitive p = el.getAsJson5Primitive();
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) return p.getAsNumber();
            return p.getAsString();
        }
        if (el.isJson5Array()) {
            List<Object> out = new ArrayList<>();
            for (Json5Element child : el.getAsJson5Array()) {
                out.add(unwrapJson5(child));
            }
            return out;
        }
        if (el.isJson5Object()) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (var e : el.getAsJson5Object().asMap().entrySet()) {
                out.put(e.getKey(), unwrapJson5(e.getValue()));
            }
            return out;
        }
        return null;
    }

    private static PendingWrite buildWrite(ConfigObject obj) {
        if (obj == null) return null;
        if (isModule(obj)) return buildModuleWrite(obj);
        if (isJsonConfig(obj)) return buildJsonWrite(obj);
        return buildJson5Write(obj);
    }

    private static void saveNow(ConfigObject obj) {
        PendingWrite write;
        try {
            write = buildWrite(obj);
        } catch (Exception e) {
            DebugLog.error("Failed to serialize config %s", e, fileOf(obj).toAbsolutePath());
            return;
        }
        if (write == null) return;
        clearPending(write.path);
        writeConfig(write.path, write.text);
    }

    // =============================================================
    //   Async write helpers
    // =============================================================

    private static void flushPending(Path path, long token) {
        String text;
        synchronized (SAVE_LOCK) {
            Long current = PENDING_TOKENS.get(path);
            if (current == null || current != token) return;
            text = PENDING_TEXT.remove(path);
            PENDING_TOKENS.remove(path);
            PENDING_TASKS.remove(path);
        }
        if (text != null) {
            writeConfig(path, text);
        }
    }

    private static void clearPending(Path path) {
        synchronized (SAVE_LOCK) {
            ScheduledFuture<?> prev = PENDING_TASKS.remove(path);
            if (prev != null) prev.cancel(false);
            PENDING_TEXT.remove(path);
            PENDING_TOKENS.remove(path);
        }
    }

    private static void writeConfig(Path out, String text) {
        try {
            Path parent = out.getParent();
            Files.createDirectories(parent != null ? parent : CONFIG_DIR);
            Files.writeString(out, text);
            DebugLog.config("Config saved: %s", out.toAbsolutePath());
        } catch (IOException e) {
            DebugLog.error("Failed to save config %s", e, out.toAbsolutePath());
        }
    }

    private record JsonLookup(JsonElement element, boolean alias) {
        static JsonLookup missing() {
            return new JsonLookup(null, false);
        }
    }

    private record Json5Lookup(Json5Element element, boolean alias) {
        static Json5Lookup missing() {
            return new Json5Lookup(null, false);
        }
    }

    private record PendingWrite(Path path, String text) {
    }
}
