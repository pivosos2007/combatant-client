/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;
import combatant.client.render.engine.renderer.ui.runtime.style.UiInlineStyle;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UiScriptObjectConverterTest {
    private final UiScriptObjectConverter converter = new UiScriptObjectConverter();

    @Test
    void usesSharedEmptyValuesForNodesWithoutOptionalFields() {
        var node = converter.convert(Map.of("type", "panel", "key", "empty"));
        assertSame(UiProps.EMPTY, node.props());
        assertSame(UiInlineStyle.EMPTY, node.inlineStyle());
        assertTrue(node.events().isEmpty());
        assertTrue(node.metadata().isEmpty());
    }

    @Test
    void explicitPropsAndStyleOverridePromotedFields() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", "panel");
        source.put("value", 1);
        source.put("props", Map.of("value", 2, "enabled", true));
        source.put("width", 10);
        source.put("style", Map.of("width", 20));
        source.put("events", Map.of("click", "original"));
        source.put("onClick", "override");

        var node = converter.convert(source);
        assertEquals(2, node.props().get("value"));
        assertEquals(true, node.props().get("enabled"));
        assertEquals(20, node.inlineStyle().values().get("width"));
        assertEquals("override", node.events().get("click"));
    }

    @Test
    void directEventAliasWorksWithoutAnEventsObject() {
        var node = converter.convert(Map.of("type", "panel", "onClick", "open"));
        assertEquals(Map.of("click", "open"), node.events());
    }
}
