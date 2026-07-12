/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.values;

import combatant.client.util.logging.DebugLog;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Хранит набор item-id в формате namespace:path (minecraft:xxx).
 */
public class ItemIdSetValue extends ConfigValue<Set<String>> {

    public ItemIdSetValue(String name) {
        super(name, new LinkedHashSet<>());
    }

    public ItemIdSetValue(String name, Set<String> def) {
        super(name, normalizeCopy(def));
    }

    private static LinkedHashSet<String> normalizeCopy(Set<String> source) {
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        if (source == null) return copy;

        for (String s : source) {
            String norm = normalizeStatic(s);
            if (norm != null) copy.add(norm);
        }
        return copy;
    }

    private static String normalizeStatic(String s) {
        if (s == null || s.isBlank()) return null;
        s = s.trim().toLowerCase();
        if (!s.contains(":")) s = "minecraft:" + s;
        return s;
    }

    @Override
    public void set(Set<String> v) {
        super.set(normalizeCopy(v));
        log("set()", value);
    }

    private String normalize(String s) {
        return normalizeStatic(s);
    }

    @Override
    public Object toJson() {
        log("toJson()", value);
        return new java.util.ArrayList<>(value);
    }

    @Override
    public void fromJson(Object json) {
        if (json instanceof List<?> list) {
            LinkedHashSet<String> copy = new LinkedHashSet<>();
            for (Object o : list) {
                if (o instanceof String s) {
                    String norm = normalize(s);
                    if (norm != null) copy.add(norm);
                }
            }
            value = copy;
            log("fromJson()", value);
        }
    }

    private void log(String stage, Set<String> data) {
        DebugLog.config("[ItemIdSetValue %s] %s -> %s", getName(), stage, data);
    }
}
