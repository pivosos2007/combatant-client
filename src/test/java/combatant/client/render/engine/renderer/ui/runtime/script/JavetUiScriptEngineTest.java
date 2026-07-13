/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class JavetUiScriptEngineTest {
    @Test
    void normalizesLongPropsToJavaScriptNumbersRecursively() {
        Object normalized = JavetUiScriptEngine.plainValue(Map.of(
                "count", 37L,
                "items", List.of(Map.of("count", 64L)),
                "large", (long) Integer.MAX_VALUE + 1L
        ));

        Map<?, ?> props = assertInstanceOf(Map.class, normalized);
        assertEquals(37, props.get("count"));

        List<?> items = assertInstanceOf(List.class, props.get("items"));
        Map<?, ?> item = assertInstanceOf(Map.class, items.getFirst());
        assertEquals(64, item.get("count"));

        Number large = assertInstanceOf(Number.class, props.get("large"));
        assertEquals((double) Integer.MAX_VALUE + 1.0, large.doubleValue());
    }
}
