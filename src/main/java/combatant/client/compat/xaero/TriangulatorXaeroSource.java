/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.compat.xaero;

import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.gui.hud.draggable.impl.Triangulator;

import java.util.List;

/** Triangulation is one producer of central Xaero waypoint snapshots. */
enum TriangulatorXaeroSource implements XaeroWaypointSource {
    INSTANCE;

    private static final String SOURCE_ID = "triangulator";

    @Override
    public String id() {
        return SOURCE_ID;
    }

    @Override
    public List<XaeroWaypointSnapshot> snapshots() {
        DraggableHudElement element = DraggableHudElementRegistry.getById(SOURCE_ID);
        if (!(element instanceof Triangulator triangulator)) return List.of();

        Triangulator.XaeroSnapshot snapshot = triangulator.getXaeroSnapshot();
        if (snapshot == null) return List.of();

        return List.of(new XaeroWaypointSnapshot(
                "stronghold",
                snapshot.overworldX(),
                64,
                snapshot.overworldZ(),
                XaeroWaypointSnapshot.CoordinateSpace.OVERWORLD,
                "Stronghold",
                "S",
                XaeroWaypointStore.SET,
                snapshot.ready() ? XaeroWaypointSnapshot.Color.GOLD : XaeroWaypointSnapshot.Color.RED,
                false,
                false,
                true,
                true,
                true
        ));
    }
}
