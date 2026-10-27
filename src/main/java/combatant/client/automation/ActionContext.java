/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation;

/**
 * Per-tick view handed to actions. One instance is rebound to each task while the scheduler ticks
 * it, so actions must not keep a reference across ticks.
 */
public final class ActionContext {
    private final ActionScheduler scheduler;
    private AutomationTask task;

    ActionContext(ActionScheduler scheduler) {
        this.scheduler = scheduler;
    }

    void bind(AutomationTask task) {
        this.task = task;
    }

    public long tick() {
        return scheduler.currentTick();
    }

    public AutomationTask task() {
        return task;
    }

    public Object owner() {
        return task.owner();
    }

    public int priority() {
        return task.priority();
    }

    public ActionCoordinator coordinator() {
        return scheduler.coordinator();
    }

    /** Runs {@code work} later in this tick if {@code action} is still running by then. */
    public void defer(ExecutionPhase phase, AbstractAction action, Runnable work) {
        scheduler.defer(phase, action, work);
    }

    /** Sets movement keys for this tick. Ignored unless {@code holder} holds {@link ActionResource#MOVEMENT}. */
    public void setMovement(LeaseHolder holder, boolean forward, boolean backward, boolean left, boolean right) {
        if (coordinator().holds(holder, ActionResource.MOVEMENT)) {
            scheduler.movement().setMovement(holder, forward, backward, left, right);
        }
    }

    public void setJump(LeaseHolder holder, boolean jump) {
        if (coordinator().holds(holder, ActionResource.JUMP)) scheduler.movement().setJump(holder, jump);
    }

    public void setSneak(LeaseHolder holder, boolean sneak) {
        if (coordinator().holds(holder, ActionResource.SNEAK)) scheduler.movement().setSneak(holder, sneak);
    }

    public void setSprint(LeaseHolder holder, boolean sprint) {
        if (coordinator().holds(holder, ActionResource.SPRINT)) scheduler.movement().setSprint(holder, sprint);
    }
}
