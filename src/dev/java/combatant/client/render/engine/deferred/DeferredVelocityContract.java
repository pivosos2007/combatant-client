/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

/** Canonical SceneVelocity semantics used by all producers and consumers. */
public final class DeferredVelocityContract {
    private DeferredVelocityContract() { }

    public static final int VERSION = 1;

    /** velocity = currentUv - previousUv, therefore previousUv = currentUv - velocity. */
    public enum Direction {
        CURRENT_PIXEL_TO_PREVIOUS_PIXEL
    }

    public enum CoordinateSpace {
        NORMALIZED_RENDER_UV
    }

    public static final Direction DIRECTION = Direction.CURRENT_PIXEL_TO_PREVIOUS_PIXEL;
    public static final CoordinateSpace SPACE = CoordinateSpace.NORMALIZED_RENDER_UV;
}
