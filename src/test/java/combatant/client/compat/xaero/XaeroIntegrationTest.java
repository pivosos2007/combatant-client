/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.compat.xaero;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class XaeroIntegrationTest {
    @Test
    void registeredSubsystemIsNamespacedFilteredAndRemovable() {
        XaeroWaypointSnapshot waypoint = new XaeroWaypointSnapshot(
                "target",
                12.0,
                64,
                -8.0,
                XaeroWaypointSnapshot.CoordinateSpace.OVERWORLD,
                "Target",
                "T",
                "combatant",
                XaeroWaypointSnapshot.Color.GOLD,
                false,
                false,
                false,
                true,
                false
        );
        XaeroWaypointSource source = new XaeroWaypointSource() {
            @Override
            public String id() {
                return "test-source";
            }

            @Override
            public List<XaeroWaypointSnapshot> snapshots() {
                return List.of(waypoint);
            }
        };

        XaeroIntegration.Registration registration = XaeroIntegration.register(source);
        try {
            List<XaeroWaypointSnapshot> minimap = XaeroIntegration.snapshots(XaeroIntegration.RenderTarget.MINIMAP);
            assertEquals(1, minimap.stream().filter(it -> it.id().equals("test-source:target")).count());
            assertFalse(XaeroIntegration.snapshots(XaeroIntegration.RenderTarget.WORLD_MAP).stream()
                    .anyMatch(it -> it.id().equals("test-source:target")));
        } finally {
            registration.close();
        }

        assertFalse(XaeroIntegration.snapshots(XaeroIntegration.RenderTarget.MINIMAP).stream()
                .anyMatch(it -> it.id().equals("test-source:target")));
    }
}
