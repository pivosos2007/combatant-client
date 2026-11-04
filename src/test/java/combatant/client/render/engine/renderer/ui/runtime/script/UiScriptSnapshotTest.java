/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.runtime.script;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UiScriptSnapshotTest {
    @Test
    void detachesMutableNestedPropsBeforeWorkerReadsThem() {
        ArrayList<Integer> rows = new ArrayList<>(List.of(1, 2));
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("rows", rows);
        Map<String, Object> originals = new LinkedHashMap<>();
        originals.put("content", nested);
        originals.put("color", "#FFFFFFFF");

        Map<String, Object> snapshot = UiScriptSnapshot.capture(originals);
        rows.set(0, 999);
        nested.put("late", 100);
        originals.clear();

        @SuppressWarnings("unchecked")
        Map<String, Object> copied = (Map<String, Object>) snapshot.get("content");
        assertEquals(List.of(1, 2), copied.get("rows"));
        assertFalse(copied.containsKey("late"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("bad", true));
        assertThrows(UnsupportedOperationException.class, () -> copied.put("bad", true));
    }

    @Test
    void rejectsLiveMutableJavaObjectsAndCycles() {
        assertThrows(IllegalArgumentException.class,
                () -> UiScriptSnapshot.capture(Map.of("live", new StringBuilder("mutable"))));
        Map<String, Object> cyclic = new LinkedHashMap<>();
        cyclic.put("self", cyclic);
        assertThrows(IllegalArgumentException.class, () -> UiScriptSnapshot.capture(cyclic));
    }

    @Test
    void normalizesLongsToJavaScriptNumbersAndAvoidsSecondCopy() {
        Map<String, Object> snapshot = UiScriptSnapshot.capture(Map.of(
                "small", 17L, "large", 9007199254740000L,
                "rows", List.of(Map.of("tick", 42L))));
        assertInstanceOf(Integer.class, snapshot.get("small"));
        assertInstanceOf(Double.class, snapshot.get("large"));
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) ((List<?>) snapshot.get("rows")).get(0);
        assertInstanceOf(Integer.class, first.get("tick"));
        var props = combatant.client.render.engine.renderer.ui.runtime.core.UiProps.fromDetachedSnapshot(snapshot);
        assertSame(snapshot, props.values());
        assertTrue(props.isDetachedSnapshot());
    }

    @Test
    void representsEmptyMinecraftItemAsDetachedData() {
        // Even a shared ItemStack.EMPTY must not be exposed to the V8 worker.
        Map<String, Object> snapshot = UiScriptSnapshot.capture(
                Map.of("stack", net.minecraft.world.item.ItemStack.EMPTY));
        assertEquals(Map.of("empty", true), snapshot.get("stack"));
        assertFalse(snapshot.get("stack") instanceof net.minecraft.world.item.ItemStack);
    }

    @Test
    void detachesPrimitiveArrays() {
        int[] positions = {1, 2, 3};
        Map<String, Object> copy = UiScriptSnapshot.capture(Map.of("positions", positions));
        positions[1] = 500;
        assertEquals(List.of(1, 2, 3), copy.get("positions"));
    }
}
