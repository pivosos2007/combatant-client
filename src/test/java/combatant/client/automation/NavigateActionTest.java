/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation;

import combatant.client.automation.actions.NavigateAction;
import combatant.client.automation.navigation.AbstractNavigationHandle;
import combatant.client.automation.navigation.NavigationFailure;
import combatant.client.automation.navigation.NavigationHandle;
import combatant.client.automation.navigation.NavigationProvider;
import combatant.client.automation.navigation.NavigationRequest;
import combatant.client.automation.navigation.PlayerSample;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigateActionTest {

    private final ActionCoordinator coordinator = new ActionCoordinator();
    private final ActionScheduler scheduler = new ActionScheduler(coordinator);
    private final Object owner = new Object();
    private long tick;

    /** Handle steering east at a fixed aim; optionally a provider-driven one. */
    private static final class ScriptedHandle extends AbstractNavigationHandle {
        final boolean drives;
        boolean sprint;
        boolean jump;
        int cancelledCalls;

        ScriptedHandle(NavigationRequest request, boolean drives) {
            super(request);
            this.drives = drives;
        }

        @Override
        protected void step(PlayerSample p) {
            intent.steer = true;
            intent.aimX = p.x + 5.0;
            intent.aimZ = p.z;
            intent.sprint = sprint;
            intent.jump = jump;
        }

        @Override
        protected void onFinished(combatant.client.automation.navigation.NavigationResult result) {
            cancelledCalls++;
        }

        @Override
        public boolean drivesPlayer() {
            return drives;
        }

        @Override
        public double remainingDistance() {
            return 5.0;
        }

        @Override
        public int expandedNodes() {
            return -1;
        }
    }

    private static final class Env implements NavigateAction.Environment {
        float yaw;
        boolean canSprint = true;

        @Override
        public boolean sample(PlayerSample out) {
            out.set(0.5, 0.0, 0.5, 0, 0, true, false);
            return true;
        }

        @Override
        public float movementYaw() {
            return yaw;
        }

        @Override
        public boolean canSprint() {
            return canSprint;
        }
    }

    private ScriptedHandle handle;

    private NavigationProvider provider(boolean drives) {
        return new NavigationProvider() {
            @Override
            public String id() {
                return "test";
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public NavigationHandle navigate(NavigationRequest request) {
                handle = new ScriptedHandle(request, drives);
                return handle;
            }
        };
    }

    private AutomationTask submit(Action action) {
        return scheduler.submit(AutomationTask.builder(owner, "nav", action).priority(ActionPriority.BUILD).build());
    }

    private void tick() {
        scheduler.tick(++tick);
    }

    @Test
    void intentBecomesKeysRelativeToTheMovementYaw() {
        Env env = new Env();
        env.yaw = 0f; // facing +Z: east (+X) is to the left in Minecraft's yaw convention
        submit(new NavigateAction(NavigationRequest.block(9, 0, 0), provider(false), env));
        tick();
        MovementIntent keys = scheduler.movement();
        assertTrue(keys.hasMovement(coordinator));
        assertTrue(keys.left());
        assertFalse(keys.forward() || keys.right() || keys.backward());

        env.yaw = -90f; // facing east: east is straight ahead
        tick();
        assertTrue(keys.forward());
        assertFalse(keys.left() || keys.right());
    }

    @Test
    void sprintOnlyWhenMovingStraightAheadOnTheGroundAndAllowed() {
        Env env = new Env();
        env.yaw = -90f;
        submit(new NavigateAction(NavigationRequest.block(9, 0, 0), provider(false), env));
        tick();
        handle.sprint = true;
        tick();
        assertTrue(scheduler.movement().sprint());
        env.canSprint = false;
        tick();
        assertFalse(scheduler.movement().sprint());
        env.canSprint = true;
        handle.jump = true;
        tick();
        assertTrue(scheduler.movement().jump());
        assertFalse(scheduler.movement().sprint(), "no sprint while jumping");
        env.yaw = 0f;
        handle.jump = false;
        tick();
        assertFalse(scheduler.movement().sprint(), "no sprint while strafing");
    }

    @Test
    void holdsMovementJumpAndSprintAndReleasesThemOnCancel() {
        NavigateAction action = new NavigateAction(NavigationRequest.block(9, 0, 0), provider(false), new Env());
        AutomationTask task = submit(action);
        tick();
        assertSame(action, coordinator.holder(ActionResource.MOVEMENT));
        assertSame(action, coordinator.holder(ActionResource.JUMP));
        assertSame(action, coordinator.holder(ActionResource.SPRINT));
        scheduler.cancel(task, "stop");
        assertNull(coordinator.holder(ActionResource.MOVEMENT));
        assertTrue(handle.isDone(), "ending the action cancels the handle");
    }

    @Test
    void providerDrivenHandleKeepsLeasesButWritesNoKeys() {
        NavigateAction action = new NavigateAction(NavigationRequest.block(9, 0, 0), provider(true), new Env());
        submit(action);
        tick();
        tick();
        assertSame(action, coordinator.holder(ActionResource.MOVEMENT));
        assertFalse(scheduler.movement().hasMovement(coordinator));
        assertFalse(scheduler.movement().forward() || scheduler.movement().left());
    }

    @Test
    void preemptionCancelsTheProviderImmediately() {
        NavigateAction action = new NavigateAction(NavigationRequest.block(9, 0, 0), provider(true), new Env());
        AutomationTask task = submit(action);
        tick();
        scheduler.submit(AutomationTask.builder(owner, "evade", new TestActions.Hold("evade", 50, ActionResource.MOVEMENT))
                .priority(ActionPriority.EVASION).build());
        tick();
        assertTrue(handle.isDone());
        tick();
        assertEquals(FailureReason.PREEMPTED, task.result().reason());
    }

    @Test
    void waitsForBusyResourcesBeforeCreatingTheHandle() {
        TestActions.Hold blocker = new TestActions.Hold("blocker", 2, ActionResource.MOVEMENT);
        scheduler.submit(AutomationTask.builder(owner, "blocker", blocker).priority(ActionPriority.COMBAT).build());
        NavigateAction action = new NavigateAction(NavigationRequest.block(9, 0, 0), provider(false), new Env());
        submit(action);
        tick();
        assertNull(handle);
        tick();
        tick();
        assertTrue(handle != null && !handle.isDone());
    }

    @Test
    void handleFailureMapsToActionFailureReason() {
        NavigationProvider failing = new NavigationProvider() {
            @Override
            public String id() {
                return "failing";
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public NavigationHandle navigate(NavigationRequest request) {
                return new combatant.client.automation.navigation.FailedNavigationHandle(request, NavigationFailure.NO_PATH, "nope");
            }
        };
        AutomationTask task = submit(new NavigateAction(NavigationRequest.block(9, 0, 0), failing, new Env()));
        tick();
        assertEquals(ActionStatus.FAILED, task.status());
        assertEquals(FailureReason.UNREACHABLE, task.result().reason());
        assertNull(coordinator.holder(ActionResource.MOVEMENT));
    }
}
