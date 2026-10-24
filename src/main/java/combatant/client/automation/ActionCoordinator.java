/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation;

import java.util.Set;

/**
 * Single owner table for {@link ActionResource}s.
 *
 * <ul>
 *   <li>Acquisition is all-or-nothing: either every requested resource is granted or none is.</li>
 *   <li>A request preempts a current holder only with a strictly higher priority; ties keep the
 *       current holder so equal-priority tasks do not thrash.</li>
 *   <li>Resources reported busy by the {@link ResourceProbe} (legacy, non-preemptible users) are
 *       never granted.</li>
 *   <li>Revoked holders are notified synchronously, after the table no longer lists them.</li>
 * </ul>
 *
 * Client-thread only.
 */
public final class ActionCoordinator {

    private static final ActionResource[] RESOURCES = ActionResource.VALUES;

    private final ActionLease[] leases = new ActionLease[RESOURCES.length];
    private ResourceProbe probe = ResourceProbe.NONE;

    public void setProbe(ResourceProbe probe) {
        this.probe = probe != null ? probe : ResourceProbe.NONE;
    }

    /**
     * Grants all {@code resources} to {@code holder} or nothing.
     *
     * @param manualYield resources the holder gives up when the player uses them directly
     * @return true when the holder owns every requested resource afterwards
     */
    public boolean tryAcquire(LeaseHolder holder, int priority, Set<ActionResource> manualYield, long tick,
                              ActionResource... resources) {
        if (holder == null || resources == null) return false;
        for (ActionResource resource : resources) {
            ActionLease current = leases[resource.ordinal()];
            if (current == null) {
                if (probe.externallyHeld(resource, holder)) return false;
            } else if (current.holder() != holder && current.priority() >= priority) {
                return false;
            }
        }
        for (ActionResource resource : resources) {
            ActionLease current = leases[resource.ordinal()];
            if (current != null && current.holder() == holder) continue;
            if (current != null) revoke(current, ActionLease.RevokeReason.PREEMPTED);
            boolean yields = manualYield != null && manualYield.contains(resource);
            leases[resource.ordinal()] = new ActionLease(resource, holder, priority, yields, tick);
        }
        return true;
    }

    /** True when {@code holder} could take every resource right now without waiting. */
    public boolean canAcquire(LeaseHolder holder, int priority, ActionResource... resources) {
        if (holder == null || resources == null) return false;
        for (ActionResource resource : resources) {
            ActionLease current = leases[resource.ordinal()];
            if (current == null) {
                if (probe.externallyHeld(resource, holder)) return false;
            } else if (current.holder() != holder && current.priority() >= priority) {
                return false;
            }
        }
        return true;
    }

    public boolean holds(LeaseHolder holder, ActionResource resource) {
        ActionLease lease = leases[resource.ordinal()];
        return lease != null && lease.holder() == holder;
    }

    public ActionLease lease(ActionResource resource) {
        return leases[resource.ordinal()];
    }

    public LeaseHolder holder(ActionResource resource) {
        ActionLease lease = leases[resource.ordinal()];
        return lease != null ? lease.holder() : null;
    }

    /** Voluntary release; the holder is not notified. */
    public void release(LeaseHolder holder, ActionResource resource) {
        ActionLease lease = leases[resource.ordinal()];
        if (lease == null || lease.holder() != holder) return;
        leases[resource.ordinal()] = null;
        lease.end(null);
    }

    /** Voluntary release of everything the holder owns; the holder is not notified. */
    public void releaseAll(LeaseHolder holder) {
        if (holder == null) return;
        for (int i = 0; i < leases.length; i++) {
            ActionLease lease = leases[i];
            if (lease != null && lease.holder() == holder) {
                leases[i] = null;
                lease.end(null);
            }
        }
    }

    /**
     * The player used {@code resource}; revoke it if its holder yields to manual input.
     *
     * @return true when a lease was revoked
     */
    public boolean revokeForManualInput(ActionResource resource) {
        ActionLease lease = leases[resource.ordinal()];
        if (lease == null || !lease.yieldsToManualInput()) return false;
        revoke(lease, ActionLease.RevokeReason.MANUAL_INPUT);
        return true;
    }

    /** Revokes every lease, notifying holders. */
    public void revokeAll(ActionLease.RevokeReason reason) {
        for (ActionLease lease : leases) {
            if (lease != null) revoke(lease, reason);
        }
    }

    private void revoke(ActionLease lease, ActionLease.RevokeReason reason) {
        int index = lease.resource().ordinal();
        if (leases[index] != lease) return;
        leases[index] = null;
        lease.end(reason);
        lease.holder().onLeaseRevoked(lease, reason);
    }
}
