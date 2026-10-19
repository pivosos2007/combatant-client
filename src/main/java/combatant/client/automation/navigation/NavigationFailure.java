/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.navigation;

/** Why a navigation ended without arriving. */
public enum NavigationFailure {
    NONE,
    /** No route to the goal (or none that obeys the request options). */
    NO_PATH,
    /** {@link NavigationRequest.Options#timeoutTicks()} ran out. */
    TIMEOUT,
    /** The follower gave up: wedged against something, or replanned too often. */
    STUCK,
    /** The provider cannot run (mod missing, no world, no player). */
    UNAVAILABLE,
    /** The provider threw or reported something unexpected. */
    ERROR,
    CANCELLED
}
