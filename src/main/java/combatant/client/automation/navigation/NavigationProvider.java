/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.navigation;

/** A way of getting the player somewhere. Implementations must be cheap to ask {@link #isAvailable()}. */
public interface NavigationProvider {

    /** Stable lowercase id used by module settings, for example {@code "walk"} or {@code "baritone"}. */
    String id();

    /** Whether {@link #navigate} can work right now (backing mod present, world loaded...). */
    boolean isAvailable();

    /** Starts a navigation. Never returns null; a provider that cannot start returns an already failed handle. */
    NavigationHandle navigate(NavigationRequest request);
}
