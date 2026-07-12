/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeSpec;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeType;
import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class UiScriptObjectConverter {
    private static final List<String> RESERVED_NODE_KEYS = List.of(
            "type",
            "key",
            "class",
            "className",
            "props",
            "events",
            "meta",
            "children"
    );

    public UiNodeSpec convert(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("UI script render must return an object.");
        }
        return node(map);
    }

    private UiNodeSpec node(Map<?, ?> map) {
        Map<String, Object> props = mapObject(map.get("props"));
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) continue;
            String key = String.valueOf(entry.getKey());
            if (!RESERVED_NODE_KEYS.contains(key)) {
                props.put(key, entry.getValue());
            }
        }

        return new UiNodeSpec(
                string(map.get("key"), ""),
                nodeType(string(map.get("type"), "panel")),
                new UiProps(props),
                UiStyle.DEFAULT,
                string(map.get("class"), string(map.get("className"), "")),
                events(map.get("events")),
                mapObject(map.get("meta")),
                children(map.get("children"))
        );
    }

    private List<UiNodeSpec> children(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<UiNodeSpec> children = new ArrayList<>();
        for (Object child : iterable) {
            if (child instanceof Map<?, ?> childMap) {
                children.add(node(childMap));
            }
        }
        return children;
    }

    private static Map<String, String> events(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        Map<String, String> events = new LinkedHashMap<>(raw.size());
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null && entry.getValue() instanceof String action) {
                events.put(String.valueOf(entry.getKey()), action);
            }
        }
        return events;
    }

    private static Map<String, Object> mapObject(Object value) {
        if (!(value instanceof Map<?, ?> raw) || raw.isEmpty()) return new LinkedHashMap<>();
        Map<String, Object> map = new LinkedHashMap<>(raw.size());
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                map.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return map;
    }

    private static UiNodeType nodeType(String raw) {
        String normalized = raw == null ? "panel" : raw.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return UiNodeType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return UiNodeType.PANEL;
        }
    }

    private static String string(Object value, String fallback) {
        return value instanceof String s ? s : fallback;
    }
}
