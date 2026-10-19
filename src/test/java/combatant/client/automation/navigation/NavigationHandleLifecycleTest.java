/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.navigation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationHandleLifecycleTest {

    private static final class Scripted extends AbstractNavigationHandle {
        int steps;
        int finished;
        Runnable onStep = () -> { };

        Scripted(NavigationRequest request) {
            super(request);
        }

        @Override
        protected void step(PlayerSample player) {
            steps++;
            intent.steer = true;
            onStep.run();
        }

        @Override
        protected void onFinished(NavigationResult result) {
            finished++;
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

        void succeedNow() {
            succeed();
        }

        void failNow() {
            fail(NavigationFailure.NO_PATH, "x");
        }
    }

    private final PlayerSample player = new PlayerSample();

    @Test
    void cancelIsIdempotentAndFinishesOnce() {
        Scripted handle = new Scripted(NavigationRequest.block(0, 0, 0));
        handle.tick(player);
        handle.cancel();
        handle.cancel();
        assertEquals(NavigationStatus.CANCELLED, handle.status());
        assertEquals(1, handle.finished);
        assertFalse(handle.intent().steer, "a finished handle asks for nothing");
    }

    @Test
    void terminalStateNeverChangesAndStopsTicking() {
        Scripted handle = new Scripted(NavigationRequest.block(0, 0, 0));
        handle.tick(player);
        handle.succeedNow();
        handle.cancel();
        handle.failNow();
        handle.tick(player);
        assertEquals(NavigationStatus.SUCCEEDED, handle.status());
        assertSame(NavigationResult.SUCCESS, handle.result());
        assertEquals(1, handle.steps);
        assertEquals(1, handle.finished);
    }

    @Test
    void cancelledBeforeFirstTickNeverSteps() {
        Scripted handle = new Scripted(NavigationRequest.block(0, 0, 0));
        assertNull(handle.result());
        handle.cancel();
        handle.tick(player);
        assertEquals(0, handle.steps);
        assertTrue(handle.isDone());
    }

    @Test
    void timeoutFailsOnTheTickAfterTheLimit() {
        NavigationRequest request = NavigationRequest.block(0, 0, 0)
                .withOptions(NavigationRequest.Options.DEFAULT.withTimeout(3));
        Scripted handle = new Scripted(request);
        for (int i = 0; i < 3; i++) handle.tick(player);
        assertEquals(NavigationStatus.RUNNING, handle.status());
        handle.tick(player);
        assertEquals(NavigationStatus.FAILED, handle.status());
        assertEquals(NavigationFailure.TIMEOUT, handle.result().failure());
        assertEquals(3, handle.steps);
    }

    @Test
    void stepExceptionBecomesErrorFailure() {
        Scripted handle = new Scripted(NavigationRequest.block(0, 0, 0));
        handle.onStep = () -> {
            throw new IllegalStateException("boom");
        };
        handle.tick(player);
        assertEquals(NavigationFailure.ERROR, handle.result().failure());
        assertTrue(handle.result().detail().contains("boom"));
        assertEquals(1, handle.finished);
    }

    @Test
    void failedHandleIsTerminalFromTheStart() {
        FailedNavigationHandle handle = new FailedNavigationHandle(NavigationRequest.block(0, 0, 0),
                NavigationFailure.UNAVAILABLE, "no mod");
        assertEquals(NavigationStatus.FAILED, handle.status());
        handle.cancel();
        assertEquals(NavigationStatus.FAILED, handle.status());
    }

    @Test
    void requestGoalPredicates() {
        assertTrue(NavigationRequest.block(3, 64, -2).satisfiedBy(3.5, 64.0, -1.5, 1.62));
        assertFalse(NavigationRequest.block(3, 64, -2).satisfiedBy(4.5, 64.0, -1.5, 1.62));
        assertTrue(NavigationRequest.near(0, 0, 0, 2).satisfiedBy(0.5, 1.0, 2.0, 1.62));
        assertFalse(NavigationRequest.near(0, 0, 0, 2).satisfiedBy(0.5, 0.0, 4.0, 1.62));
        // Reach is measured from the eyes: 4.4 above the block centre is out, feet at the block's level is in.
        assertTrue(NavigationRequest.reach(0, 5, 0, 4.5).satisfiedBy(0.5, 2.0, 0.5, 1.62));
        assertFalse(NavigationRequest.reach(0, 5, 0, 3.0).satisfiedBy(0.5, 0.0, 0.5, 1.62));
    }
}
