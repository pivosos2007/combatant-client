/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player;

public final class VehicleFallGroundController {
    private static final double RESET_DISTANCE = 1.35D;
    private static final double MAX_SAFE_DISTANCE = 2.45D;
    private static final double MIN_FALL_STEP = 0.015D;

    private Object trackedVehicle;
    private double previousY = Double.NaN;
    private double accumulatedFall;
    private boolean unsafe;

    public void reset() {
        trackedVehicle = null;
        previousY = Double.NaN;
        accumulatedFall = 0.0D;
        unsafe = false;
    }

    public boolean shouldSpoof(Object vehicle, double y, double reportedFallDistance, boolean ground) {
        if (vehicle == null || !Double.isFinite(y)) {
            reset();
            return false;
        }
        if (vehicle != trackedVehicle) {
            trackedVehicle = vehicle;
            previousY = y;
            accumulatedFall = Math.max(0.0D, reportedFallDistance);
            unsafe = !Double.isFinite(reportedFallDistance)
                    || accumulatedFall > MAX_SAFE_DISTANCE;
            return false;
        }
        double delta = y - previousY;
        previousY = y;
        if (ground) {
            accumulatedFall = 0.0D;
            unsafe = false;
            return false;
        }
        if (!Double.isFinite(delta)) {
            reset();
            return false;
        }
        if (delta >= -MIN_FALL_STEP) {
            if (delta > 0.0D) {
                accumulatedFall = 0.0D;
                unsafe = false;
            }
            return false;
        }
        if (unsafe) return false;
        accumulatedFall += -delta;
        if (accumulatedFall > MAX_SAFE_DISTANCE) {
            unsafe = true;
            return false;
        }
        if (accumulatedFall >= RESET_DISTANCE) {
            accumulatedFall = 0.0D;
            return true;
        }
        return false;
    }
}
