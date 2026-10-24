/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A high-level objective submitted by an owner (usually a module): a root {@link Action}, a
 * priority shared by all its leases, and the resources it gives up when the player takes over.
 * The task is cancelled automatically when its owner module is disabled.
 */
public final class AutomationTask {

    /** Movement keys yield to the player by default; silent rotation and slots do not. */
    public static final Set<ActionResource> DEFAULT_MANUAL_YIELD = Collections.unmodifiableSet(
            EnumSet.of(ActionResource.MOVEMENT, ActionResource.JUMP, ActionResource.SNEAK));

    private final Object owner;
    private final String name;
    private final int priority;
    private final Action root;
    private final Set<ActionResource> manualYield;
    private final Consumer<AutomationTask> onFinish;
    private ActionResult result = ActionResult.RUNNING;
    private ActionScheduler scheduler;

    private AutomationTask(Builder builder) {
        this.owner = builder.owner;
        this.name = builder.name;
        this.priority = builder.priority;
        this.root = builder.root;
        this.manualYield = builder.manualYield;
        this.onFinish = builder.onFinish;
    }

    public static Builder builder(Object owner, String name, Action root) {
        return new Builder(owner, name, root);
    }

    public Object owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public int priority() {
        return priority;
    }

    public Action root() {
        return root;
    }

    public Set<ActionResource> manualYield() {
        return manualYield;
    }

    public ActionResult result() {
        return result;
    }

    public ActionStatus status() {
        return result.status();
    }

    public boolean isDone() {
        return result.isDone();
    }

    /** Cancels the task if it was submitted and is still running. */
    public void cancel(String reason) {
        if (scheduler != null) scheduler.cancel(this, reason);
    }

    void attach(ActionScheduler scheduler) {
        if (this.scheduler != null) throw new IllegalStateException("Task already submitted: " + name);
        this.scheduler = scheduler;
    }

    /** @return false when already complete */
    boolean complete(ActionResult result) {
        if (this.result.isDone()) return false;
        this.result = result;
        return true;
    }

    void notifyFinished() {
        if (onFinish != null) onFinish.accept(this);
    }

    @Override
    public String toString() {
        return name + "@" + priority + "[" + result + "]";
    }

    public static final class Builder {
        private final Object owner;
        private final String name;
        private final Action root;
        private int priority = ActionPriority.UTILITY;
        private Set<ActionResource> manualYield = DEFAULT_MANUAL_YIELD;
        private Consumer<AutomationTask> onFinish;

        private Builder(Object owner, String name, Action root) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.name = Objects.requireNonNull(name, "name");
            this.root = Objects.requireNonNull(root, "root");
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        /** Resources this task gives up as soon as the player uses them. */
        public Builder yieldToManualInput(Set<ActionResource> resources) {
            this.manualYield = resources == null || resources.isEmpty()
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(EnumSet.copyOf(resources));
            return this;
        }

        /** Runs on the client thread once the task finishes for any reason. */
        public Builder onFinish(Consumer<AutomationTask> onFinish) {
            this.onFinish = onFinish;
            return this;
        }

        public AutomationTask build() {
            return new AutomationTask(this);
        }
    }
}
