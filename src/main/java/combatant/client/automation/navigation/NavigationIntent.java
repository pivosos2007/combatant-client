/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.navigation;

/**
 * What a handle wants the player to do this tick. Never touches input itself: the driver converts it to keys
 * through the automation leases. Mutable and reused every tick.
 */
public final class NavigationIntent {
    /** Walk toward {@link #aimX}/{@link #aimZ}; false means release the movement keys (coast, wait, planning). */
    public boolean steer;
    public double aimX;
    public double aimZ;
    public boolean jump;
    /** Sprint is wanted; the driver still checks vanilla's sprint conditions and that it only moves forward. */
    public boolean sprint;

    public void clear() {
        steer = false;
        jump = false;
        sprint = false;
    }
}
