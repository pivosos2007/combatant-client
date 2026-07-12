/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.compat.xaero;

import org.jetbrains.annotations.Nullable;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;

public enum TriangulatorXaeroMinimapCompat {
    ;
    private static Waypoint hudWaypoint;
    private static Waypoint minimapWaypoint;
    private static boolean hudActive;
    private static boolean minimapActive;

    @Nullable
    public static Waypoint getHudWaypoint() {
        sync();
        return hudActive ? hudWaypoint : null;
    }

    @Nullable
    public static Waypoint getMinimapWaypoint() {
        sync();
        return minimapActive ? minimapWaypoint : null;
    }

    private static void sync() {
        TriangulatorXaeroState.XaeroSnapshot snapshot = TriangulatorXaeroState.snapshot();
        if (snapshot == null) {
            hudActive = false;
            minimapActive = false;
            return;
        }

        TriangulatorXaeroState.SyncPoint point = TriangulatorXaeroState.toCurrentDimension(snapshot);
        WaypointColor color = snapshot.ready() ? WaypointColor.GOLD : WaypointColor.RED;

        hudWaypoint = syncWaypoint(hudWaypoint, point, color);
        minimapWaypoint = syncWaypoint(minimapWaypoint, point, color);
        hudActive = true;
        minimapActive = true;
    }

    private static Waypoint syncWaypoint(@Nullable Waypoint waypoint,
                                         TriangulatorXaeroState.SyncPoint point,
                                         WaypointColor color) {
        Waypoint result = waypoint;
        if (result == null) {
            result = new Waypoint(
                    point.x(),
                    point.y(),
                    point.z(),
                    TriangulatorXaeroState.NAME,
                    TriangulatorXaeroState.SYMBOL,
                    color,
                    WaypointPurpose.NORMAL
            );
            result.setTemporary(false);
            result.setYIncluded(false);
        }
        result.setX(point.x());
        result.setY(point.y());
        result.setZ(point.z());
        result.setWaypointColor(color);
        result.setDisabled(false);
        return result;
    }
}
