package combatant.client.features.gui.component.solid;

import combatant.client.render.engine.renderer.ui.runtime.asset.UiAssetRef;

public record SolidCollectionEntry<T>(String stableId,
                                      T value,
                                      String label,
                                      UiAssetRef icon,
                                      boolean selected,
                                      boolean emphasized) {
    public SolidCollectionEntry {
        if (stableId == null || stableId.isBlank()) throw new IllegalArgumentException("Collection stableId is blank");
        label = label != null ? label : "";
    }
}
