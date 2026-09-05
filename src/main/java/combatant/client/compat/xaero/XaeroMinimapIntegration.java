/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.compat.xaero;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Converts central Combatant waypoint snapshots into Xaero minimap objects. */
public enum XaeroMinimapIntegration {
    ;

    private static final Map<String, Waypoint> CACHE = new HashMap<>();

    public static List<Waypoint> waypoints(XaeroIntegration.RenderTarget target) {
        List<XaeroWaypointSnapshot> snapshots = XaeroIntegration.snapshots(target);
        if (snapshots.isEmpty()) {
            CACHE.clear();
            return List.of();
        }

        boolean nether = isNether();
        List<Waypoint> result = new ArrayList<>(snapshots.size());
        Set<String> active = new HashSet<>();
        for (XaeroWaypointSnapshot snapshot : snapshots) {
            String cacheKey = target.name() + ':' + snapshot.id();
            active.add(cacheKey);
            double scale = snapshot.coordinateSpace() == XaeroWaypointSnapshot.CoordinateSpace.OVERWORLD && nether
                    ? 0.125
                    : 1.0;
            int x = (int) Math.round(snapshot.x() * scale);
            int z = (int) Math.round(snapshot.z() * scale);
            Waypoint waypoint = CACHE.get(cacheKey);
            if (waypoint == null) {
                waypoint = new Waypoint(
                        x,
                        snapshot.y(),
                        z,
                        snapshot.name(),
                        snapshot.symbol(),
                        color(snapshot.color()),
                        WaypointPurpose.NORMAL
                );
                CACHE.put(cacheKey, waypoint);
            }
            waypoint.setX(x);
            waypoint.setY(snapshot.y());
            waypoint.setZ(z);
            waypoint.setWaypointColor(color(snapshot.color()));
            waypoint.setTemporary(snapshot.temporary());
            waypoint.setYIncluded(snapshot.yIncluded());
            waypoint.setDisabled(false);
            result.add(waypoint);
        }
        CACHE.keySet().removeIf(key -> key.startsWith(target.name() + ':') && !active.contains(key));
        return List.copyOf(result);
    }

    static WaypointColor color(XaeroWaypointSnapshot.Color color) {
        return color == XaeroWaypointSnapshot.Color.GOLD ? WaypointColor.GOLD : WaypointColor.RED;
    }

    private static boolean isNether() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null && mc.level.dimension() == Level.NETHER;
    }
}
