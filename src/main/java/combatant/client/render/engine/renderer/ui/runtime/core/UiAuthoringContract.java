/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Canonical authoring contract shared by Java runtime validation and script bootstrap.
 *
 * <p>The source of truth is {@code /assets/combatant/ui/api/ui.contract.json}. Java enums,
 * script factories and TypeScript declarations are verified against it by the Gradle
 * {@code verifyUiApiContract} task. Runtime code reads the same contract instead of carrying
 * duplicate lists of style/promoted/reserved keys.</p>
 */
public final class UiAuthoringContract {
    public static final String RESOURCE_PATH = "/assets/combatant/ui/api/ui.contract.json";

    private static final ContractData DATA = load();

    private UiAuthoringContract() {
    }

    public static int version() {
        return DATA.version;
    }

    public static Set<String> nodeTypes() {
        return DATA.nodeTypes;
    }

    public static Set<String> styleKeys() {
        return DATA.styleKeys;
    }

    public static Set<String> normalizedStyleKeys() {
        return DATA.normalizedStyleKeys;
    }

    public static Map<String, String> promotedStyles() {
        return DATA.promotedStyles;
    }

    public static Map<String, String> eventAliases() {
        return DATA.eventAliases;
    }

    public static Set<String> reservedNodeKeys() {
        return DATA.reservedNodeKeys;
    }

    public static Map<String, Set<String>> styleValues() {
        return DATA.styleValues;
    }

    public static String rawJson() {
        return DATA.rawJson;
    }

    public static String canonicalNodeType(String raw) {
        if (raw == null || raw.isBlank()) return "panel";
        return raw.trim().replace('-', '_').toLowerCase(Locale.ROOT);
    }

    public static boolean isKnownNodeType(String raw) {
        return DATA.nodeTypes.contains(canonicalNodeType(raw));
    }

    public static boolean isKnownStyleKey(String raw) {
        if (raw == null || raw.isBlank()) return false;
        return DATA.normalizedStyleKeys.contains(normalizeStyleKey(raw));
    }

    public static Set<String> acceptedStyleValues(String key) {
        if (key == null) return Set.of();
        Set<String> accepted = DATA.styleValues.get(key);
        if (accepted == null) {
            accepted = DATA.styleValues.get(canonicalStyleKey(key));
        }
        return accepted != null ? accepted : Set.of();
    }

    public static boolean acceptsStyleValue(String key, String value) {
        if (key == null || value == null) return true;
        Set<String> accepted = acceptedStyleValues(key);
        return accepted.isEmpty() || accepted.contains(value.trim().toLowerCase(Locale.ROOT));
    }

    private static ContractData load() {
        String raw = readResource();
        JsonObject root = JsonParser.parseString(raw).getAsJsonObject();

        int version = root.has("version") ? root.get("version").getAsInt() : 0;
        Set<String> nodeTypes = stringSet(root.get("nodeTypes"), false);
        Set<String> styleKeys = stringSet(root.get("styleKeys"), false);
        Set<String> normalizedStyleKeys = new LinkedHashSet<>(styleKeys.size());
        for (String key : styleKeys) {
            normalizedStyleKeys.add(normalizeStyleKey(key));
        }

        Map<String, String> promotedStyles = stringMap(root.get("promotedStyles"));
        Map<String, String> eventAliases = stringMap(root.get("eventAliases"));
        Set<String> structural = stringSet(root.get("structuralNodeKeys"), false);

        Set<String> reserved = new LinkedHashSet<>(structural);
        reserved.addAll(promotedStyles.keySet());
        reserved.addAll(eventAliases.keySet());

        Map<String, Set<String>> styleValues = stringSetMap(root.get("styleValues"));

        validate(version, nodeTypes, styleKeys, promotedStyles, styleValues);

        return new ContractData(
                version,
                immutableSet(nodeTypes),
                immutableSet(styleKeys),
                immutableSet(normalizedStyleKeys),
                Collections.unmodifiableMap(new LinkedHashMap<>(promotedStyles)),
                Collections.unmodifiableMap(new LinkedHashMap<>(eventAliases)),
                immutableSet(reserved),
                immutableSetMap(styleValues),
                raw
        );
    }

