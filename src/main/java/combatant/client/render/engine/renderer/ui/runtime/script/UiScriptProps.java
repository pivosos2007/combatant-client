/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import java.util.LinkedHashMap;
import java.util.Map;

/** Props builder for JS UI modules. */
public final class UiScriptProps {
    private final Map<String, Object> values;

    private UiScriptProps(int expectedSize) {
        this.values = new LinkedHashMap<>(Math.max(1, expectedSize));
    }

    public static UiScriptProps create() {
        return new UiScriptProps(16);
    }

    public static UiScriptProps create(int expectedSize) {
        return new UiScriptProps(expectedSize);
    }

    public UiScriptProps put(String key, Object value) {
        if (key != null) {
            values.put(key, value);
        }
        return this;
    }

    public UiScriptProps put(String key, int value) {
        return put(key, Integer.valueOf(value));
    }

    public UiScriptProps put(String key, long value) {
        return put(key, Long.valueOf(value));
    }

    public UiScriptProps put(String key, float value) {
        return put(key, Float.valueOf(value));
    }

    public UiScriptProps put(String key, double value) {
        return put(key, Double.valueOf(value));
    }

    public UiScriptProps put(String key, boolean value) {
        return put(key, Boolean.valueOf(value));
    }

    public Object get(String key) {
        return values.get(key);
    }

    public String string(String key, String fallback) {
        Object value = values.get(key);
        return value instanceof String s ? s : fallback;
    }

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

    public boolean bool(String key, boolean fallback) {
        Object value = values.get(key);
        return value instanceof Boolean b ? b : fallback;
    }

    public Map<String, Object> asMap() {
        return values;
    }
}
