/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.placement;

import java.util.ArrayList;
import java.util.function.BooleanSupplier;

/**
 * Decides whether a sent block interaction actually took effect on the server.
 *
 * <p>The client predicts placements locally, so the world shows the block right after the click.
 * Only after the server acknowledges the interaction's prediction sequence
 * ({@code ClientboundBlockChangedAckPacket}) does the client world hold the server's verdict: then
 * the expected state is confirmed, anything else is a rejection. Without an acknowledgement within
 * the timeout the current world state decides (lag, proxies that strip acks).
 *
 * <p>Free of Minecraft types; {@link PlacementTracker} feeds it packets and world checks.
 * Client-thread only.
 */
public final class PlacementConfirmation {

    public enum Outcome {
        PENDING,
        CONFIRMED,
        REJECTED,
        TIMED_OUT
    }

    public static final class Ticket {
        private final int sequence;
        private final long sentTick;
        private final int timeoutTicks;
        private final BooleanSupplier satisfied;
        private boolean acknowledged;
        private Outcome outcome = Outcome.PENDING;

        private Ticket(int sequence, long sentTick, int timeoutTicks, BooleanSupplier satisfied) {
            this.sequence = sequence;
            this.sentTick = sentTick;
            this.timeoutTicks = timeoutTicks;
            this.satisfied = satisfied;
        }

        public Outcome outcome() {
            return outcome;
        }

        public boolean isResolved() {
            return outcome != Outcome.PENDING;
        }

        public boolean acknowledged() {
            return acknowledged;
        }

        public int sequence() {
            return sequence;
        }

        /** Stops waiting; the ticket is dropped on the next resolve. */
        public void abandon() {
            if (outcome == Outcome.PENDING) outcome = Outcome.TIMED_OUT;
        }
    }

    private final ArrayList<Ticket> pending = new ArrayList<>();

    /**
     * @param sequence  prediction sequence of the sent packet, or a negative value when unknown
     * @param satisfied true once the world shows the intended result
     */
    public Ticket track(int sequence, long sentTick, int timeoutTicks, BooleanSupplier satisfied) {
        Ticket ticket = new Ticket(sequence, sentTick, Math.max(1, timeoutTicks), satisfied);
        pending.add(ticket);
        return ticket;
    }

    /** The server processed every interaction up to {@code sequence}; it handles them in order. */
    public void onAcknowledged(int sequence) {
        for (Ticket ticket : pending) {
            if (ticket.sequence >= 0 && ticket.sequence <= sequence) ticket.acknowledged = true;
        }
    }

    /** Resolves tickets against the current world. Call once per tick after packets were handled. */
    public void resolve(long tick) {
        for (int i = pending.size() - 1; i >= 0; i--) {
            Ticket ticket = pending.get(i);
            if (ticket.outcome == Outcome.PENDING) {
                if (ticket.acknowledged) {
                    ticket.outcome = ticket.satisfied.getAsBoolean() ? Outcome.CONFIRMED : Outcome.REJECTED;
                } else if (tick - ticket.sentTick >= ticket.timeoutTicks) {
                    ticket.outcome = ticket.satisfied.getAsBoolean() ? Outcome.CONFIRMED : Outcome.TIMED_OUT;
                }
            }
            if (ticket.outcome != Outcome.PENDING) pending.remove(i);
        }
    }

    public int pendingCount() {
        return pending.size();
    }

    /** World changed: every pending ticket is moot. */
    public void clear() {
        for (Ticket ticket : pending) ticket.abandon();
        pending.clear();
    }
}
