/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.compat.xaero;

import org.jetbrains.annotations.Nullable;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.map.mods.SupportXaeroMinimap;

import java.util.List;

/** Compatibility entrypoint for command waypoint mixins. */
public enum XaeroCommandWaypointCompat {
    ;

    public static List<Waypoint> getMinimapWaypoints() {
        return List.of();
    }

    public static List<xaero.map.mods.gui.Waypoint> getWorldMapWaypoints(@Nullable SupportXaeroMinimap minimap) {
        return List.of();
    }
}
