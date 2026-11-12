/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound.acoustics;

/** A main-thread acoustic measurement; native EFX operations consume only this immutable value. */
public record AcousticProfile(int lowPass, Room room) {
    public static final AcousticProfile DRY = new AcousticProfile(0, Room.NONE);

    public enum Room {
        NONE, TUNNEL, CAVE
    }
}
