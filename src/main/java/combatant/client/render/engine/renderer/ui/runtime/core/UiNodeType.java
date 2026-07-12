/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.core;

import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;

/**
 * Base node types understood by layout, render, and input runtime code.
 *
 * <p>The type controls default behavior such as flow direction, intrinsic
 * measurement, or renderer bridge. Visual styling is stored in class tokens
 * and {@link UiStyle}.</p>
 */
public enum UiNodeType {
    /**
     * Top-level document container.
     */
    ROOT,
    /**
     * Generic vertical container.
     */
    PANEL,
    /**
     * Horizontal flow layout.
     */
    ROW,
    /**
     * Vertical flow layout.
     */
    COLUMN,
    /**
     * Overlay layout where children share the same area.
     */
    STACK,
    /**
     * Text node rendered through render.engine.text.
     */
    TEXT,
    /**
     * Image-like asset node rendered through the asset bridge.
     */
    IMAGE,
    /**
     * SVG asset node.
     */
    SVG,
    /**
     * Code-drawn rectangle primitive.
     */
    SHAPE,
    /**
     * Code-drawn connector, wire, cable, or spline primitive.
     */
    CONNECTOR,
    /**
     * Minecraft ItemStack bridge.
     */
    ITEM,
    /**
     * Interactive container node.
     */
    BUTTON,
    /**
     * Scroll container. Scroll state is stored in UiState.
     */
    SCROLL,
    /**
     * Empty element with intrinsic or explicit size.
     */
    SPACER,
    /**
     * Generic input node type.
     */
    INPUT,
    /**
     * Text input node type.
     */
    INPUT_TEXT,
    /**
     * Checkbox node type.
     */
    CHECKBOX,
    /**
     * Slider node type.
     */
    SLIDER,
    /**
     * Divider primitive.
     */
    DIVIDER,
    /**
     * Canvas node.
     */
    CANVAS
}
