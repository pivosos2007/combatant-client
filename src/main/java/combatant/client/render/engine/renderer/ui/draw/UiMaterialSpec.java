/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.draw;

/**
 * Rendering-independent UI material contract. The backdrop source is intentionally separate:
 * texture capture and blur describe the destination, not the surface's physical appearance.
 * Each family has a dedicated shader path rather than an all-purpose fragment shader.
 */
public sealed interface UiMaterialSpec permits UiSurfaceMaterialSpec, UiLiquidGlassMaterial, UiHoneycombMaterialSpec {
    Family family();

    default boolean samplesBackdrop() {
        return family() == Family.GLASS || family() == Family.HONEYCOMB;
    }

    enum Family { SOLID, GLASS, HONEYCOMB }
}
