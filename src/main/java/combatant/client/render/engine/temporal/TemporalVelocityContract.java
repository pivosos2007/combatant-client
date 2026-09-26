/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.temporal;

/** Canonical screen-space velocity semantics shared by temporal consumers. */
public final class TemporalVelocityContract {
    private TemporalVelocityContract() { }
    public static final int VERSION = 1;
    public enum Direction { CURRENT_PIXEL_TO_PREVIOUS_PIXEL }
    public enum CoordinateSpace { NORMALIZED_RENDER_UV }
    public static final Direction DIRECTION = Direction.CURRENT_PIXEL_TO_PREVIOUS_PIXEL;
    public static final CoordinateSpace SPACE = CoordinateSpace.NORMALIZED_RENDER_UV;
}
