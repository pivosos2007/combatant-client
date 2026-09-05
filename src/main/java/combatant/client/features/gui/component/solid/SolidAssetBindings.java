/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.component.solid;

import combatant.client.render.engine.renderer.ui.runtime.asset.UiAssetKind;
import combatant.client.render.engine.renderer.ui.runtime.asset.UiAssetRef;

/** Semantic shell assets. Consumers may replace every binding. */
public record SolidAssetBindings(UiAssetRef logo,
                                 UiAssetRef search,
                                 UiAssetRef close,
                                 UiAssetRef footerAction) {
    public static SolidAssetBindings defaults() {
        return new SolidAssetBindings(
                svg("swords", 11.0f),
                svg("eye", 5.0f),
                svg("x", 9.0f),
                svg("brain-cog", 9.0f)
        );
    }

    public static UiAssetRef svg(String id, float size) {
        return new UiAssetRef(UiAssetKind.SVG, id, size, size);
    }
}
