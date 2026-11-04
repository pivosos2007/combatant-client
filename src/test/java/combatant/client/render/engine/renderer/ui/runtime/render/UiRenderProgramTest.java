/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.runtime.render;

import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeSpec;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiRenderProgramTest {
    @Test
    void encodesNestedNodesInDrawOrderAndCanSkipEntireSubtrees() {
        UiNode root = UiNode.fromSpec(UiNodeSpec.root(
                UiNodeSpec.panel("first", UiStyle.DEFAULT,
                        UiNodeSpec.text("child", "txt", UiStyle.DEFAULT)),
                UiNodeSpec.text("second", "txt2", UiStyle.DEFAULT)));
        UiRenderProgram tape = UiRenderProgram.compile(root);
        assertNotNull(tape);
        assertEquals(8, tape.length); // 4 nodes, enter + leave
        assertEquals("root", tape.nodes[0].key());
        assertEquals("first", tape.nodes[1].key());
        assertEquals("child", tape.nodes[2].key());
        assertEquals(UiRenderProgram.LEAVE, tape.operation[3]);
        assertEquals("first", tape.nodes[4].key());
        assertEquals("second", tape.nodes[5].key());
        assertEquals(5, tape.jump[1]); // skip first and all of its descendants
        assertEquals(7, tape.jump[5]);
        assertEquals(8, tape.jump[0]);
        assertEquals(3, tape.nodeContexts.length); // max depth 2, preallocated
    }

    @Test
    void handlesNullRootAndLeaf() {
        assertNull(UiRenderProgram.compile(null));
        UiRenderProgram tape = UiRenderProgram.compile(UiNode.fromSpec(UiNodeSpec.root()));
        assertEquals(2, tape.length);
        assertEquals(2, tape.jump[0]);
        assertEquals(1, tape.childContexts.length);
    }
}
