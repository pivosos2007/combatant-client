/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.info;

import net.minecraft.world.item.Item;

/**
 * Receives positions already detected by a module, never triggers a world scan.
 * Coordinates are world block X/Z; the map owns chunk/zoom grouping and presentation.
 * Entries with the same source and key in a cell are summed. A null icon is allowed.
 */
@FunctionalInterface
public interface MapInfoSink {
    void add(int blockX, int blockZ, String key, String label, Item icon, int argb, int count);
}
