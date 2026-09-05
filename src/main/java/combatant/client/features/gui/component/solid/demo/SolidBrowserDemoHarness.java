/*
 * Isolated visual/interaction harness. It is deliberately not registered as a
 * production ClickGUI or map screen; a debug host may forward its screen input.
 */
package combatant.client.features.gui.component.solid.demo;

import combatant.client.features.gui.component.solid.*;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeSpec;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;

import java.util.ArrayList;
import java.util.List;

public final class SolidBrowserDemoHarness {
    private final List<String> rows = new ArrayList<>();
    private final SolidBrowserSurface<String, String> surface;
    private String navigation = "collection";
    private String selected = "entry-04";
    private String search = "";
    private boolean empty;
    private boolean detailOpen = true;
    private int mutation;

    public SolidBrowserDemoHarness() {
        for (int i = 0; i < 26; i++) rows.add(String.format("entry-%02d", i));
        surface = new SolidBrowserSurface<>(SolidStyleTokens.defaults(), SolidAssetBindings.defaults(), new Callbacks());
        surface.setFloatingViewport(500, 330, true);
        rebuild();
    }

    public SolidBrowserSurface<String, String> surface() { return surface; }

    public void showSearchMode(boolean enabled) {
        search = enabled ? "long" : "";
        rebuild();
    }

    public void showEmptyDetail(boolean value) {
        empty = value;
        detailOpen = true;
        rebuild();
    }

    /** Adds, removes and reorders stable IDs to exercise enter/exit reconciliation. */
    public void mutateCollection() {
        mutation++;
        if (rows.size() > 22) rows.remove(2);
        rows.add(1, "dynamic-" + mutation);
        if (rows.size() > 5) {
            String moved = rows.remove(rows.size() - 1);
            rows.add(4, moved);
        }
        rebuild();
    }

    public void showShortcuts() {
        detailOpen = false;
        empty = false;
        rebuild();
    }

    private void rebuild() {
        List<SolidNavigationEntry<String>> navigationEntries = List.of(
                nav("collection", "Collection", "folder"),
                nav("players", "Players", "user"),
                nav("markers", "Markers", "cross"),
                nav("history", "History", "folder-clock"),
                nav("search", "Search", "eye")
        );
        List<SolidCollectionEntry<String>> collection = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            String id = rows.get(i);
            String label = i == 7
                    ? "A deliberately very long overflowing collection row name for marquee verification"
                    : id.startsWith("dynamic") ? "New retained item " + id : "Generic collection item " + (i + 1);
            collection.add(new SolidCollectionEntry<>(id, id, label, null, id.equals(selected), i % 4 == 0));
        }
        SolidDetailModel detail = new SolidDetailModel(
                "Selected detail",
                "Arbitrary consumer content independent of collection items",
                null,
                detailOpen ? cards() : shortcuts(),
                "Nothing to show for this item",
                centeredText("demo:empty", "Nothing to show for this item", 0.55f),
                detailOpen,
                empty,
                detailOpen
        );
        surface.setModel(new SolidBrowserModel<>(navigationEntries, collection, "Collection", search,
                "Search", detail, null, null, true));
    }

    private SolidNavigationEntry<String> nav(String id, String label, String icon) {
        return new SolidNavigationEntry<>(id, id, label, SolidAssetBindings.svg(icon, 9), id.equals(navigation));
    }

    private static UiNodeSpec cards() {
        SolidStyleTokens tokens = SolidStyleTokens.defaults();
        List<UiNodeSpec> cards = new ArrayList<>();
        for (int i = 0; i < 18; i++) {
            cards.add(SolidPrimitives.card("demo:card:" + i,
                    centeredText("demo:card-text:" + i, "Reusable detail card " + (i + 1), 0.72f), tokens));
        }
        return SolidPrimitives.twoColumnGrid("demo:cards-grid", cards, 80.0f);
    }

    private static UiNodeSpec shortcuts() {
        List<UiNodeSpec> shortcuts = new ArrayList<>();
        String[] labels = {"Overview", "Pinned", "Recent", "Nearby", "Shared", "Archived"};
        for (int i = 0; i < labels.length; i++) {
            shortcuts.add(new UiNodeSpec("demo:shortcut:" + i,
                    combatant.client.render.engine.renderer.ui.runtime.core.UiNodeType.BUTTON,
                    combatant.client.render.engine.renderer.ui.runtime.core.UiProps.EMPTY,
                    UiStyle.builder().width(90).height(17).padding(5, 0, 6, 0).radius(4)
                            .backgroundColor(0x661A171F).cursor("pointer").build(),
                    "solid-shortcut-card", List.of(centeredText("demo:shortcut-text:" + i, labels[i], 0.72f))));
        }
        return SolidPrimitives.twoColumnGrid("demo:shortcuts-grid", shortcuts, 90.0f);
    }

    private static UiNodeSpec centeredText(String key, String text, float alpha) {
        return new UiNodeSpec(key, combatant.client.render.engine.renderer.ui.runtime.core.UiNodeType.TEXT,
                combatant.client.render.engine.renderer.ui.runtime.core.UiProps.of("text", text),
                UiStyle.builder().height(12).textScale(7.0f / 9.0f).textAlign("center")
                        .textColor(SolidStyleTokens.withAlpha(0xFFFFFFFF, alpha)).build(), "solid-demo-text", List.of());
    }

    private final class Callbacks implements SolidBrowserCallbacks<String, String> {
        @Override public void onNavigation(String value) { navigation = value; rebuild(); }
        @Override public void onPrimaryClick(String value) { selected = value; detailOpen = true; empty = false; rebuild(); }
        @Override public void onSecondaryClick(String value) { selected = value; detailOpen = true; rebuild(); }
        @Override public void onAuxiliaryClick(String value) { selected = value; rebuild(); }
        @Override public void onSearchChanged(String value) { search = value; rebuild(); }
        @Override public void onCloseDetail() { detailOpen = false; rebuild(); }
    }
}
