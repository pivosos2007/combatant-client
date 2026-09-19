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
import combatant.client.render.engine.renderer.ui.runtime.debug.UiRuntimeValidation;
import combatant.client.render.engine.renderer.ui.runtime.style.UiInlineStyle;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class UiScriptObjectConverter {
    private static final List<String> RESERVED_NODE_KEYS = List.of(
            "type", "key", "class", "className", "style", "props", "events", "meta", "children",
            "width", "height", "minWidth", "minHeight", "maxWidth", "maxHeight", "grow", "flexGrow", "shrink", "flexShrink", "display", "flexDirection",
            "padding", "paddingX", "paddingHorizontal", "paddingY", "paddingVertical", "paddingLeft", "paddingTop", "paddingRight", "paddingBottom",
            "margin", "marginX", "marginHorizontal", "marginY", "marginVertical", "marginLeft", "marginTop", "marginRight", "marginBottom", "gap",
            "position", "absolute", "x", "y", "left", "top", "right", "bottom", "align", "alignItems", "justify", "justifyContent", "overflow",
            "fontSize", "lineHeight", "fontFamily", "fontWeight", "fontStyle",
            "textAlign", "maxTextWidth", "ellipsis", "whiteSpace", "overflowWrap", "maxLines", "textOverflow", "marquee",
            "onClick", "onChange", "onInput", "onScroll"
    );

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
        Map<String, Object> props = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) continue;
            String key = String.valueOf(entry.getKey());
            if (!RESERVED_NODE_KEYS.contains(key)) {
                props.put(key, entry.getValue());
            }
        }
        // Explicit props win over shorthand top-level props, matching ui.js normalization.
        props.putAll(mapObject(map.get("props")));

        return new UiNodeSpec(
                string(map.get("key"), ""),
                type,
                new UiProps(props),
                UiStyle.DEFAULT,
                styleClass(map),
                new UiInlineStyle(inlineStyle(map, type)),
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
            int index = 0;
            for (Object child : iterable) {
                appendChildren(child, out, path + "[" + index + "]");
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
        directEvent(node, events, "onClick", "click");
        directEvent(node, events, "onChange", "change");
        directEvent(node, events, "onInput", "input");
        directEvent(node, events, "onScroll", "scroll");
        return events.isEmpty() ? Map.of() : events;
    }

    private static Map<String, String> eventMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return new LinkedHashMap<>();
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
        return events;
    }

    private static void directEvent(Map<?, ?> node, Map<String, String> events, String field, String eventName) {
        if (!node.containsKey(field)) return;
        Object value = node.get(field);
        if (value == null) return;
        if (value instanceof String action) {
            events.put(eventName, action);
            return;
        }
        if (UiRuntimeValidation.enabled()) {
            throw UiRuntimeValidation.invalid(
                    "UI node field '" + field + "' must name a String action, got "
                            + value.getClass().getSimpleName() + "."
            );
        }
    }

    private static String styleClass(Map<?, ?> node) {
        String className = string(node.get("className"), "").trim();
        String classValue = string(node.get("class"), "").trim();
        if (className.isEmpty()) return classValue;
        if (classValue.isEmpty()) return className;
        return className + " " + classValue;
    }

    private static Map<String, Object> inlineStyle(Map<?, ?> node, UiNodeType type) {
        Map<String, Object> style = new LinkedHashMap<>();
        promote(node, style, "width", "width");
        promote(node, style, "height", "height");
        promote(node, style, "minWidth", "minWidth");
        promote(node, style, "minHeight", "minHeight");
        promote(node, style, "maxWidth", "maxWidth");
        promote(node, style, "maxHeight", "maxHeight");
        promote(node, style, "grow", "grow");
        promote(node, style, "flexGrow", "flexGrow");
        promote(node, style, "shrink", "shrink");
        promote(node, style, "flexShrink", "flexShrink");
        promote(node, style, "display", "display");
        promote(node, style, "flexDirection", "flexDirection");
        promote(node, style, "padding", "padding");
        promote(node, style, "paddingX", "paddingX");
        promote(node, style, "paddingHorizontal", "paddingHorizontal");
        promote(node, style, "paddingY", "paddingY");
        promote(node, style, "paddingVertical", "paddingVertical");
        promote(node, style, "paddingLeft", "paddingLeft");
        promote(node, style, "paddingTop", "paddingTop");
        promote(node, style, "paddingRight", "paddingRight");
        promote(node, style, "paddingBottom", "paddingBottom");
        promote(node, style, "margin", "margin");
        promote(node, style, "marginX", "marginX");
        promote(node, style, "marginHorizontal", "marginHorizontal");
        promote(node, style, "marginY", "marginY");
        promote(node, style, "marginVertical", "marginVertical");
        promote(node, style, "marginLeft", "marginLeft");
        promote(node, style, "marginTop", "marginTop");
        promote(node, style, "marginRight", "marginRight");
        promote(node, style, "marginBottom", "marginBottom");
        promote(node, style, "gap", "gap");
        promote(node, style, "position", "position");
        promote(node, style, "absolute", "absolute");
        promote(node, style, "x", "x");
        promote(node, style, "y", "y");
        promote(node, style, "left", "left");
        promote(node, style, "top", "top");
        promote(node, style, "right", "right");
        promote(node, style, "bottom", "bottom");
        promote(node, style, "justify", "justify");
        promote(node, style, "justifyContent", "justifyContent");
        promote(node, style, "overflow", "overflow");
        promote(node, style, "fontSize", "fontSize");
        promote(node, style, "lineHeight", "lineHeight");
        promote(node, style, "fontFamily", "fontFamily");
        promote(node, style, "fontWeight", "fontWeight");
        promote(node, style, "fontStyle", "fontStyle");
        promote(node, style, "textAlign", "textAlign");
        promote(node, style, "maxTextWidth", "maxTextWidth");
        promote(node, style, "ellipsis", "ellipsis");
        promote(node, style, "whiteSpace", "whiteSpace");
        promote(node, style, "overflowWrap", "overflowWrap");
        promote(node, style, "maxLines", "maxLines");
        promote(node, style, "textOverflow", "textOverflow");
        promote(node, style, "marquee", "marquee");

        if (node.containsKey("align")) {
            Object align = node.get("align");
            if (type == UiNodeType.TEXT && align instanceof String text
                    && ("left".equalsIgnoreCase(text) || "right".equalsIgnoreCase(text)
                    || "center".equalsIgnoreCase(text) || "end".equalsIgnoreCase(text))) {
                style.put("textAlign", align);
            } else {
                style.put("align", align);
            }
        }
        promote(node, style, "alignItems", "alignItems");

        // Explicit style is last, matching browser/CSS precedence over convenience aliases.
        style.putAll(mapObject(node.get("style")));
        return style;
    }

    private static void promote(Map<?, ?> source, Map<String, Object> target, String sourceKey, String styleKey) {
        if (source.containsKey(sourceKey)) target.put(styleKey, source.get(sourceKey));
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
            if (UiRuntimeValidation.enabled()) {
                throw UiRuntimeValidation.invalid("Unknown UI node type '" + raw + "'.");
            }
            return UiNodeType.PANEL;
        }
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
