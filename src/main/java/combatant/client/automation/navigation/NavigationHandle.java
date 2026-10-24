/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.navigation;

/**
 * One running navigation. The driver (normally {@code NavigateAction}) calls {@link #tick} once per game tick,
 * then reads {@link #intent()} unless {@link #drivesPlayer()}. Handles never press keys and never block: planning
 * is sliced across ticks.
 *
 * <p>{@link #cancel()} is idempotent and a no-op once the handle is terminal; the status and result of a
 * terminal handle never change.</p>
 */
public interface NavigationHandle {

    NavigationStatus status();

    /** Null while running. */
    NavigationResult result();

    /** Advances planning and following by one tick. Does nothing once terminal. */
    void tick(PlayerSample player);

    /** This tick's wish; meaningless when {@link #drivesPlayer()}. Valid until the next {@link #tick}. */
    NavigationIntent intent();

    /**
     * True for providers that move the player themselves (an external pathing mod): the driver must not apply
     * {@link #intent()}, only keep its leases so nothing else fights the provider.
     */
    boolean drivesPlayer();

    /** Distance left to the goal in blocks, or NaN when unknown. */
    double remainingDistance();

    /** Search nodes expanded so far for this goal, or -1 when the provider does not say. */
    int expandedNodes();

    /** Stops the navigation as {@link NavigationStatus#CANCELLED}; also stops an external provider. Idempotent. */
    void cancel();

    default boolean isDone() {
        return status().isDone();
    }
}
