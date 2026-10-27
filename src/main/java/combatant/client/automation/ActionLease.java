/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation;

/**
 * Ownership of one {@link ActionResource} by one {@link LeaseHolder}. Leases are created and ended
 * only by the {@link ActionCoordinator}; holders observe loss through
 * {@link LeaseHolder#onLeaseRevoked}.
 */
public final class ActionLease {

    public enum RevokeReason {
        /** A higher priority holder took the resource. */
        PREEMPTED,
        /** The player used the resource directly and the holder yields to manual input. */
        MANUAL_INPUT,
        /** World change, disconnect, panic or runtime shutdown. */
        SHUTDOWN
    }

    private final ActionResource resource;
    private final LeaseHolder holder;
    private final int priority;
    private final boolean yieldsToManualInput;
    private final long acquiredTick;
    private boolean active = true;
    private RevokeReason revokeReason;

    ActionLease(ActionResource resource, LeaseHolder holder, int priority, boolean yieldsToManualInput, long acquiredTick) {
        this.resource = resource;
        this.holder = holder;
        this.priority = priority;
        this.yieldsToManualInput = yieldsToManualInput;
        this.acquiredTick = acquiredTick;
    }

    public ActionResource resource() {
        return resource;
    }

    public LeaseHolder holder() {
        return holder;
    }

    public int priority() {
        return priority;
    }

    public boolean yieldsToManualInput() {
        return yieldsToManualInput;
    }

    public long acquiredTick() {
        return acquiredTick;
    }

    public boolean isActive() {
        return active;
    }

    /** Null while active or after a voluntary release. */
    public RevokeReason revokeReason() {
        return revokeReason;
    }

    void end(RevokeReason reason) {
        active = false;
        revokeReason = reason;
    }
}
