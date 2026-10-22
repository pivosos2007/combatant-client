/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.clickgui.sections;

import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.UiLiquidGlassMaterial;

/** Map controls retain four Kawase passes; a shorter offset keeps map labels legible. */
final class XaeroMapGlass {
    private static final float KAWASE_OFFSET_PX = 0.68f;

    private XaeroMapGlass() {}

    static void withMaterial(Renderer2D renderer, UiLiquidGlassMaterial material, Runnable draw) {
        renderer.withLiquidGlassBlurProfile(Renderer2D.BlurQuality.LIQUID_GLASS,
                KAWASE_OFFSET_PX, () -> renderer.withLiquidGlassMaterial(material, draw));
    }
}
