/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.values;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ordered list of block/item ids where every entry may carry its own colour.
 * <p>
 * Entries without an explicit colour inherit the module's global colour ({@code fallback}), so the
 * global colour setting keeps working as the default and later changes to it still apply to them.
 * <p>
 * It is a regular {@link ItemIdSetValue}: consumers that only care about the ids keep reading
 * {@link #get()}. JSON shape is a list whose elements are either a plain id string (inherits the
 * default colour; this is also the legacy format of the plain id lists) or
 * {@code {"id": "minecraft:tnt", "color": "#FF8800"}}. An object keyed by id is accepted on load too.
 */
public final class ColorListValue extends ItemIdSetValue {

    private final ColorValue fallback;
    private final Map<String, ColorValue> overrides = new LinkedHashMap<>();

    public ColorListValue(String name, Set<String> defaults, ColorValue fallback) {
        super(name, defaults);
        if (fallback == null) throw new IllegalArgumentException("fallback colour is required");
        this.fallback = fallback;
    }

    /** The module-level colour used by entries without an explicit colour. */
    public ColorValue fallback() {
        return fallback;
    }

    public boolean supportsAlpha() {
        return fallback.supportsAlpha();
    }

    public boolean hasOverride(String id) {
        String key = normalizeStatic(id);
        return key != null && overrides.containsKey(key);
    }

    /** Effective ARGB colour for the id: its own colour, or the default colour when it has none. */
    public int argbFor(String id) {
        ColorValue own = overrides.get(id);
        if (own == null) {
            String key = normalizeStatic(id);
            if (key != null) own = overrides.get(key);
        }
        return (own != null ? own : fallback).getArgb();
    }

    /** Hex string of the colour the id is drawn with: its own colour, or the default colour when it has none. */
    public String hexFor(String id) {
        String key = normalizeStatic(id);
        ColorValue own = key == null ? null : overrides.get(key);
        return (own != null ? own : fallback).get();
    }

    public void setOverride(String id, String hex) {
        String key = normalizeStatic(id);
        if (key == null || hex == null || !value.contains(key)) return;
        ColorValue own = overrides.get(key);
        if (own == null) {
            overrides.put(key, newColor(hex));
        } else {
            own.set(hex);
        }
    }

    public void clearOverride(String id) {
        String key = normalizeStatic(id);
        if (key != null) overrides.remove(key);
    }

    /** Drops every explicit colour so all entries follow the default again. */
    public void clearOverrides() {
        overrides.clear();
    }

    private ColorValue newColor(String hex) {
        return fallback.supportsAlpha()
                ? new RGBAColorValue(getName() + "_entry", hex)
                : new RGBColorValue(getName() + "_entry", hex);
    }

    @Override
    public void set(Set<String> v) {
        super.set(v);
        overrides.keySet().retainAll(value);
    }

    @Override
    public Object toJson() {
        List<Object> out = new ArrayList<>(value.size());
        for (String id : value) {
            ColorValue own = overrides.get(id);
            if (own == null) {
                out.add(id);
            } else {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", id);
                entry.put("color", own.get());
                out.add(entry);
            }
        }
        return out;
    }

    @Override
    public void fromJson(Object json) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        Map<String, String> colors = new LinkedHashMap<>();
        if (json instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String s) {
                    String id = normalizeStatic(s);
                    if (id != null) ids.add(id);
                } else if (o instanceof Map<?, ?> m && m.get("id") instanceof String s) {
                    String id = normalizeStatic(s);
                    if (id == null) continue;
                    ids.add(id);
                    if (m.get("color") instanceof String c && !c.isBlank()) colors.put(id, c);
                }
            }
        } else if (json instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!(e.getKey() instanceof String s)) continue;
                String id = normalizeStatic(s);
                if (id == null) continue;
                ids.add(id);
                if (e.getValue() instanceof String c && !c.isBlank()) colors.put(id, c);
            }
        } else {
            return;
        }
        value = ids;
        overrides.clear();
        for (Map.Entry<String, String> e : colors.entrySet()) {
            overrides.put(e.getKey(), newColor(e.getValue()));
        }
    }
}
