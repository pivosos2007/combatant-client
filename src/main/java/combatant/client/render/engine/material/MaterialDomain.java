/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.material;

/**
 * High-level shading domain supplied by the producer together with a scene draw class.
 * Detailed material identity is deliberately separate and will be provided by MaterialRegistry.
 */
public enum MaterialDomain {
    OPAQUE,
    CUTOUT,
    TRANSLUCENT,
    WATER,
    PORTAL,
    EMISSIVE,
    UNKNOWN
}
