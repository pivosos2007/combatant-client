/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.sodium.fluid;

/** Compatibility facade for explicit height-displacement replacement routing. */
public final class HeightSurfacePatchRouting {
    private HeightSurfacePatchRouting() { }
    public static boolean replacementActive() { return SurfacePatchRouting.heightReplacementActive(); }
    public static boolean setReplacementActive(boolean active) { return SurfacePatchRouting.setHeightReplacementActive(active); }
}
