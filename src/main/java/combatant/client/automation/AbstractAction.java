/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation;

/**
 * Lifecycle shell for actions: start on first tick, end exactly once, leases released on every
 * exit path, exceptions turned into {@link FailureReason#ERROR}.
 *
 * <p>Subclasses implement {@link #onTick}. Leaf actions acquire resources with {@link #acquire}
 * and release backend state in {@link #onEnd}; {@link #onRevoked} must drop backend state
 * immediately because the new holder may claim the same backend in the same call.
 */
public abstract class AbstractAction implements Action, LeaseHolder {

    private final String name;
    private boolean started;
    private long startTick;
    private ActionResult result = ActionResult.RUNNING;
    private ActionResult revocation;
    private ActionCoordinator coordinator;

    protected AbstractAction(String name) {
        this.name = name != null ? name : getClass().getSimpleName();
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final ActionStatus status() {
        return result.status();
    }

    public final ActionResult result() {
        return result;
    }

    /** Started, not finished and not revoked: deferred work may still run. */
    public final boolean isLive() {
        return started && result.isRunning() && revocation == null;
    }

    @Override
    public final ActionResult tick(ActionContext ctx) {
        if (result.isDone()) return result;
        ActionResult next;
        try {
            if (!started) {
                started = true;
                startTick = ctx.tick();
                coordinator = ctx.coordinator();
                onStart(ctx);
            }
            next = revocation != null ? revocation : onTick(ctx);
        } catch (RuntimeException e) {
            next = ActionResult.failure(FailureReason.ERROR, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        if (next == null) next = ActionResult.RUNNING;
        if (next.isDone()) finish(ctx, next);
        return next;
    }

    @Override
    public final void cancel(ActionContext ctx, String reason) {
        if (result.isDone()) return;
        ActionResult cancelled = ActionResult.cancelled(reason);
        if (!started) {
            result = cancelled;
            return;
        }
        try {
            onCancel(ctx);
        } finally {
            finish(ctx, cancelled);
        }
    }

    @Override
    public final void onLeaseRevoked(ActionLease lease, ActionLease.RevokeReason reason) {
        if (result.isDone() || revocation != null) return;
        FailureReason failure = switch (reason) {
            case PREEMPTED -> FailureReason.PREEMPTED;
            case MANUAL_INPUT -> FailureReason.MANUAL_OVERRIDE;
            case SHUTDOWN -> FailureReason.CANCELLED;
        };
        revocation = ActionResult.failure(failure, lease.resource() + " " + reason.name().toLowerCase());
        try {
            onRevoked(lease);
        } finally {
            if (coordinator != null) coordinator.releaseAll(this);
        }
    }

    private void finish(ActionContext ctx, ActionResult finalResult) {
        result = finalResult;
        try {
            onEnd(ctx, finalResult);
        } catch (RuntimeException ignored) {
            // Cleanup failures must not keep leases alive; the result is already final.
        } finally {
            ctx.coordinator().releaseAll(this);
        }
    }

    /** Ticks since the first tick, 0 on the first tick. */
    protected final long elapsedTicks(ActionContext ctx) {
        return started ? ctx.tick() - startTick : 0;
    }

    /** Takes every resource at the task's priority or none. */
    protected final boolean acquire(ActionContext ctx, ActionResource... resources) {
        return ctx.coordinator().tryAcquire(this, ctx.priority(), ctx.task().manualYield(), ctx.tick(), resources);
    }

    protected final boolean holds(ActionContext ctx, ActionResource resource) {
        return ctx.coordinator().holds(this, resource);
    }

    protected void onStart(ActionContext ctx) {
    }

    protected abstract ActionResult onTick(ActionContext ctx);

    /** Called before {@link #onEnd} when the action is cancelled while running. */
    protected void onCancel(ActionContext ctx) {
    }

    /** Called once when the action finishes for any reason. Release backend state here. */
    protected void onEnd(ActionContext ctx, ActionResult result) {
    }

    /** Called synchronously when a lease is revoked; drop backend state bound to it. */
    protected void onRevoked(ActionLease lease) {
    }

    @Override
    public String toString() {
        return name + "[" + result + "]";
    }
}
