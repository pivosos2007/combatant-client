/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.values;

import java.util.LinkedHashMap;
import java.util.Map;

public class BooleanMapValue extends ConfigValue<Map<String, Boolean>> {

    private final Map<String, Boolean> defaults = new LinkedHashMap<>();

    public BooleanMapValue(String name, Map<String, Boolean> defaults) {
        super(name, new LinkedHashMap<>());
        this.defaults.putAll(defaults);
        value.putAll(defaults);
    }

    public Map<String, Boolean> getAll() {
        return value;
    }

    public boolean get(String key) {
        return value.getOrDefault(key, false);
    }

    public void set(String key, boolean val) {
        value.put(key, val);
    }

    @Override
    public Object toJson() {
        return new LinkedHashMap<>(value);
    }

    @Override
    public void fromJson(Object json) {
        value.clear();
        // start with defaults so new keys always exist
        value.putAll(defaults);
        if (json instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (entry.getKey() instanceof String key &&
                        entry.getValue() instanceof Boolean b &&
                        defaults.containsKey(key)) {
                    value.put(key, b);
                }
            }
        }
    }

    @Override
    public String toDisplay() {
        return value.toString();
    }
}
