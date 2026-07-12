/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.core;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Map;
import java.util.Objects;

/**
 * Lightweight container for arbitrary node props.
 *
 * <p>Props are used by renderers, input handlers, and asset providers. Values
 * are copied into a fastutil map without extra wrapper objects.</p>
 */
public final class UiProps {
    public static final UiProps EMPTY = new UiProps(Map.of());

    private final Map<String, Object> values;

    private UiProps(Map<String, Object> values, boolean trusted) {
        this.values = values == null || values.isEmpty() ? Map.of() : values;
    }

    /**
     * Creates a props copy. Null or empty input becomes a shared empty map.
     */
    public UiProps(Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            this.values = Map.of();
        } else {
            Object2ObjectOpenHashMap<String, Object> copy = new Object2ObjectOpenHashMap<>(values.size());
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                copy.put(entry.getKey(), entry.getValue());
            }
            copy.trim();
            this.values = copy;
        }
    }

    /**
     * Helper for single-prop specs, for example a text node.
     */
    public static UiProps of(String key, Object value) {
        Object2ObjectOpenHashMap<String, Object> map = new Object2ObjectOpenHashMap<>(1);
        map.put(key, value);
        return new UiProps(map, true);
    }

    /**
     * Raw props map.
     */
    public Map<String, Object> values() {
        return values;
    }

    /**
     * Returns a prop without type coercion.
     */
    public Object get(String key) {
        return values.get(key);
    }

    /**
     * Returns a string prop or fallback.
     */
    public String string(String key, String fallback) {
        Object value = values.get(key);
        return value instanceof String s ? s : fallback;
    }

    /**
     * Returns a boolean prop or fallback.
     */
    public boolean bool(String key, boolean fallback) {
        Object value = values.get(key);
        return value instanceof Boolean b ? b : fallback;
    }

    /**
     * Returns a numeric prop. Number is read directly; String is parsed as float.
     */
    public float number(String key, float fallback) {
        Object value = values.get(key);
        if (value instanceof Number n) return n.floatValue();
        if (value instanceof String s) {
            try {
                return Float.parseFloat(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    /**
     * Returns a copy with patch values applied, or this instance when nothing changes.
     */
    public UiProps withPatch(Map<String, ?> patch) {
        if (patch == null || patch.isEmpty()) return this;

        boolean changed = false;
        int validPatchEntries = 0;
        for (Map.Entry<String, ?> entry : patch.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;
            validPatchEntries++;
            if (!Objects.equals(values.get(key), entry.getValue()) || !values.containsKey(key)) {
                changed = true;
            }
        }
        if (!changed) return this;

        Object2ObjectOpenHashMap<String, Object> merged = new Object2ObjectOpenHashMap<>(values.size() + validPatchEntries);
        merged.putAll(values);
        for (Map.Entry<String, ?> entry : patch.entrySet()) {
            String key = entry.getKey();
            if (key != null) {
                merged.put(key, entry.getValue());
            }
        }
        merged.trim();
        return new UiProps(merged, true);
    }
}
