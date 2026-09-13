/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.scene;

/**
 * Producer-owned semantic class for a scene submission.
 *
 * <p>Consumers must not reconstruct this information from framebuffer state, shader ids,
 * texture ids, screen position or depth. {@link #UNKNOWN} is an explicit fallback state.</p>
 */
public enum SceneDrawClass {
    TERRAIN_OPAQUE,
    TERRAIN_CUTOUT,
    TERRAIN_TRANSLUCENT,
    ENTITY,
    BLOCK_ENTITY,
    ITEM,
    ARMOR,
    GLINT,
    TEXT,
    PARTICLE,
    WEATHER,
    CLOUD,
    SKY,
    WORLD_BORDER,
    BEACON,
    PORTAL,
    LIGHTNING,
    HAND,
    WORLD_OVERLAY,
    CUSTOM,
    UNKNOWN
}
