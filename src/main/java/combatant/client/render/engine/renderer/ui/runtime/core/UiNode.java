/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.core;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Live runtime node produced by reconciliation.
 *
 * <p>Unlike {@link UiNodeSpec}, this object stores mutable state: bounds,
 * parent/children links, hover/focus/scroll state, animations, measured size,
 * and local error text.</p>
 */
public final class UiNode {
    private static final AtomicLong NEXT_RUNTIME_ID = new AtomicLong(1L);

    private final long runtimeId;
    private final String key;
    private final UiNodeType type;
    private final UiState state = new UiState();
    private final ObjectArrayList<UiNode> children = new ObjectArrayList<>();
    private UiProps props;
    private UiStyle style;
    private String styleClass;
    private Map<String, String> events = Map.of();
    private Map<String, Object> metadata = Map.of();
    private UiBounds bounds = UiBounds.ZERO;
    private UiNode parent;
    private String lastError;
    private float measuredWidth;
    private float measuredHeight;

    private UiNode(String key, UiNodeType type) {
        this.runtimeId = NEXT_RUNTIME_ID.getAndIncrement();
        this.key = key != null ? key : "";
        this.type = type != null ? type : UiNodeType.PANEL;
    }

    public static UiNode create(UiNodeSpec spec) {
        UiNode node = new UiNode(spec.key(), spec.type());
        node.updateFromSpec(spec);
        return node;
    }

    public static UiNode fromSpec(UiNodeSpec spec) {
        UiNode node = create(spec);
        for (UiNodeSpec childSpec : spec.children()) {
            UiNode child = fromSpec(childSpec);
            child.parent = node;
            node.children.add(child);
        }
        return node;
    }

    /**
     * Unique runtime id. This is for debug/tooling, not reconciliation.
     */
    public long runtimeId() {
        return runtimeId;
    }

    /**
     * Stable spec key.
     */
    public String key() {
        return key;
    }

    /**
     * Node type from the spec.
     */
    public UiNodeType type() {
        return type;
    }

    /**
     * Props from the current spec.
     */
    public UiProps props() {
        return props;
    }

    /**
     * Resolved style from the current spec.
     */
    public UiStyle style() {
        return style;
    }

    /**
     * Source class string for debug.
     */
    public String styleClass() {
        return styleClass;
    }

    /**
     * Event bindings from the current spec.
     */
    public Map<String, String> events() {
        return events;
    }

    /**
     * Metadata from the current spec.
     */
    public Map<String, Object> metadata() {
        return metadata;
    }

    /**
     * Bounds after the layout pass.
     */
    public UiBounds bounds() {
        return bounds;
    }

    /**
     * Parent node, or null for root.
     */
    public UiNode parent() {
        return parent;
    }

    /**
     * Mutable interaction/animation/scroll state.
     */
    public UiState state() {
        return state;
    }

    /**
     * Mutable children list updated by the reconciler.
     */
    public List<UiNode> children() {
        return children;
    }

    /**
     * Last local node error.
     */
    public String lastError() {
        return lastError;
    }

    /**
     * Intrinsic/resolved width after measure and before assign.
     */
    public float measuredWidth() {
        return measuredWidth;
    }

    /**
     * Intrinsic/resolved height after measure and before assign.
     */
    public float measuredHeight() {
        return measuredHeight;
    }

    /**
     * Stores the result of the measure pass. Layout assignment reads these
     * values without measuring the subtree again.
     */
    public void setMeasuredSize(float measuredWidth, float measuredHeight) {
        this.measuredWidth = Math.max(0.0f, measuredWidth);
        this.measuredHeight = Math.max(0.0f, measuredHeight);
    }

    /**
     * Updates runtime bounds after layout assignment or animation correction.
     */
    public void setBounds(UiBounds bounds) {
        this.bounds = bounds != null ? bounds : UiBounds.ZERO;
    }

    /**
     * Replaces props without touching style, children, or interaction state.
     */
    public void setProps(UiProps props) {
        this.props = props != null ? props : UiProps.EMPTY;
    }

    /**
     * Sets the parent link.
     */
    public void setParent(UiNode parent) {
        this.parent = parent;
    }

    /**
     * Sets the node error text used by diagnostics tree dump.
     */
    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    /**
     * Copies spec fields into this runtime node without resetting state.
     */
    public void updateFromSpec(UiNodeSpec spec) {
        this.props = spec.props();
        this.style = spec.style();
        this.styleClass = spec.styleClass();
        this.events = spec.events();
        this.metadata = spec.metadata();
    }

    /**
     * Replaces the children list and assigns parent for every child.
     */
    public void replaceChildren(List<UiNode> newChildren) {
        children.clear();
        if (newChildren == null) return;
        for (UiNode child : newChildren) {
            if (child == null) continue;
            child.setParent(this);
            children.add(child);
        }
    }

    /**
     * Returns whether this runtime node can be reused for the given spec.
     */
    public boolean sameIdentity(UiNodeSpec spec) {
        if (spec == null) return false;
        return type == spec.type() && key.equals(spec.key());
    }
}
