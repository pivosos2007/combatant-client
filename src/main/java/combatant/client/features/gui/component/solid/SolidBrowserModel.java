package combatant.client.features.gui.component.solid;

import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeSpec;

import java.util.List;

/** Immutable input model; it contains no module, setting, map, or service concepts. */
public record SolidBrowserModel<N, I>(List<SolidNavigationEntry<N>> navigation,
                                     List<SolidCollectionEntry<I>> collection,
                                     String collectionTitle,
                                     String searchText,
                                     String searchPlaceholder,
                                     SolidDetailModel detail,
                                     UiNodeSpec brandingSlot,
                                     UiNodeSpec footerSlot,
                                     boolean searchSelectsFirstResult) {
    public SolidBrowserModel {
        navigation = navigation != null ? List.copyOf(navigation) : List.of();
        collection = collection != null ? List.copyOf(collection) : List.of();
        collectionTitle = collectionTitle != null ? collectionTitle : "";
        searchText = searchText != null ? searchText : "";
        searchPlaceholder = searchPlaceholder != null ? searchPlaceholder : "Search";
        detail = detail != null ? detail : SolidDetailModel.shortcuts(null);
    }
}
