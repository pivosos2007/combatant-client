/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.core;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Immutable declarative description of a node.
 *
 * <p>A spec does not store runtime state. Hover, focus, scroll, animations,
 * bounds, and measured size live in {@link UiNode}. The reconciler can reuse a
 * runtime node when {@link #type()} and {@link #key()} match.</p>
 *
 * <p>Fields:</p>
 * <ul>
 *     <li>{@code key} - stable reconciliation key.</li>
 *     <li>{@code type} - node layout/render behavior type.</li>
 *     <li>{@code props} - node data such as text, asset, value, or item.</li>
 *     <li>{@code style} - resolved style or direct Java style.</li>
 *     <li>{@code styleClass} - utility token string.</li>
 *     <li>{@code events} - map event name -> action ref.</li>
 *     <li>{@code metadata} - extra data for debug/tools.</li>
 *     <li>{@code children} - child specs.</li>
 * </ul>
 */
public final class UiNodeSpec {
    private final String key;
    private final UiNodeType type;
    private final UiProps props;
    private final UiStyle style;
    private final String styleClass;
    private final Map<String, String> events;
    private final Map<String, Object> metadata;
    private final List<UiNodeSpec> children;

    /**
     * Constructor for a node spec.
     */
    public UiNodeSpec(String key,
                      UiNodeType type,
                      UiProps props,
                      UiStyle style,
                      String styleClass,
                      List<UiNodeSpec> children) {
        this(key, type, props, style, styleClass, Map.of(), Map.of(), children);
    }

    public UiNodeSpec(String key,
                      UiNodeType type,
                      UiProps props,
                      UiStyle style,
                      String styleClass,
                      Map<String, String> events,
                      Map<String, ?> metadata,
                      List<UiNodeSpec> children) {
        this.key = key != null ? key : "";
        this.type = type != null ? type : UiNodeType.PANEL;
        this.props = props != null ? props : UiProps.EMPTY;
        this.style = style != null ? style : UiStyle.DEFAULT;
        this.styleClass = styleClass != null ? styleClass : "";
        this.events = events == null || events.isEmpty()
                ? Map.of()
                : new Object2ObjectOpenHashMap<>(events);
        this.metadata = metadata == null || metadata.isEmpty()
                ? Map.of()
                : copyMetadata(metadata);
        this.children = children == null || children.isEmpty() ? List.of() : new ObjectArrayList<>(children);
    }

    public static UiNodeSpec node(String key, UiNodeType type, UiStyle style, UiNodeSpec... children) {
        return new UiNodeSpec(key, type, UiProps.EMPTY, style, "", Arrays.asList(children));
    }

    public static UiNodeSpec root(UiNodeSpec... children) {
        return node("root", UiNodeType.ROOT, UiStyle.DEFAULT, children);
    }

    public static UiNodeSpec panel(String key, UiStyle style, UiNodeSpec... children) {
        return node(key, UiNodeType.PANEL, style, children);
    }

    public static UiNodeSpec row(String key, UiStyle style, UiNodeSpec... children) {
        return node(key, UiNodeType.ROW, style, children);
    }

    public static UiNodeSpec column(String key, UiStyle style, UiNodeSpec... children) {
        return node(key, UiNodeType.COLUMN, style, children);
    }

    public static UiNodeSpec stack(String key, UiStyle style, UiNodeSpec... children) {
        return node(key, UiNodeType.STACK, style, children);
    }

    public static UiNodeSpec text(String key, String text, UiStyle style) {
        return new UiNodeSpec(key, UiNodeType.TEXT, UiProps.of("text", text), style, "", List.of());
    }

    public static UiNodeSpec spacer(String key, UiStyle style) {
        return node(key, UiNodeType.SPACER, style);
    }

    private static Object2ObjectOpenHashMap<String, Object> copyMetadata(Map<String, ?> metadata) {
        Object2ObjectOpenHashMap<String, Object> copy = new Object2ObjectOpenHashMap<>(metadata.size());
        for (Map.Entry<String, ?> entry : metadata.entrySet()) {
            copy.put(entry.getKey(), entry.getValue());
        }
        copy.trim();
        return copy;
    }

    /**
     * Stable key for reconciliation. Empty key falls back to list position.
     */
    public String key() {
        return key;
    }

    /**
     * Node type: row, column, text, image, scroll, and so on.
     */
    public UiNodeType type() {
        return type;
    }

    /**
     * Data props visible to renderers, input, and asset providers.
     */
    public UiProps props() {
        return props;
    }

    /**
     * Resolved style.
     */
    public UiStyle style() {
        return style;
    }

    /**
     * Class string with utility tokens.
     */
    public String styleClass() {
        return styleClass;
    }

    /**
     * Event bindings, for example {@code click -> "inventory.open"}.
     */
    public Map<String, String> events() {
        return events;
    }

    /**
     * Extra data for tooling/debug.
     */
    public Map<String, Object> metadata() {
        return metadata;
    }

    /**
     * Declarative children.
     */
    public List<UiNodeSpec> children() {
        return children;
    }
}
