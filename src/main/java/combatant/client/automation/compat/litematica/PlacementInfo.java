/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.compat.litematica;

/**
 * One schematic placement as the user set it up in Litematica.
 *
 * @param name    display name
 * @param bounds  the placement's enclosing box in world coordinates, or null while it has no regions
 * @param enabled whether Litematica renders (and therefore materialises) it
 */
public record PlacementInfo(String name, SchematicBox bounds, boolean enabled) {
}
