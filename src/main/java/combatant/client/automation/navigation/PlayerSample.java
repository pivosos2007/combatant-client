/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.navigation;

/**
 * The player's state as a handle needs it, filled by whoever drives the handle (the action reads the live player,
 * tests write numbers). Positions are the feet; velocity is last tick's.
 */
public final class PlayerSample {
    public double x;
    public double y;
    public double z;
    public double vx;
    public double vz;
    public double eyeHeight = 1.62;
    public boolean onGround = true;
    public boolean horizontalCollision;

    public PlayerSample set(double x, double y, double z, double vx, double vz, boolean onGround,
                            boolean horizontalCollision) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.vx = vx;
        this.vz = vz;
        this.onGround = onGround;
        this.horizontalCollision = horizontalCollision;
        return this;
    }
}
