/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation;

/**
 * Why an action stopped without succeeding. {@link #retryable()} tells {@code RetryAction}
 * whether another attempt can change the outcome.
 */
public enum FailureReason {
    NONE(false),
    /** A precondition stopped holding (target gone, block already placed by someone else). */
    PRECONDITION(false),
    /** A required resource is held by a higher or equal priority owner. */
    RESOURCE_BUSY(true),
    /** A higher priority action took a resource mid-run. */
    PREEMPTED(true),
    /** The player took over a resource that yields to manual input. */
    MANUAL_OVERRIDE(false),
    TIMEOUT(true),
    /** The server did not confirm the expected result. */
    REJECTED(true),
    /** Out of reach, no line of sight, or no path. */
    UNREACHABLE(true),
    MISSING_ITEM(false),
    INVALID_TARGET(false),
    /** Owner disabled, world changed, disconnect, panic or explicit cancel. */
    CANCELLED(false),
    ERROR(false);

    private final boolean retryable;

    FailureReason(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
