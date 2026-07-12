/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixininterface;

public interface ILocalPlayer {
    float combatant$getLastYaw();

    float combatant$getLastPitch();

    void combatant$setLastYaw(float yaw);

    void combatant$setLastPitch(float pitch);
}
