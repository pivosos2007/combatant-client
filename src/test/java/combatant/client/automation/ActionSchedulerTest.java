package combatant.client.automation;

import combatant.client.automation.TestActions.Hold;
import combatant.client.automation.TestActions.Scripted;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static combatant.client.automation.TestActions.afterTicks;
import static combatant.client.automation.TestActions.fail;
import static combatant.client.automation.TestActions.running;
import static combatant.client.automation.TestActions.succeed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionSchedulerTest {

    private final ActionCoordinator coordinator = new ActionCoordinator();
    private final ActionScheduler scheduler = new ActionScheduler(coordinator);
    private final Object owner = new Object();
    private long tick;

    private AutomationTask submit(String name, int priority, Action root) {
        return scheduler.submit(AutomationTask.builder(owner, name, root).priority(priority).build());
    }

    private void tick(int count) {
        for (int i = 0; i < count; i++) scheduler.tick(++tick);
    }

    @Test
    void higherPriorityTaskWinsContestedResourceEvenWhenSubmittedLater() {
        Hold low = new Hold("low", 100, ActionResource.ROTATION);
        Hold high = new Hold("high", 100, ActionResource.ROTATION);
        AutomationTask lowTask = submit("low", ActionPriority.BUILD, low);
        tick(1);
        submit("high", ActionPriority.COMBAT, high);
        tick(1);

        assertSame(high, coordinator.holder(ActionResource.ROTATION));
        assertEquals(1, low.revoked);
        tick(1);
        assertEquals(ActionStatus.FAILED, lowTask.status());
        assertEquals(FailureReason.PREEMPTED, lowTask.result().reason());
    }

    @Test
    void lowerPriorityTaskWaitsWhileResourceIsHeld() {
        Hold high = new Hold("high", 2, ActionResource.HOTBAR);
        Hold low = new Hold("low", 1, ActionResource.HOTBAR);
        submit("low", ActionPriority.BUILD, low);
        submit("high", ActionPriority.COMBAT, high);
        tick(1);
        assertEquals(List.of("wait"), low.log);
        tick(2);
        assertEquals(ActionStatus.SUCCEEDED, high.status());
        assertEquals(ActionStatus.SUCCEEDED, low.status());
        assertNull(coordinator.holder(ActionResource.HOTBAR));
    }

    @Test
    void stoppedOwnerCancelsTaskAndReleasesLeases() {
        Set<Object> alive = new HashSet<>(Set.of(owner));
        scheduler.setOwnerAlive(alive::contains);
        Hold hold = new Hold("hold", 100, ActionResource.MOVEMENT);
        List<ActionResult> finished = new ArrayList<>();
        scheduler.submit(AutomationTask.builder(owner, "walk", hold)
                .onFinish(task -> finished.add(task.result())).build());
        tick(1);
        assertSame(hold, coordinator.holder(ActionResource.MOVEMENT));

        alive.clear();
        tick(1);
        assertNull(coordinator.holder(ActionResource.MOVEMENT));
        assertEquals(1, finished.size());
        assertEquals(ActionStatus.CANCELLED, finished.getFirst().status());
        assertTrue(hold.log.contains("end:CANCELLED"));
        assertFalse(scheduler.hasTasks(owner));
    }

    @Test
    void deferredWorkRunsOnlyForLiveActions() {
        List<String> ran = new ArrayList<>();
        AbstractAction deferring = new AbstractAction("defer") {
            @Override
            protected ActionResult onTick(ActionContext ctx) {
                ctx.defer(ExecutionPhase.AFTER_ROTATION, this, () -> ran.add("tick" + ctx.tick()));
                return ActionResult.RUNNING;
            }
        };
        AutomationTask task = submit("defer", ActionPriority.BUILD, deferring);
        tick(1);
        scheduler.runDeferred(ExecutionPhase.AFTER_ROTATION);
        assertEquals(List.of("tick1"), ran);

        tick(1);
        task.cancel("test");
        scheduler.runDeferred(ExecutionPhase.AFTER_ROTATION);
        assertEquals(List.of("tick1"), ran, "work queued before cancel must be dropped");
    }

    @Test
    void cancelAllRevokesEverythingAndClearsMovementIntent() {
        AbstractAction walker = new AbstractAction("walk") {
            @Override
            protected ActionResult onTick(ActionContext ctx) {
                if (acquire(ctx, ActionResource.MOVEMENT)) ctx.setMovement(this, true, false, false, false);
                return ActionResult.RUNNING;
            }
        };
        submit("walk", ActionPriority.UTILITY, walker);
        tick(1);
        assertTrue(scheduler.movement().hasMovement(coordinator));

        scheduler.cancelAll("world change");
        assertFalse(scheduler.movement().hasMovement(coordinator));
        assertNull(coordinator.holder(ActionResource.MOVEMENT));
        assertTrue(scheduler.tasks().isEmpty());
    }

    @Test
    void movementIntentIgnoresWritersWithoutLease() {
        AbstractAction intruder = new AbstractAction("intruder") {
            @Override
            protected ActionResult onTick(ActionContext ctx) {
                ctx.setMovement(this, true, false, false, false);
                ctx.setJump(this, true);
                return ActionResult.RUNNING;
            }
        };
        submit("intruder", ActionPriority.SAFETY, intruder);
        tick(1);
        assertFalse(scheduler.movement().hasMovement(coordinator));
        assertFalse(scheduler.movement().hasJump(coordinator));
    }

    @Test
    void manualOverrideFailsTaskWithNonRetryableReason() {
        Hold walk = new Hold("walk", 100, ActionResource.MOVEMENT);
        AutomationTask task = submit("walk", ActionPriority.UTILITY,
                Actions.retry("retry", () -> walk, 5, 0));
        tick(1);
        coordinator.revokeForManualInput(ActionResource.MOVEMENT);
        tick(1);
        assertEquals(FailureReason.MANUAL_OVERRIDE, task.result().reason());
    }

    @Test
    void exceptionBecomesErrorAndReleasesLeases() {
        AbstractAction broken = new AbstractAction("broken") {
            @Override
            protected ActionResult onTick(ActionContext ctx) {
                acquire(ctx, ActionResource.INTERACTION);
                throw new IllegalStateException("boom");
            }
        };
        AutomationTask task = submit("broken", ActionPriority.BUILD, broken);
        tick(1);
        assertEquals(FailureReason.ERROR, task.result().reason());
        assertNull(coordinator.holder(ActionResource.INTERACTION));
    }

    @Test
    void taskSubmittedFromFinishCallbackStartsNextTick() {
        Scripted follow = succeed("follow");
        scheduler.submit(AutomationTask.builder(owner, "first", succeed("first"))
                .onFinish(task -> submit("follow", ActionPriority.BUILD, follow)).build());
        tick(1);
        assertEquals(0, follow.ticks);
        tick(1);
        assertEquals(ActionStatus.SUCCEEDED, follow.status());
    }

    @Test
    void sequenceRunsInstantStepsInOneTickAndStopsOnFailure() {
        Scripted a = succeed("a");
        Scripted b = succeed("b");
        Scripted c = afterTicks("c", 1, ActionResult.SUCCESS);
        AutomationTask ok = submit("seq", ActionPriority.BUILD, Actions.sequence("seq", a, b, c));
        tick(1);
        assertEquals(1, b.ticks);
        assertEquals(ActionStatus.RUNNING, ok.status());
        tick(1);
        assertEquals(ActionStatus.SUCCEEDED, ok.status());

        Scripted after = succeed("after");
        AutomationTask bad = submit("bad", ActionPriority.BUILD,
                Actions.sequence("bad", fail("x", FailureReason.UNREACHABLE), after));
        tick(1);
        assertEquals(FailureReason.UNREACHABLE, bad.result().reason());
        assertEquals(0, after.ticks);
    }

    @Test
    void selectorFallsBackToNextAlternative() {
        AutomationTask task = submit("sel", ActionPriority.BUILD, Actions.selector("sel",
                fail("airplace", FailureReason.UNREACHABLE), succeed("support")));
        tick(1);
        assertEquals(ActionStatus.SUCCEEDED, task.status());

        AutomationTask none = submit("none", ActionPriority.BUILD, Actions.selector("none",
                fail("a", FailureReason.UNREACHABLE), fail("b", FailureReason.MISSING_ITEM)));
        tick(1);
        assertEquals(FailureReason.MISSING_ITEM, none.result().reason());
    }

    @Test
    void selectorStopsOnManualOverrideCancelAndErrorInsteadOfTryingAlternatives() {
        for (FailureReason reason : new FailureReason[]{FailureReason.MANUAL_OVERRIDE, FailureReason.CANCELLED,
                FailureReason.ERROR}) {
            Scripted next = succeed("next");
            AutomationTask task = submit("sel", ActionPriority.BUILD,
                    Actions.selector("sel", fail("first", reason), next));
            tick(1);
            assertEquals(reason, task.result().reason());
            assertEquals(0, next.ticks, "alternative must not start after " + reason);
        }
    }

    @Test
    void parallelRequireAllCancelsSiblingsOnFailure() {
        Scripted slow = running("slow");
        AutomationTask task = submit("par", ActionPriority.BUILD,
                Actions.all("par", slow, afterTicks("fails", 1, ActionResult.failure(FailureReason.REJECTED, "x"))));
        tick(2);
        assertEquals(FailureReason.REJECTED, task.result().reason());
        assertTrue(slow.cancelled);
    }

    @Test
    void parallelRequireOneSucceedsOnFirstSuccess() {
        Scripted slow = running("slow");
        AutomationTask task = submit("race", ActionPriority.BUILD,
                Actions.any("race", slow, afterTicks("fast", 1, ActionResult.SUCCESS)));
        tick(2);
        assertEquals(ActionStatus.SUCCEEDED, task.status());
        assertTrue(slow.cancelled);
    }

    @Test
    void retryRetriesRetryableFailuresWithBackoff() {
        int[] attempt = {0};
        TestActions.Factory factory = new TestActions.Factory(() -> ++attempt[0] < 3
                ? fail("place", FailureReason.REJECTED)
                : succeed("place"));
        AutomationTask task = submit("retry", ActionPriority.BUILD, Actions.retry("retry", factory, 5, 2));
        tick(1);
        assertEquals(1, factory.created);
        tick(2);
        assertEquals(1, factory.created, "two idle ticks before the retry");
        tick(1);
        assertEquals(2, factory.created);
        tick(2);
        assertEquals(2, factory.created);
        tick(1);
        assertEquals(3, factory.created);
        assertEquals(ActionStatus.SUCCEEDED, task.status());
    }

    @Test
    void retryStopsOnNonRetryableFailureAndOnAttemptLimit() {
        TestActions.Factory missing = new TestActions.Factory(() -> fail("x", FailureReason.MISSING_ITEM));
        AutomationTask a = submit("a", ActionPriority.BUILD, Actions.retry("a", missing, 5, 0));
        TestActions.Factory rejected = new TestActions.Factory(() -> fail("y", FailureReason.REJECTED));
        AutomationTask b = submit("b", ActionPriority.BUILD, Actions.retry("b", rejected, 2, 0));
        tick(5);
        assertEquals(1, missing.created);
        assertEquals(FailureReason.MISSING_ITEM, a.result().reason());
        assertEquals(2, rejected.created);
        assertEquals(FailureReason.REJECTED, b.result().reason());
    }

    @Test
    void timeoutAllowsExactlyTheGivenTicksThenCancelsChild() {
        Scripted child = running("child");
        AutomationTask task = submit("timeout", ActionPriority.BUILD, Actions.timeout(child, 3));
        tick(3);
        assertEquals(ActionStatus.RUNNING, task.status());
        assertEquals(3, child.ticks);
        tick(1);
        assertEquals(FailureReason.TIMEOUT, task.result().reason());
        assertTrue(child.cancelled);
    }

    @Test
    void guardStopsChildWhenPreconditionBreaks() {
        boolean[] valid = {true};
        Scripted child = running("child");
        AutomationTask task = submit("guard", ActionPriority.BUILD, Actions.guard("target valid", () -> valid[0], child));
        tick(2);
        valid[0] = false;
        tick(1);
        assertEquals(FailureReason.PRECONDITION, task.result().reason());
        assertTrue(child.cancelled);
    }

    @Test
    void cancellingCompositeReachesRunningLeafAndReleasesItsLease() {
        Hold leaf = new Hold("leaf", 100, ActionResource.ROTATION);
        AutomationTask task = submit("tree", ActionPriority.BUILD,
                Actions.sequence("tree", succeed("plan"), Actions.timeout(leaf, 50)));
        tick(2);
        assertSame(leaf, coordinator.holder(ActionResource.ROTATION));
        task.cancel("disabled");
        assertEquals(ActionStatus.CANCELLED, leaf.status());
        assertNull(coordinator.holder(ActionResource.ROTATION));
    }
}
