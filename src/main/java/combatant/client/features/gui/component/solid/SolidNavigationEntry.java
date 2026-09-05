package combatant.client.features.gui.component.solid;

import combatant.client.render.engine.renderer.ui.runtime.asset.UiAssetRef;

public record SolidNavigationEntry<T>(String stableId, T value, String label, UiAssetRef icon, boolean selected) {
    public SolidNavigationEntry {
        if (stableId == null || stableId.isBlank()) throw new IllegalArgumentException("Navigation stableId is blank");
        label = label != null ? label : "";
    }
}
