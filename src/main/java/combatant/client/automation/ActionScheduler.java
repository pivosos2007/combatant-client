/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Ticks submitted tasks in priority order (higher first, then submission order), so a higher
 * priority task always acquires resources before a lower one in the same tick. Free of Minecraft
 * types; {@link AutomationRuntime} drives it from client events.
 *
 * <p>Client-thread only. Tasks submitted or cancelled from inside a tick (for example from an
 * {@code onFinish} callback) take effect safely: new tasks first tick on the next tick.
 */
public final class ActionScheduler {

    private record Deferred(AbstractAction action, Runnable work) {
    }

    private final ActionCoordinator coordinator;
    private final MovementIntent movement = new MovementIntent();
    private final ActionContext context = new ActionContext(this);
    private final ArrayList<AutomationTask> tasks = new ArrayList<>();
    private final ArrayList<AutomationTask> pending = new ArrayList<>();
    private final ArrayDeque<Deferred> afterRotation = new ArrayDeque<>();
    private final ArrayDeque<Deferred> afterMovementPacket = new ArrayDeque<>();
    private Predicate<Object> ownerAlive = owner -> true;
    private Consumer<RuntimeException> errorSink = e -> { };
    private long tick;
    private boolean ticking;

    public ActionScheduler(ActionCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    public void setOwnerAlive(Predicate<Object> ownerAlive) {
        this.ownerAlive = ownerAlive != null ? ownerAlive : owner -> true;
    }

    /** Receives exceptions thrown by task callbacks and deferred work. */
    public void setErrorSink(Consumer<RuntimeException> errorSink) {
        this.errorSink = errorSink != null ? errorSink : e -> { };
    }

    public ActionCoordinator coordinator() {
        return coordinator;
    }

    public MovementIntent movement() {
        return movement;
    }

    public long currentTick() {
        return tick;
    }

    public AutomationTask submit(AutomationTask task) {
        task.attach(this);
        if (ticking) {
            pending.add(task);
        } else {
            insert(task);
        }
        return task;
    }

    /** Running tasks in tick order. */
    public List<AutomationTask> tasks() {
        ArrayList<AutomationTask> out = new ArrayList<>(tasks.size() + pending.size());
        for (AutomationTask task : tasks) if (!task.isDone()) out.add(task);
        for (AutomationTask task : pending) if (!task.isDone()) out.add(task);
        return out;
    }

    public boolean hasTasks(Object owner) {
        for (AutomationTask task : tasks) if (task.owner() == owner && !task.isDone()) return true;
        for (AutomationTask task : pending) if (task.owner() == owner && !task.isDone()) return true;
        return false;
    }

    /** Advances every running task by one tick. */
    public void tick(long tick) {
        this.tick = tick;
        movement.clear();
        ticking = true;
        try {
            for (int i = 0; i < tasks.size(); i++) {
                AutomationTask task = tasks.get(i);
                if (task.isDone()) continue;
                if (!ownerAlive.test(task.owner())) {
                    cancel(task, "owner stopped");
                    continue;
                }
                context.bind(task);
                ActionResult result = task.root().tick(context);
                if (result.isDone()) complete(task, result);
            }
        } finally {
            ticking = false;
            context.bind(null);
        }
        tasks.removeIf(AutomationTask::isDone);
        for (AutomationTask task : pending) {
            if (!task.isDone()) insert(task);
        }
        pending.clear();
    }

    public void cancel(AutomationTask task, String reason) {
        if (task == null || task.isDone()) return;
        AutomationTask previous = context.task();
        context.bind(task);
        try {
            task.root().cancel(context, reason);
        } finally {
            context.bind(previous);
        }
        complete(task, ActionResult.cancelled(reason));
        if (!ticking) {
            tasks.remove(task);
            pending.remove(task);
        }
    }

    public void cancelOwner(Object owner, String reason) {
        for (AutomationTask task : tasks()) {
            if (task.owner() == owner) cancel(task, reason);
        }
    }

    public void cancelAll(String reason) {
        for (AutomationTask task : tasks()) cancel(task, reason);
        afterRotation.clear();
        afterMovementPacket.clear();
        movement.clear();
    }

    void defer(ExecutionPhase phase, AbstractAction action, Runnable work) {
        if (action == null || work == null) return;
        queue(phase).add(new Deferred(action, work));
    }

    /** Runs deferred work for {@code phase}; work of actions that ended meanwhile is dropped. */
    public void runDeferred(ExecutionPhase phase) {
        ArrayDeque<Deferred> queue = queue(phase);
        Deferred deferred;
        while ((deferred = queue.poll()) != null) {
            if (!deferred.action().isLive()) continue;
            try {
                deferred.work().run();
            } catch (RuntimeException e) {
                errorSink.accept(e);
            }
        }
    }

    private ArrayDeque<Deferred> queue(ExecutionPhase phase) {
        return phase == ExecutionPhase.AFTER_ROTATION ? afterRotation : afterMovementPacket;
    }

    private void insert(AutomationTask task) {
        int index = tasks.size();
        while (index > 0 && tasks.get(index - 1).priority() < task.priority()) index--;
        tasks.add(index, task);
    }

    private void complete(AutomationTask task, ActionResult result) {
        if (!task.complete(result)) return;
        try {
            task.notifyFinished();
        } catch (RuntimeException e) {
            errorSink.accept(e);
        }
    }
}
