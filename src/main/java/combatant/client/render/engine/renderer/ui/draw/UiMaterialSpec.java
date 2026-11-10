/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.draw;

/**
 * Describes a UI material independently of its backdrop source.
 * The material family determines whether sampling the backdrop is required.
 */
public sealed interface UiMaterialSpec permits UiSurfaceMaterialSpec, UiLiquidGlassMaterial, UiHoneycombMaterialSpec, UiIceMaterialSpec {
    Family family();

    default boolean samplesBackdrop() {
        return family() == Family.GLASS;
    }

    enum Family { SOLID, GLASS, HONEYCOMB }
}
