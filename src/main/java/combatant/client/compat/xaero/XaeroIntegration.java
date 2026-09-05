/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.compat.xaero;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** Central Xaero boundary. Individual Combatant systems contribute immutable data sources. */
public enum XaeroIntegration {
    ;

    private static final CopyOnWriteArrayList<XaeroWaypointSource> SOURCES =
            new CopyOnWriteArrayList<>(List.of(TriangulatorXaeroSource.INSTANCE));

    public static Registration register(XaeroWaypointSource source) {
        Objects.requireNonNull(source, "source");
        for (XaeroWaypointSource existing : SOURCES) {
            if (existing.id().equals(source.id())) {
                throw new IllegalArgumentException("Xaero waypoint source is already registered: " + source.id());
            }
        }
        SOURCES.add(source);
        return () -> SOURCES.remove(source);
    }

    public static List<XaeroWaypointSource> sources() {
        return List.copyOf(SOURCES);
    }

    public static List<XaeroWaypointSnapshot> snapshots(RenderTarget target) {
        Objects.requireNonNull(target, "target");
        Map<String, XaeroWaypointSnapshot> result = new LinkedHashMap<>();
        for (XaeroWaypointSource source : SOURCES) {
            List<XaeroWaypointSnapshot> snapshots;
            try {
                snapshots = source.snapshots();
            } catch (RuntimeException ignored) {
                continue;
            }
            if (snapshots == null) continue;
            for (XaeroWaypointSnapshot snapshot : snapshots) {
                if (snapshot != null && snapshot.visibleAt(target)) {
                    String qualifiedId = source.id() + ':' + snapshot.id();
                    result.put(qualifiedId, qualify(qualifiedId, snapshot));
                }
            }
        }
        return List.copyOf(new ArrayList<>(result.values()));
    }

    private static XaeroWaypointSnapshot qualify(String id, XaeroWaypointSnapshot snapshot) {
        return new XaeroWaypointSnapshot(
                id,
                snapshot.x(),
                snapshot.y(),
                snapshot.z(),
                snapshot.coordinateSpace(),
                snapshot.name(),
                snapshot.symbol(),
                snapshot.set(),
                snapshot.color(),
                snapshot.yIncluded(),
                snapshot.temporary(),
                snapshot.worldHud(),
                snapshot.minimap(),
                snapshot.worldMap()
        );
    }

    public enum RenderTarget {
        WORLD_HUD,
        MINIMAP,
        WORLD_MAP
    }

    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }
}
