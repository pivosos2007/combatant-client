package combatant.client.features.gui.component.solid;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolidBrowserSurfaceTest {
    @Test
    void preservesRemovedRowsForExitLifecycleAndReusesSurvivors() {
        SolidBrowserSurface<String, String> surface = new SolidBrowserSurface<>(
                SolidStyleTokens.defaults(), SolidAssetBindings.defaults(), new SolidBrowserCallbacks<>() {});
        surface.setModel(model(List.of(entry("a"), entry("b"), entry("c"))));
        long bRuntimeId = find(surface.runtime().root(), "solid:row:b").runtimeId();

        surface.setModel(model(List.of(entry("c"), entry("b"), entry("d"))));

        assertEquals(bRuntimeId, find(surface.runtime().root(), "solid:row:b").runtimeId());
        assertTrue(surface.retainedRowIds().contains("a"));
        assertEquals(List.of("c", "b", "d", "a"), surface.retainedRowIds());
    }

    private static SolidBrowserModel<String, String> model(List<SolidCollectionEntry<String>> entries) {
        return new SolidBrowserModel<>(List.of(), entries, "Collection", "", "Search",
                SolidDetailModel.shortcuts(null), null, null, false);
    }

    private static SolidCollectionEntry<String> entry(String id) {
        return new SolidCollectionEntry<>(id, id, id, null, false, false);
    }

    private static combatant.client.render.engine.renderer.ui.runtime.core.UiNode find(
            combatant.client.render.engine.renderer.ui.runtime.core.UiNode node, String key) {
        if (node.key().equals(key)) return node;
        for (var child : node.children()) {
            var found = find(child, key);
            if (found != null) return found;
        }
        return null;
    }
}
