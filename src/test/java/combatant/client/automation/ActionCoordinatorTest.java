package combatant.client.automation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionCoordinatorTest {

    private static final class Holder implements LeaseHolder {
        final List<String> revocations = new ArrayList<>();
        ActionCoordinator releaseOnRevoke;

        @Override
        public void onLeaseRevoked(ActionLease lease, ActionLease.RevokeReason reason) {
            revocations.add(lease.resource() + ":" + reason);
            if (releaseOnRevoke != null) releaseOnRevoke.releaseAll(this);
        }
    }

    private final ActionCoordinator coordinator = new ActionCoordinator();

    @Test
    void higherPriorityPreemptsAndNotifiesHolder() {
        Holder low = new Holder();
        Holder high = new Holder();
        assertTrue(coordinator.tryAcquire(low, 10, Set.of(), 0, ActionResource.ROTATION));
        assertTrue(coordinator.tryAcquire(high, 35, Set.of(), 1, ActionResource.ROTATION));

        assertSame(high, coordinator.holder(ActionResource.ROTATION));
        assertEquals(List.of("ROTATION:PREEMPTED"), low.revocations);
    }

    @Test
    void equalPriorityKeepsCurrentHolder() {
        Holder first = new Holder();
        Holder second = new Holder();
        assertTrue(coordinator.tryAcquire(first, 10, Set.of(), 0, ActionResource.HOTBAR));
        assertFalse(coordinator.tryAcquire(second, 10, Set.of(), 0, ActionResource.HOTBAR));
        assertSame(first, coordinator.holder(ActionResource.HOTBAR));
        assertTrue(first.revocations.isEmpty());
    }

    @Test
    void acquisitionIsAllOrNothing() {
        Holder busy = new Holder();
        Holder requester = new Holder();
        coordinator.tryAcquire(busy, 50, Set.of(), 0, ActionResource.HOTBAR);

        assertFalse(coordinator.tryAcquire(requester, 20, Set.of(), 0, ActionResource.ROTATION, ActionResource.HOTBAR));
        assertNull(coordinator.holder(ActionResource.ROTATION), "free resource must not be taken by a failed request");
    }

    @Test
    void failedMultiRequestDoesNotPreemptAnything() {
        Holder lowRotation = new Holder();
        Holder highHotbar = new Holder();
        Holder requester = new Holder();
        coordinator.tryAcquire(lowRotation, 5, Set.of(), 0, ActionResource.ROTATION);
        coordinator.tryAcquire(highHotbar, 50, Set.of(), 0, ActionResource.HOTBAR);

        assertFalse(coordinator.tryAcquire(requester, 20, Set.of(), 0, ActionResource.ROTATION, ActionResource.HOTBAR));
        assertSame(lowRotation, coordinator.holder(ActionResource.ROTATION));
        assertTrue(lowRotation.revocations.isEmpty());
    }

    @Test
    void externallyHeldResourceIsRefusedRegardlessOfPriority() {
        coordinator.setProbe((resource, requester) -> resource == ActionResource.HOTBAR);
        Holder requester = new Holder();
        assertFalse(coordinator.tryAcquire(requester, 1000, Set.of(), 0, ActionResource.HOTBAR));
        assertTrue(coordinator.tryAcquire(requester, 1000, Set.of(), 0, ActionResource.ROTATION));
    }

    @Test
    void probeIsNotConsultedWhileARuntimeHolderOwnsTheResource() {
        Holder low = new Holder();
        Holder high = new Holder();
        coordinator.tryAcquire(low, 10, Set.of(), 0, ActionResource.HOTBAR);
        // The low holder's own backend lease would look external to anyone else.
        coordinator.setProbe((resource, requester) -> requester != low);
        assertTrue(coordinator.tryAcquire(high, 20, Set.of(), 0, ActionResource.HOTBAR));
    }

    @Test
    void manualInputRevokesOnlyYieldingLeases() {
        Holder walker = new Holder();
        Holder aimer = new Holder();
        coordinator.tryAcquire(walker, 20, EnumSet.of(ActionResource.MOVEMENT), 0, ActionResource.MOVEMENT);
        coordinator.tryAcquire(aimer, 20, EnumSet.of(ActionResource.MOVEMENT), 0, ActionResource.ROTATION);

        assertTrue(coordinator.revokeForManualInput(ActionResource.MOVEMENT));
        assertFalse(coordinator.revokeForManualInput(ActionResource.ROTATION));
        assertEquals(List.of("MOVEMENT:MANUAL_INPUT"), walker.revocations);
        assertSame(aimer, coordinator.holder(ActionResource.ROTATION));
    }

    @Test
    void preemptedHolderReleasingEverythingFreesItsOtherResources() {
        Holder low = new Holder();
        low.releaseOnRevoke = coordinator;
        Holder high = new Holder();
        coordinator.tryAcquire(low, 10, Set.of(), 0, ActionResource.ROTATION, ActionResource.HOTBAR);
        assertTrue(coordinator.tryAcquire(high, 20, Set.of(), 0, ActionResource.ROTATION));

        assertNull(coordinator.holder(ActionResource.HOTBAR));
        assertEquals(List.of("ROTATION:PREEMPTED"), low.revocations);
    }

    @Test
    void voluntaryReleaseDoesNotNotify() {
        Holder holder = new Holder();
        coordinator.tryAcquire(holder, 10, Set.of(), 0, ActionResource.SNEAK);
        ActionLease lease = coordinator.lease(ActionResource.SNEAK);
        coordinator.releaseAll(holder);

        assertFalse(lease.isActive());
        assertNull(lease.revokeReason());
        assertTrue(holder.revocations.isEmpty());
    }
}
