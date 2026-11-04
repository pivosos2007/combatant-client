/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.runtime.core;

import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UiPropLayoutDependencyTest {
    @Test
    void paintAndStatePropsNeverInvalidateGeometry() {
        UiProps before = new UiProps(Map.of("text", "same", "opacity", 1.0f));
        assertFalse(UiPropLayoutDependency.requiresLayout(UiNodeType.TEXT, before,
                Map.of("opacity", 0.25f)));
        assertFalse(UiPropLayoutDependency.requiresLayout(UiNodeType.BUTTON, before,
                Map.of("opacity", 0.25f)));
        assertFalse(UiPropLayoutDependency.requiresLayout(UiNodeType.SHAPE, before,
                Map.of("opacity", 0.25f)));
    }

    @Test
    void textAndIntrinsicDimensionsInvalidateOnlyTheirRelevantNodeTypes() {
        UiProps before = new UiProps(Map.of("text", "one", "intrinsicWidth", 10));
        assertTrue(UiPropLayoutDependency.requiresLayout(UiNodeType.TEXT, before,
                Map.of("text", "two")));
        assertFalse(UiPropLayoutDependency.requiresLayout(UiNodeType.PANEL, before,
                Map.of("text", "two")));
        assertTrue(UiPropLayoutDependency.requiresLayout(UiNodeType.IMAGE, before,
                Map.of("intrinsicWidth", 20)));
        assertTrue(UiPropLayoutDependency.requiresLayout(UiNodeType.ITEM, before,
                Map.of("intrinsicWidth", 20)));
        assertFalse(UiPropLayoutDependency.requiresLayout(UiNodeType.ROW, before,
                Map.of("intrinsicWidth", 20)));
        assertFalse(UiPropLayoutDependency.requiresLayout(UiNodeType.IMAGE, before,
                Map.of("intrinsicWidth", 10)));
    }
}
