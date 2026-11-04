/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.core;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import combatant.client.render.engine.renderer.ui.runtime.debug.UiRuntimeValidation;

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
    private final boolean detachedSnapshot;

    private UiProps(Map<String, Object> values, boolean trusted) {
        this(values, trusted, false);
    }

    private UiProps(Map<String, Object> values, boolean trusted, boolean detachedSnapshot) {
        // An empty map can still be a preallocated, privately owned runtime map.
        // Collapsing it to Map.of() breaks the very first patch on nodes whose
        // declarative spec has no props (e.g. a keyed HUD stat placeholder).
        this.values = values != null ? values : Map.of();
        this.detachedSnapshot = detachedSnapshot;
    }

    /**
     * Transfer an already deeply detached, immutable JS snapshot without copying it again.
     * This must only be called with a graph produced by UiScriptSnapshot.capture().
     */
    public static UiProps fromDetachedSnapshot(Map<String, Object> snapshot) {
        return new UiProps(snapshot, true, true);
    }

    public boolean isDetachedSnapshot() {
        return detachedSnapshot;
    }

    /**
     * Creates a props copy. Null or empty input becomes a shared empty map.
     */
    public UiProps(Map<String, ?> values) {
        this.detachedSnapshot = false;
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
        if (value instanceof String s) return s;
        validateType(key, value, "String");
        return fallback;
    }

    /**
     * Returns a boolean prop or fallback.
     */
    public boolean bool(String key, boolean fallback) {
        Object value = values.get(key);
        if (value instanceof Boolean b) return b;
        validateType(key, value, "Boolean");
        return fallback;
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
                if (UiRuntimeValidation.enabled()) {
                    throw UiRuntimeValidation.invalid(
                            "UI prop '" + key + "' must be numeric; value '" + s + "' cannot be parsed."
                    );
                }
            }
            return fallback;
        }
        validateType(key, value, "Number or numeric String");
        return fallback;
    }

    private void validateType(String key, Object value, String expected) {
        if (!UiRuntimeValidation.enabled() || value == null || !values.containsKey(key)) return;
        throw UiRuntimeValidation.invalid(
                "UI prop '" + key + "' must be " + expected + ", got " + value.getClass().getSimpleName() + "."
        );
    }

    /**
     * Create a runtime-owned, mutable copy once, reserving space for dynamic slots.
     * Declarative specs keep their normal immutable-copy semantics; only the live
     * render-thread node uses this backing map.
     */
    UiProps runtimeCopy(int initialPatchEntries) {
        Object2ObjectOpenHashMap<String, Object> data = new Object2ObjectOpenHashMap<>(
                values.size() + Math.max(8, initialPatchEntries));
        data.putAll(values);
        return new UiProps(data, true);
    }

    /**
     * Update preallocated runtime props without constructing a new UiProps/map per frame.
     * Must only be called on the privately owned result of runtimeCopy().
     */
    boolean updateRuntime(Map<String, ?> patch) {
        if (patch == null || patch.isEmpty()) return false;
        boolean changed = false;
        for (Map.Entry<String, ?> entry : patch.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;
            Object value = entry.getValue();
            if (!Objects.equals(values.get(key), value) || !values.containsKey(key)) {
                values.put(key, value);
                changed = true;
            }
        }
        return changed;
    }

    /** Single-slot update for a compiled UI patch instruction. */
    boolean updateRuntime(String key, Object value) {
        if (Objects.equals(values.get(key), value) && values.containsKey(key)) return false;
        values.put(key, value);
        return true;
    }

    /** Primitive fast path: no Float allocation until the value actually changes. */
    boolean updateRuntimeFloat(String key, float value) {
        Object old = values.get(key);
        if (old instanceof Float f && Float.floatToIntBits(f) == Float.floatToIntBits(value)) return false;
        values.put(key, value);
        return true;
    }

    /** Primitive integer patch without allocation when the value is unchanged. */
    boolean updateRuntimeInt(String key, int value) {
        Object old = values.get(key);
        if (old instanceof Integer i && i == value) return false;
        values.put(key, value);
        return true;
    }

    /** Boolean uses JDK cached boxes and preserves the single-prop fast path. */
    boolean updateRuntimeBoolean(String key, boolean value) {
        Object old = values.get(key);
        if (old instanceof Boolean b && b == value) return false;
        values.put(key, value);
        return true;
    }

    boolean differsFrom(Map<String, ?> patch) {
        if (patch == null || patch.isEmpty()) return false;
        for (Map.Entry<String, ?> entry : patch.entrySet()) {
            String key = entry.getKey();
            if (key != null && (!Objects.equals(values.get(key), entry.getValue())
                    || !values.containsKey(key))) return true;
        }
        return false;
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
