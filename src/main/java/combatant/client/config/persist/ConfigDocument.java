/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.persist;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.marhali.json5.Json5;
import de.marhali.json5.Json5Element;
import de.marhali.json5.Json5Object;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Serialization side of the persistence layer: a snapshot of what should be on disk, plus how to
 * validate stored bytes and how to merge in whatever the file already holds that this build does not know.
 */
public interface ConfigDocument {
    /**
     * @param existing current on-disk bytes (null when absent or unreadable)
     * @return final bytes to write
     */
    byte[] render(byte[] existing);

    /** @return true when {@code bytes} are a complete document of this kind. */
    boolean isValid(byte[] bytes);

    /** Document that is written verbatim and accepts any stored bytes. */
    static ConfigDocument raw(byte[] bytes) {
        return new ConfigDocument() {
            @Override
            public byte[] render(byte[] existing) {
                return bytes;
            }

            @Override
            public boolean isValid(byte[] stored) {
                return true;
            }
        };
    }

    /**
     * Strict-JSON object document. Top-level keys present on disk but absent from {@code generated}
     * (unknown fields, addon keys) are written back unchanged, except {@code droppedKeys} (retired legacy names).
     */
    static ConfigDocument json(Gson gson, String generated, Set<String> droppedKeys) {
        return new ConfigDocument() {
            @Override
            public byte[] render(byte[] existing) {
                String text = generated;
                JsonObject old = parseObject(existing);
                if (old != null) {
                    JsonObject merged = parseObject(generated.getBytes(StandardCharsets.UTF_8));
                    if (merged != null) {
                        boolean added = false;
                        for (Map.Entry<String, JsonElement> e : old.entrySet()) {
                            if (merged.has(e.getKey()) || droppedKeys.contains(e.getKey())) continue;
                            merged.add(e.getKey(), e.getValue());
                            added = true;
                        }
                        if (added) text = gson.toJson(merged) + "\n";
                    }
                }
                return text.getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public boolean isValid(byte[] bytes) {
                return parseObject(bytes) != null;
            }
        };
    }

    /** Json5 object document with the same unknown-field preservation as {@link #json}. */
    static ConfigDocument json5(Json5 json5, String generated, Set<String> droppedKeys) {
        return new ConfigDocument() {
            @Override
            public byte[] render(byte[] existing) {
                String text = generated;
                Json5Object old = parseJson5(json5, existing);
                if (old != null) {
                    Json5Object merged = parseJson5(json5, generated.getBytes(StandardCharsets.UTF_8));
                    if (merged != null) {
                        boolean added = false;
                        for (Map.Entry<String, Json5Element> e : old.asMap().entrySet()) {
                            if (merged.has(e.getKey()) || droppedKeys.contains(e.getKey())) continue;
                            merged.add(e.getKey(), e.getValue());
                            added = true;
                        }
                        if (added) {
                            try {
                                text = json5.serialize(merged) + "\n";
                            } catch (java.io.IOException e) {
                                throw new java.io.UncheckedIOException(e);
                            }
                        }
                    }
                }
                return text.getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public boolean isValid(byte[] bytes) {
                return parseJson5(json5, bytes) != null;
            }
        };
    }

    /** Parses strict JSON; returns null for anything that is not a complete top-level object. */
    static JsonObject parseObject(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try {
            JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Json5Object parseJson5(Json5 json5, byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try {
            Json5Element parsed = json5.parse(new String(bytes, StandardCharsets.UTF_8));
            return parsed instanceof Json5Object obj ? obj : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
