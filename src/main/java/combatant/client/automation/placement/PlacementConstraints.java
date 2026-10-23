/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.placement;

import combatant.client.util.aiming.features.MovementCorrection;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Policy for one placement, built by the owning module from its own settings; placement code never reads global
 * settings.
 *
 * @param range                reach to the support face, blocks
 * @param wallRange            reach through walls: supports closer than this need no line of sight
 * @param rotate               aiming mode
 * @param movementCorrection   movement correction while the rotation is held
 * @param constructFailResult  click on the planned face even when the trace misses it
 * @param ignoreEntityCollision place into cells occupied by entities
 * @param sneakTicks           ticks to sneak before clicking an interactable support (at least 1 is used)
 * @param slotResetMinTicks    lower bound of the ticks the swapped hotbar slot is kept after the click
 * @param slotResetMaxTicks    upper bound of the same range
 * @param swing                swing the hand on success
 * @param confirmTimeoutTicks  ticks to wait for the server acknowledgement; 0 derives it from ping
 */
public record PlacementConstraints(
        double range,
        double wallRange,
        PlacementRotationMode rotate,
        MovementCorrection movementCorrection,
        boolean constructFailResult,
        boolean ignoreEntityCollision,
        int sneakTicks,
        int slotResetMinTicks,
        int slotResetMaxTicks,
        boolean swing,
        int confirmTimeoutTicks) {

    public PlacementConstraints {
        range = Math.max(0.0, range);
        wallRange = Math.max(0.0, wallRange);
        rotate = rotate != null ? rotate : PlacementRotationMode.NORMAL;
        movementCorrection = movementCorrection != null ? movementCorrection : MovementCorrection.SILENT;
        sneakTicks = Math.max(0, sneakTicks);
        slotResetMinTicks = Math.max(0, slotResetMinTicks);
        slotResetMaxTicks = Math.max(0, slotResetMaxTicks);
        confirmTimeoutTicks = Math.max(0, confirmTimeoutTicks);
    }

    /** Reach used for tracing: whichever of the two ranges is larger. */
    public double traceRange() {
        return Math.max(range, wallRange);
    }

    /** A random slot reset delay inside the configured range. */
    public int randomSlotResetTicks() {
        int min = Math.min(slotResetMinTicks, slotResetMaxTicks);
        int max = Math.max(slotResetMinTicks, slotResetMaxTicks);
        return min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
