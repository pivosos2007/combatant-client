/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.shader;

/**
 * Entity-like render state variants for rig geometry.
 * Geometry/deformation is shared; only alpha, culling and depth-write policy changes.
 */
public enum RigRenderMode {
    CUTOUT,
    CUTOUT_CULL,
    TRANSLUCENT,
    TRANSLUCENT_CULL,
    TRANSLUCENT_NO_DEPTH_WRITE,
    TRANSLUCENT_NO_DEPTH_WRITE_CULL
}
