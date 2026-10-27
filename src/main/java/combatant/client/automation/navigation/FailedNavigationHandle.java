/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.navigation;

/** A handle that is already over: what a provider returns when it cannot start. */
public final class FailedNavigationHandle extends AbstractNavigationHandle {

    public FailedNavigationHandle(NavigationRequest request, NavigationFailure failure, String detail) {
        super(request);
        fail(failure, detail);
    }

    @Override
    protected void step(PlayerSample player) {
    }

    @Override
    public boolean drivesPlayer() {
        return false;
    }

    @Override
    public double remainingDistance() {
        return Double.NaN;
    }

    @Override
    public int expandedNodes() {
        return -1;
    }
}
