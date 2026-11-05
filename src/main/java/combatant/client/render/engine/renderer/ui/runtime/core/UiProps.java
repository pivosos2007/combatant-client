/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.core;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import combatant.client.render.engine.renderer.ui.runtime.debug.UiRuntimeValidation;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    // Present only on render-thread-owned runtime copies. Immutable specs never allocate slots.
    private RuntimeSlots runtimeSlots;
    private boolean runtimeOwned;
    private Map<String, Object> runtimeView;

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
     * Live map-compatible props view. Iteration snapshots indexed values on demand;
     * point reads and writes remain live.
     */
    public Map<String, Object> values() {
        // Legacy map consumers must see primitive slots without mirroring/boxing
        // every typed write back into the backing map.
        if (!runtimeOwned && runtimeSlots == null) return values;
        if (runtimeView == null) runtimeView = new RuntimeMapView();
        return runtimeView;
    }

    /**
     * Returns a prop without type coercion.
     */
    public Object get(String key) {
        int slot = slotIndex(key);
        return slot >= 0 && runtimeSlots.present[slot] ? runtimeSlots.boxed(slot) : values.get(key);
    }

    /** Preserves null-valued props and slots that have not been written yet. */
    public boolean containsKey(String key) {
        int slot = slotIndex(key);
        return slot >= 0 ? runtimeSlots.present[slot] : values.containsKey(key);
    }

    private int slotIndex(String key) {
        if (runtimeSlots == null || key == null) return -1;
        Integer index = runtimeSlots.indices.get(key);
        return index != null ? index : -1;
    }

    /**
     * Returns a string prop or fallback.
     */
    public String string(String key, String fallback) {
        Object value = get(key);
        if (value instanceof String s) return s;
        validateType(key, value, "String");
        return fallback;
    }

    /**
     * Returns a boolean prop or fallback.
     */
    public boolean bool(String key, boolean fallback) {
        int slot = slotIndex(key);
        if (slot >= 0 && runtimeSlots.present[slot] && runtimeSlots.kinds[slot] == RuntimeSlots.BOOLEAN) {
            return runtimeSlots.booleans[slot];
        }
        Object value = get(key);
        if (value instanceof Boolean b) return b;
        validateType(key, value, "Boolean");
        return fallback;
    }

    /**
     * Returns a numeric prop. Number is read directly; String is parsed as float.
     */
    public float number(String key, float fallback) {
        int slot = slotIndex(key);
        if (slot >= 0 && runtimeSlots.present[slot]) {
            if (runtimeSlots.kinds[slot] == RuntimeSlots.FLOAT) return runtimeSlots.floats[slot];
            if (runtimeSlots.kinds[slot] == RuntimeSlots.INT) return runtimeSlots.ints[slot];
        }
        Object value = get(key);
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
        if (!UiRuntimeValidation.enabled() || value == null || !containsKey(key)) return;
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
        data.putAll(values());
        UiProps live = new UiProps(data, true);
        live.runtimeOwned = true;
        return live;
    }

    /**
     * Bind a property to a stable index for the current live node. Called only
     * on tree binding/rebinding, never from the per-frame apply loop.
     */
    int bindRuntimeSlot(String key) {
        if (key == null) throw new IllegalArgumentException("Null UI property");
        if (runtimeSlots == null) runtimeSlots = new RuntimeSlots();
        Integer existing = runtimeSlots.indices.get(key);
        if (existing != null) return existing;
        int index = runtimeSlots.add(key);
        // Move its initial value out of the legacy map exactly once. The
        // compiled value and the fallback map can never diverge.
        if (values.containsKey(key)) {
            runtimeSlots.present[index] = true;
            runtimeSlots.objects[index] = values.remove(key);
        }
        return index;
    }

    boolean slotAffectsDisabled(int index) {
        String key = runtimeSlots.keys[index];
        return "disabled".equals(key) || "stateValues".equals(key);
    }

    boolean updateRuntime(Map<String, ?> patch) {
        if (patch == null || patch.isEmpty()) return false;
        boolean changed = false;
        for (Map.Entry<String, ?> entry : patch.entrySet()) {
            if (entry.getKey() != null) changed |= updateRuntime(entry.getKey(), entry.getValue());
        }
        return changed;
    }

    /** Legacy named path stays coherent with any compiled slots. */
    boolean updateRuntime(String key, Object value) {
        int index = slotIndex(key);
        if (index >= 0) return updateRuntime(index, value);
        if (Objects.equals(values.get(key), value) && values.containsKey(key)) return false;
        values.put(key, value);
        return true;
    }

    boolean updateRuntimeFloat(String key, float value) {
        int index = slotIndex(key);
        if (index >= 0) return updateRuntimeFloat(index, value);
        Object old = values.get(key);
        if (old instanceof Float f && Float.floatToIntBits(f) == Float.floatToIntBits(value)) return false;
        values.put(key, value);
        return true;
    }

    boolean updateRuntimeInt(String key, int value) {
        int index = slotIndex(key);
        if (index >= 0) return updateRuntimeInt(index, value);
        Object old = values.get(key);
        if (old instanceof Integer i && i == value) return false;
        values.put(key, value);
        return true;
    }

    boolean updateRuntimeBoolean(String key, boolean value) {
        int index = slotIndex(key);
        if (index >= 0) return updateRuntimeBoolean(index, value);
        Object old = values.get(key);
        if (old instanceof Boolean b && b == value) return false;
        values.put(key, value);
        return true;
    }

    /** Direct indexed object update; no key lookup or map write. */
    boolean updateRuntime(int index, Object value) {
        RuntimeSlots slots = runtimeSlots;
        if (slots.present[index] && Objects.equals(slots.boxed(index), value)) return false;
        slots.present[index] = true;
        slots.kinds[index] = RuntimeSlots.OBJECT;
        slots.objects[index] = value;
        return true;
    }

    boolean updateRuntimeFloat(int index, float value) {
        RuntimeSlots slots = runtimeSlots;
        if (slots.present[index]) {
            if (slots.kinds[index] == RuntimeSlots.FLOAT &&
                    Float.floatToIntBits(slots.floats[index]) == Float.floatToIntBits(value)) return false;
            if (slots.kinds[index] == RuntimeSlots.OBJECT && slots.objects[index] instanceof Float f &&
                    Float.floatToIntBits(f) == Float.floatToIntBits(value)) return false;
        }
        slots.present[index] = true;
        slots.kinds[index] = RuntimeSlots.FLOAT;
        slots.floats[index] = value;
        slots.objects[index] = null;
        return true;
    }

    boolean updateRuntimeInt(int index, int value) {
        RuntimeSlots slots = runtimeSlots;
        if (slots.present[index]) {
            if (slots.kinds[index] == RuntimeSlots.INT && slots.ints[index] == value) return false;
            if (slots.kinds[index] == RuntimeSlots.OBJECT && slots.objects[index] instanceof Integer i && i == value)
                return false;
        }
        slots.present[index] = true;
        slots.kinds[index] = RuntimeSlots.INT;
        slots.ints[index] = value;
        slots.objects[index] = null;
        return true;
    }

    boolean updateRuntimeBoolean(int index, boolean value) {
        RuntimeSlots slots = runtimeSlots;
        if (slots.present[index]) {
            if (slots.kinds[index] == RuntimeSlots.BOOLEAN && slots.booleans[index] == value) return false;
            if (slots.kinds[index] == RuntimeSlots.OBJECT && slots.objects[index] instanceof Boolean b && b == value)
                return false;
        }
        slots.present[index] = true;
        slots.kinds[index] = RuntimeSlots.BOOLEAN;
        slots.booleans[index] = value;
        slots.objects[index] = null;
        return true;
    }

    boolean differsFrom(Map<String, ?> patch) {
        if (patch == null || patch.isEmpty()) return false;
        for (Map.Entry<String, ?> entry : patch.entrySet()) {
            String key = entry.getKey();
            if (key != null && (!Objects.equals(get(key), entry.getValue()) || !containsKey(key))) return true;
        }
        return false;
    }

    /** Backwards-compatible view, materializing entries only for map iteration. */
    private final class RuntimeMapView extends AbstractMap<String, Object> {
        @Override public Object get(Object key) {
            return key instanceof String s ? UiProps.this.get(s) : values.get(key);
        }
        @Override public boolean containsKey(Object key) {
            return key instanceof String s ? UiProps.this.containsKey(s) : values.containsKey(key);
        }
        @Override public int size() {
            int count = values.size();
            for (int i = 0; i < runtimeSlots.size; i++) if (runtimeSlots.present[i]) count++;
            return count;
        }
        @Override public Object put(String key, Object value) {
            Object old = get(key);
            updateRuntime(key, value);
            return old;
        }
        @Override public Object remove(Object key) {
            if (!(key instanceof String s)) return values.remove(key);
            int index = slotIndex(s);
            if (index < 0) return values.remove(s);
            if (!runtimeSlots.present[index]) return null;
            Object old = runtimeSlots.boxed(index);
            runtimeSlots.present[index] = false;
            runtimeSlots.kinds[index] = RuntimeSlots.OBJECT;
            runtimeSlots.objects[index] = null;
            return old;
        }
        @Override public void clear() {
            values.clear();
            for (int i = 0; i < runtimeSlots.size; i++) {
                runtimeSlots.present[i] = false;
                runtimeSlots.kinds[i] = RuntimeSlots.OBJECT;
                runtimeSlots.objects[i] = null;
            }
        }
        @Override public Set<Map.Entry<String, Object>> entrySet() {
            LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>(values);
            for (int i = 0; i < runtimeSlots.size; i++) {
                if (runtimeSlots.present[i]) snapshot.put(runtimeSlots.keys[i], runtimeSlots.boxed(i));
            }
            return Collections.unmodifiableMap(snapshot).entrySet();
        }
    }

    private static final class RuntimeSlots {
        static final byte OBJECT = 0, FLOAT = 1, INT = 2, BOOLEAN = 3;
        final Map<String, Integer> indices = new HashMap<>(4);
        String[] keys = new String[4];
        Object[] objects = new Object[4];
        float[] floats = new float[4];
        int[] ints = new int[4];
        boolean[] booleans = new boolean[4];
        boolean[] present = new boolean[4];
        byte[] kinds = new byte[4];
        int size;

        int add(String key) {
            if (size == keys.length) {
                int newCapacity = keys.length * 2;
                keys = Arrays.copyOf(keys, newCapacity);
                objects = Arrays.copyOf(objects, newCapacity);
                floats = Arrays.copyOf(floats, newCapacity);
                ints = Arrays.copyOf(ints, newCapacity);
                booleans = Arrays.copyOf(booleans, newCapacity);
                present = Arrays.copyOf(present, newCapacity);
                kinds = Arrays.copyOf(kinds, newCapacity);
            }
            int index = size++;
            keys[index] = key;
            indices.put(key, index);
            return index;
        }

        Object boxed(int index) {
            return switch (kinds[index]) {
                case FLOAT -> floats[index];
                case INT -> ints[index];
                case BOOLEAN -> booleans[index];
                default -> objects[index];
            };
        }
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
            if (!Objects.equals(get(key), entry.getValue()) || !containsKey(key)) {
                changed = true;
            }
        }
        if (!changed) return this;

        Map<String, Object> source = values();
        Object2ObjectOpenHashMap<String, Object> merged = new Object2ObjectOpenHashMap<>(source.size() + validPatchEntries);
        merged.putAll(source);
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
