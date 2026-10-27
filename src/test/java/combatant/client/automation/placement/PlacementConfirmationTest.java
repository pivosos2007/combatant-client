package combatant.client.automation.placement;

import combatant.client.automation.placement.PlacementConfirmation.Outcome;
import combatant.client.automation.placement.PlacementConfirmation.Ticket;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementConfirmationTest {

    private final PlacementConfirmation confirmation = new PlacementConfirmation();

    @Test
    void acknowledgedAndSatisfiedConfirms() {
        AtomicBoolean placed = new AtomicBoolean(true);
        Ticket ticket = confirmation.track(5, 0, 10, placed::get);

        confirmation.resolve(1);
        assertEquals(Outcome.PENDING, ticket.outcome());

        confirmation.onAcknowledged(5);
        confirmation.resolve(2);
        assertEquals(Outcome.CONFIRMED, ticket.outcome());
        assertEquals(0, confirmation.pendingCount());
    }

    @Test
    void acknowledgedButWorldRevertedIsRejectedImmediately() {
        AtomicBoolean placed = new AtomicBoolean(true);
        Ticket ticket = confirmation.track(7, 0, 10, placed::get);

        // The server undid the predicted block: the client world is back to the old state at the ack.
        placed.set(false);
        confirmation.onAcknowledged(7);
        confirmation.resolve(1);

        assertEquals(Outcome.REJECTED, ticket.outcome());
    }

    @Test
    void withoutAcknowledgementTimesOutOnlyAtDeadline() {
        Ticket ticket = confirmation.track(3, 10, 5, () -> false);

        confirmation.resolve(14);
        assertEquals(Outcome.PENDING, ticket.outcome());
        confirmation.resolve(15);
        assertEquals(Outcome.TIMED_OUT, ticket.outcome());
        assertEquals(0, confirmation.pendingCount());
    }

    @Test
    void timeoutStillConfirmsWhenWorldShowsTheBlock() {
        Ticket ticket = confirmation.track(3, 0, 5, () -> true);

        confirmation.resolve(4);
        assertEquals(Outcome.PENDING, ticket.outcome());
        confirmation.resolve(5);
        assertEquals(Outcome.CONFIRMED, ticket.outcome());
    }

    @Test
    void acknowledgementCoversEarlierSequencesOnly() {
        Ticket first = confirmation.track(1, 0, 10, () -> true);
        Ticket second = confirmation.track(2, 0, 10, () -> true);
        Ticket third = confirmation.track(3, 0, 10, () -> true);

        confirmation.onAcknowledged(2);
        confirmation.resolve(1);

        assertEquals(Outcome.CONFIRMED, first.outcome());
        assertEquals(Outcome.CONFIRMED, second.outcome());
        assertEquals(Outcome.PENDING, third.outcome());
        assertFalse(third.acknowledged());
    }

    @Test
    void outOfOrderAcknowledgementDoesNotUnacknowledgeOrSkipTickets() {
        Ticket early = confirmation.track(4, 0, 10, () -> true);
        Ticket late = confirmation.track(9, 0, 10, () -> false);

        confirmation.onAcknowledged(9);
        confirmation.onAcknowledged(4); // a stale, lower acknowledgement arrives afterwards
        confirmation.resolve(1);

        assertEquals(Outcome.CONFIRMED, early.outcome());
        assertEquals(Outcome.REJECTED, late.outcome());
    }

    @Test
    void unknownSequenceIsNeverAcknowledged() {
        Ticket ticket = confirmation.track(-1, 0, 3, () -> false);

        confirmation.onAcknowledged(Integer.MAX_VALUE);
        confirmation.resolve(1);
        assertFalse(ticket.acknowledged());
        assertEquals(Outcome.PENDING, ticket.outcome());

        confirmation.resolve(3);
        assertEquals(Outcome.TIMED_OUT, ticket.outcome());
    }

    @Test
    void timeoutIsAtLeastOneTick() {
        Ticket ticket = confirmation.track(1, 0, 0, () -> false);

        confirmation.resolve(0);
        assertEquals(Outcome.PENDING, ticket.outcome());
        confirmation.resolve(1);
        assertEquals(Outcome.TIMED_OUT, ticket.outcome());
    }

    @Test
    void clearAbandonsPendingTickets() {
        Ticket ticket = confirmation.track(1, 0, 10, () -> true);

        confirmation.clear();

        assertTrue(ticket.isResolved());
        assertEquals(Outcome.TIMED_OUT, ticket.outcome());
        assertEquals(0, confirmation.pendingCount());
    }

    @Test
    void acknowledgementAfterTimeoutDoesNotChangeVerdict() {
        Ticket ticket = confirmation.track(1, 0, 2, () -> false);
        confirmation.resolve(2);
        assertEquals(Outcome.TIMED_OUT, ticket.outcome());

        confirmation.onAcknowledged(1);
        confirmation.resolve(3);

        assertEquals(Outcome.TIMED_OUT, ticket.outcome());
    }

    @Test
    void pingDerivedTimeoutHasFloorAndGrowsWithLatency() {
        assertEquals(10, PlacementTracker.timeoutForPing(0));
        assertEquals(10, PlacementTracker.timeoutForPing(-50));
        assertEquals(10, PlacementTracker.timeoutForPing(100));
        assertEquals(26, PlacementTracker.timeoutForPing(500));
    }
}
