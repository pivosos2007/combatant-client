/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import combatant.client.render.engine.renderer.ui.runtime.core.UiAuthoringContract;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeSpec;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeType;
import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;
import combatant.client.render.engine.renderer.ui.runtime.debug.UiRuntimeValidation;
import combatant.client.render.engine.renderer.ui.runtime.style.UiInlineStyle;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class UiScriptObjectConverter {
    private static final Set<String> RESERVED_NODE_KEYS = UiAuthoringContract.reservedNodeKeys();

    public UiNodeSpec convert(Object value) {
        if (value instanceof Map<?, ?> map) {
            return node(map);
        }
        if (value instanceof Iterable<?> iterable) {
            List<UiNodeSpec> children = new ArrayList<>();
            appendChildren(iterable, children, "root");
            return new UiNodeSpec(
                    "root",
                    UiNodeType.ROOT,
                    UiProps.EMPTY,
                    UiStyle.DEFAULT,
                    "",
                    UiInlineStyle.EMPTY,
                    Map.of(),
                    Map.of(),
                    children
            );
        }
        throw new IllegalArgumentException("UI script render must return a node object or fragment/iterable of nodes.");
    }

    private UiNodeSpec node(Map<?, ?> map) {
        validateFieldShape(map, "type", String.class);
        validateFieldShape(map, "key", String.class);
        validateFieldShape(map, "class", String.class);
        validateFieldShape(map, "className", String.class);
        validateMapField(map, "style");
        validateMapField(map, "props");
        validateMapField(map, "events");
        validateMapField(map, "meta");
        validateChildrenField(map);

        UiNodeType type = nodeType(string(map.get("type"), "panel"));
        // Most fields on a script node are reserved. Allocate a props map only when the node
        // actually contains an authored property; UiProps.EMPTY is shared by empty specs.
        Map<String, Object> props = null;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) continue;
            String key = String.valueOf(entry.getKey());
            if (!RESERVED_NODE_KEYS.contains(key)) {
                if (props == null) props = new LinkedHashMap<>();
                props.put(key, entry.getValue());
            }
        }
        Map<String, Object> explicitProps = mapObject(map.get("props"));
        if (!explicitProps.isEmpty()) {
            if (props == null) props = new LinkedHashMap<>(explicitProps.size());
            // Explicit props override shorthand fields, including explicit null values.
            props.putAll(explicitProps);
        }
        Map<String, Object> style = inlineStyle(map, type);

        return new UiNodeSpec(
                string(map.get("key"), ""),
                type,
                props == null ? UiProps.EMPTY : new UiProps(props),
                UiStyle.DEFAULT,
                styleClass(map),
                style.isEmpty() ? UiInlineStyle.EMPTY : new UiInlineStyle(style),
                events(map),
                mapObject(map.get("meta")),
                children(map.get("children"))
        );
    }

    private List<UiNodeSpec> children(Object value) {
        if (value == null) return List.of();
        List<UiNodeSpec> children = new ArrayList<>();
        appendChildren(value, children, "children");
        return children.isEmpty() ? List.of() : children;
    }

    private void appendChildren(Object value, List<UiNodeSpec> out, String path) {
        if (value == null || value instanceof Boolean) return;
        if (value instanceof Map<?, ?> childMap) {
            out.add(node(childMap));
            return;
        }
        if (value instanceof String text) {
            out.add(UiNodeSpec.text("", text, UiStyle.DEFAULT));
            return;
        }
        if (value instanceof Number number) {
            out.add(UiNodeSpec.text("", String.valueOf(number), UiStyle.DEFAULT));
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            // Child index paths are only consumed by strict validation diagnostics.
            // Avoid allocating one path string per child in normal UI tree conversion.
            boolean validate = UiRuntimeValidation.enabled();
            int index = 0;
            for (Object child : iterable) {
                appendChildren(child, out, validate ? path + "[" + index + "]" : path);
                index++;
            }
            return;
        }
        if (UiRuntimeValidation.enabled()) {
            throw UiRuntimeValidation.invalid(
                    "UI " + path + " must be a node, iterable, false, or null; got "
                            + value.getClass().getSimpleName() + "."
            );
        }
    }

    private static Map<String, String> events(Map<?, ?> node) {
        Map<String, String> events = eventMap(node.get("events"));
        for (Map.Entry<String, String> alias : UiAuthoringContract.eventAliases().entrySet()) {
            events = directEvent(node, events, alias.getKey(), alias.getValue());
        }
        return events.isEmpty() ? Map.of() : events;
    }

    private static Map<String, String> eventMap(Object value) {
        if (!(value instanceof Map<?, ?> raw) || raw.isEmpty()) return Map.of();
        Map<String, String> events = new LinkedHashMap<>(raw.size());
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() == null) continue;
            if (entry.getValue() instanceof String action) {
                events.put(String.valueOf(entry.getKey()), action);
            } else if (entry.getValue() != null && UiRuntimeValidation.enabled()) {
                throw UiRuntimeValidation.invalid(
                        "UI event '" + entry.getKey() + "' must name a String action, got "
                                + entry.getValue().getClass().getSimpleName() + "."
                );
            }
        }
        return events.isEmpty() ? Map.of() : events;
    }

    private static Map<String, String> directEvent(Map<?, ?> node, Map<String, String> events,
                                                    String field, String eventName) {
        if (!node.containsKey(field)) return events;
        Object value = node.get(field);
        if (value == null) return events;
        if (value instanceof String action) {
            if (events.isEmpty()) events = new LinkedHashMap<>();
            events.put(eventName, action);
        } else if (UiRuntimeValidation.enabled()) {
            throw UiRuntimeValidation.invalid(
                    "UI node field '" + field + "' must name a String action, got "
                            + value.getClass().getSimpleName() + "."
            );
        }
        return events;
    }

    private static String styleClass(Map<?, ?> node) {
        String className = string(node.get("className"), "").trim();
        String classValue = string(node.get("class"), "").trim();
        if (className.isEmpty()) return classValue;
        if (classValue.isEmpty()) return className;
        return className + " " + classValue;
    }

    private static Map<String, Object> inlineStyle(Map<?, ?> node, UiNodeType type) {
        Map<String, Object> style = null;
        for (Map.Entry<String, String> promotion : UiAuthoringContract.promotedStyles().entrySet()) {
            String sourceKey = promotion.getKey();
            if (!node.containsKey(sourceKey)) continue;

            Object value = node.get(sourceKey);
            if (style == null) style = new LinkedHashMap<>();
            if ("align".equals(sourceKey)
                    && type == UiNodeType.TEXT
                    && value instanceof String text
                    && ("left".equalsIgnoreCase(text) || "right".equalsIgnoreCase(text)
                    || "center".equalsIgnoreCase(text) || "end".equalsIgnoreCase(text))) {
                style.put("textAlign", value);
                continue;
            }
            style.put(promotion.getValue(), value);
        }

        // Explicit style is last, matching browser/CSS precedence over convenience aliases.
        Map<String, Object> explicitStyle = mapObject(node.get("style"));
        if (!explicitStyle.isEmpty()) {
            if (style == null) return explicitStyle;
            style.putAll(explicitStyle);
        }
        return style != null ? style : Map.of();
    }

    private static Map<String, Object> mapObject(Object value) {
        if (!(value instanceof Map<?, ?> raw) || raw.isEmpty()) return Map.of();
        Map<String, Object> map = new LinkedHashMap<>(raw.size());
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                map.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return map;
    }

    private static UiNodeType nodeType(String raw) {
        String canonical = UiAuthoringContract.canonicalNodeType(raw);
        if (!UiAuthoringContract.isKnownNodeType(canonical)) {
            throw UiRuntimeValidation.invalid(
                    "Unknown UI node type '" + raw + "'. Known types: " + UiAuthoringContract.nodeTypes()
            );
        }
        return UiNodeType.valueOf(canonical.toUpperCase(Locale.ROOT));
    }

    private static void validateFieldShape(Map<?, ?> map, String field, Class<?> expectedType) {
        if (!UiRuntimeValidation.enabled() || !map.containsKey(field)) return;
        Object value = map.get(field);
        if (value == null || expectedType.isInstance(value)) return;
        throw UiRuntimeValidation.invalid(
                "UI node field '" + field + "' must be " + expectedType.getSimpleName() + ", got " + value.getClass().getSimpleName() + "."
        );
    }

    private static void validateMapField(Map<?, ?> map, String field) {
        if (!UiRuntimeValidation.enabled() || !map.containsKey(field)) return;
        Object value = map.get(field);
        if (value == null || value instanceof Map<?, ?>) return;
        throw UiRuntimeValidation.invalid(
                "UI node field '" + field + "' must be an object/map, got " + value.getClass().getSimpleName() + "."
        );
    }

    private static void validateChildrenField(Map<?, ?> map) {
        if (!UiRuntimeValidation.enabled() || !map.containsKey("children")) return;
        Object value = map.get("children");
        if (value == null || value instanceof Boolean || value instanceof Map<?, ?> || value instanceof Iterable<?>) return;
        throw UiRuntimeValidation.invalid(
                "UI node field 'children' must be a node, iterable, false, or null; got "
                        + value.getClass().getSimpleName() + "."
        );
    }

    private static String string(Object value, String fallback) {
        return value instanceof String s ? s : fallback;
    }
}
