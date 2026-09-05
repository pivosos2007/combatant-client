/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.compat.xaero;

import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.map.mods.SupportMods;
import xaero.map.mods.SupportXaeroMinimap;

import java.util.ArrayList;
import java.util.List;

/** Typed Xaero World Map adapter kept separate so World Map remains optional at runtime. */
public enum XaeroWorldMapIntegration {
    ;

    public static List<xaero.map.mods.gui.Waypoint> waypoints(SupportXaeroMinimap minimap) {
        if (minimap == null) return List.of();
        List<XaeroWaypointSnapshot> snapshots = XaeroIntegration.snapshots(XaeroIntegration.RenderTarget.WORLD_MAP);
        if (snapshots.isEmpty()) return List.of();

        List<xaero.map.mods.gui.Waypoint> result = new ArrayList<>(snapshots.size());
        for (XaeroWaypointSnapshot snapshot : snapshots) {
            Waypoint base = new Waypoint(
                    (int) Math.round(snapshot.x()),
                    snapshot.y(),
                    (int) Math.round(snapshot.z()),
                    snapshot.name(),
                    snapshot.symbol(),
                    XaeroMinimapIntegration.color(snapshot.color()),
                    WaypointPurpose.NORMAL
            );
            base.setTemporary(snapshot.temporary());
            base.setYIncluded(snapshot.yIncluded());
            base.setDisabled(false);
            result.add(minimap.convertWaypoint(base, false, snapshot.set(), minimap.getDimDiv()));
        }
        return List.copyOf(result);
    }

    public static void requestRefresh() {
        SupportXaeroMinimap minimap = SupportMods.xaeroMinimap;
        if (minimap != null) {
            minimap.requestWaypointsRefresh();
        }
    }
}