    private static void validate(
            int version,
            Set<String> nodeTypes,
            Set<String> styleKeys,
            Map<String, String> promotedStyles,
            Map<String, Set<String>> styleValues
    ) {
        if (version <= 0) {
            throw new IllegalStateException("UI authoring contract version must be positive.");
        }
        if (nodeTypes.isEmpty()) {
            throw new IllegalStateException("UI authoring contract must declare nodeTypes.");
        }
        if (styleKeys.isEmpty()) {
            throw new IllegalStateException("UI authoring contract must declare styleKeys.");
        }

        Set<String> enumTypes = new LinkedHashSet<>();
        for (UiNodeType type : UiNodeType.values()) {
            enumTypes.add(type.name().toLowerCase(Locale.ROOT));
        }
        if (!enumTypes.equals(nodeTypes)) {
            Set<String> missingInEnum = new LinkedHashSet<>(nodeTypes);
            missingInEnum.removeAll(enumTypes);
            Set<String> missingInContract = new LinkedHashSet<>(enumTypes);
            missingInContract.removeAll(nodeTypes);
            throw new IllegalStateException(
                    "UI node-type contract mismatch. Missing in UiNodeType=" + missingInEnum
                            + ", missing in ui.contract.json=" + missingInContract
            );
        }

        for (Map.Entry<String, String> entry : promotedStyles.entrySet()) {
            if (!styleKeys.contains(entry.getValue())) {
                throw new IllegalStateException(
                        "UI promoted style '" + entry.getKey() + "' targets unknown style key '"
                                + entry.getValue() + "'."
                );
            }
        }
        for (String key : styleValues.keySet()) {
            if (!styleKeys.contains(key) && !promotedStyles.containsKey(key)) {
                throw new IllegalStateException(
                        "UI styleValues declares unknown key '" + key + "'."
                );
            }
        }
    }

    private static String readResource() {
        try (InputStream stream = UiAuthoringContract.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                throw new IllegalStateException("Missing UI authoring contract resource: " + RESOURCE_PATH);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read UI authoring contract: " + RESOURCE_PATH, e);
        }
    }

    private static Set<String> stringSet(JsonElement value, boolean lowerCase) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (value == null || !value.isJsonArray()) return out;
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonPrimitive()) continue;
            String text = element.getAsString();
            if (text == null || text.isBlank()) continue;
            out.add(lowerCase ? text.trim().toLowerCase(Locale.ROOT) : text.trim());
        }
        return out;
    }

    private static Map<String, String> stringMap(JsonElement value) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (value == null || !value.isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) continue;
            out.put(entry.getKey(), entry.getValue().getAsString());
        }
        return out;
    }

    private static Map<String, Set<String>> stringSetMap(JsonElement value) {
        LinkedHashMap<String, Set<String>> out = new LinkedHashMap<>();
        if (value == null || !value.isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
            out.put(entry.getKey(), immutableSet(stringSet(entry.getValue(), true)));
        }
        return out;
    }

    private static Set<String> immutableSet(Set<String> source) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }

    private static Map<String, Set<String>> immutableSetMap(Map<String, Set<String>> source) {
        LinkedHashMap<String, Set<String>> copy = new LinkedHashMap<>(source.size());
        source.forEach((key, value) -> copy.put(key, immutableSet(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static String normalizeStyleKey(String key) {
        StringBuilder out = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '-' || c == '_' || Character.isWhitespace(c)) continue;
            out.append(Character.toLowerCase(c));
        }
        return out.toString();
    }

    private static String canonicalStyleKey(String raw) {
        String normalized = normalizeStyleKey(raw);
        for (String key : DATA.styleKeys) {
            if (normalizeStyleKey(key).equals(normalized)) return key;
        }
        return raw;
    }

    private record ContractData(
            int version,
            Set<String> nodeTypes,
            Set<String> styleKeys,
            Set<String> normalizedStyleKeys,
            Map<String, String> promotedStyles,
            Map<String, String> eventAliases,
            Set<String> reservedNodeKeys,
            Map<String, Set<String>> styleValues,
            String rawJson
    ) {
    }
}
