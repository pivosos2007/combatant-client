/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.compat.xaero;

import java.util.Objects;

/** Provider-neutral waypoint data consumed only by the Xaero boundary adapters. */
public record XaeroWaypointSnapshot(String id,
                                    double x,
                                    int y,
                                    double z,
                                    CoordinateSpace coordinateSpace,
                                    String name,
                                    String symbol,
                                    String set,
                                    Color color,
                                    boolean yIncluded,
                                    boolean temporary,
                                    boolean worldHud,
                                    boolean minimap,
                                    boolean worldMap) {
    public XaeroWaypointSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(coordinateSpace, "coordinateSpace");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(set, "set");
        Objects.requireNonNull(color, "color");
    }

    public boolean visibleAt(XaeroIntegration.RenderTarget target) {
        return switch (target) {
            case WORLD_HUD -> worldHud;
            case MINIMAP -> minimap;
            case WORLD_MAP -> worldMap;
        };
    }

    public enum CoordinateSpace {
        /** X/Z are canonical Overworld coordinates and are projected for the Nether. */
        OVERWORLD,
        /** X/Z already belong to the dimension currently being rendered. */
        CURRENT_DIMENSION
    }

    public enum Color {
        RED,
        GOLD
    }
}
