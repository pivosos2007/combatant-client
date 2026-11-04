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

class UiRuntimePropSlotsTest {
    @Test
    void dynamicPatchReusesNodeOwnedPropsWithoutTouchingSpec() {
        UiNodeSpec spec = UiNodeSpec.text("label", "initial", null);
        UiNode node = UiNode.create(spec);
        UiProps original = node.props();
        assertFalse(node.patchProps(Map.of("text", "initial")));
        assertSame(original, node.props());
        assertTrue(node.patchProps(Map.of("text", "first")));
        UiProps mutable = node.props();
        assertNotSame(original, mutable);
        Map<String, Object> storage = mutable.values();
        for (int i = 0; i < 100; i++) {
            assertTrue(node.patchProps(Map.of("text", "tick-" + i)));
            assertSame(mutable, node.props());
            assertSame(storage, node.props().values());
        }
        assertEquals("initial", original.string("text", ""));
        assertEquals("initial", spec.props().string("text", ""));
        assertEquals("tick-99", node.props().string("text", ""));
        assertFalse(node.patchProps(Map.of("text", "tick-99")));
    }

    @Test
    void firstPatchOfEmptyDeclarativePropsUsesMutablePreallocatedStorage() {
        UiNodeSpec spec = UiNodeSpec.root();
        UiNode node = UiNode.create(spec);
        assertSame(UiProps.EMPTY, node.props());

        assertTrue(node.patchProps(Map.of("text", "Speed", "value", 12.5f)));
        UiProps runtimeProps = node.props();
        Map<String, Object> storage = runtimeProps.values();
        assertNotSame(UiProps.EMPTY, runtimeProps);
        assertEquals("Speed", runtimeProps.string("text", ""));
        assertEquals(12.5f, runtimeProps.number("value", 0), 0.001f);
        assertFalse(UiProps.EMPTY.values().containsKey("text"));

        for (int i = 0; i < 100; i++) {
            assertTrue(node.patchProps(Map.of("value", (float) i)));
            assertSame(runtimeProps, node.props());
            assertSame(storage, node.props().values());
        }
        assertFalse(node.patchProps(Map.of("value", 99.0f)));
        assertEquals(99.0f, node.props().number("value", 0), 0.001f);
    }

    @Test
    void disablingViaPatchImmediatelyUpdatesInteractionState() {
        UiNode node = UiNode.create(UiNodeSpec.text("label", "ok", null));
        node.state().setHovered(true);
        node.state().setActive(true);
        node.state().setFocused(true);
        assertTrue(node.patchProps(Map.of("disabled", true)));
        assertTrue(node.state().disabled());
        assertFalse(node.state().hovered());
        assertFalse(node.state().active());
        assertFalse(node.state().focused());
        assertTrue(node.patchProps(Map.of("disabled", false)));
        assertFalse(node.state().disabled());
    }

    @Test
    void reconciliationReplacesSlotStorageWithNewSpec() {
        UiNode node = UiNode.create(UiNodeSpec.text("label", "original", null));
        node.patchProps(Map.of("text", "patched"));
        node.updateFromSpec(UiNodeSpec.text("label", "after-reconcile", null));
        assertEquals("after-reconcile", node.props().string("text", ""));
        assertTrue(node.patchProps(Map.of("text", "current")));
        assertEquals("current", node.props().string("text", ""));
    }
}
