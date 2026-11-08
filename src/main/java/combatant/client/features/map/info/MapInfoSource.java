/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.info;

/** A module-owned map information provider. The caller invokes it on the client render thread. */
public interface MapInfoSource {
    /** Only report actual matches from the module's existing filtered detection/cache state. */
    void collectMapInfo(MapInfoSink sink);
}
