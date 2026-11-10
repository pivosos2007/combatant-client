/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.asset;

import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;

/**
 * Supplies custom asset references for a registered asset type.
 * Returning {@code null} allows the built-in asset-kind fallback to handle the request.
 */
@FunctionalInterface
public interface UiAssetProvider {
    UiAssetRef resolve(UiProps props, String type, String id, float intrinsicWidth, float intrinsicHeight);
}
