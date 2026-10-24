/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.composite;

import combatant.client.automation.AbstractAction;
import combatant.client.automation.Action;
import combatant.client.automation.ActionContext;
import combatant.client.automation.ActionResult;

import java.util.function.Supplier;

/**
 * Re-runs a freshly built action after retryable failures (see
 * {@link combatant.client.automation.FailureReason#retryable()}), waiting {@code backoffTicks}
 * between attempts. Non-retryable failures and the last failed attempt end the retry.
 */
public final class RetryAction extends AbstractAction {
    private final Supplier<? extends Action> factory;
    private final int maxAttempts;
    private final int backoffTicks;
    private Action current;
    private int attempts;
    private long resumeTick = Long.MIN_VALUE;

    public RetryAction(String name, Supplier<? extends Action> factory, int maxAttempts, int backoffTicks) {
        super(name != null ? name : "Retry");
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
        this.factory = factory;
        this.maxAttempts = maxAttempts;
        this.backoffTicks = Math.max(0, backoffTicks);
    }

    /** Attempts started so far. */
    public int attempts() {
        return attempts;
    }

    @Override
    protected ActionResult onTick(ActionContext ctx) {
        if (current == null) {
            if (ctx.tick() < resumeTick) return ActionResult.RUNNING;
            current = factory.get();
            attempts++;
        }
        ActionResult result = current.tick(ctx);
        if (result.isRunning() || result.isSuccess()) return result;
        if (!result.reason().retryable() || attempts >= maxAttempts) return result;
        current = null;
        // N idle ticks between the failing tick and the next attempt; N = 0 still waits for the next tick so a stale world is not re-hit in the same tick.
        resumeTick = ctx.tick() + backoffTicks + 1;
        return ActionResult.RUNNING;
    }

    @Override
    protected void onCancel(ActionContext ctx) {
        if (current != null) current.cancel(ctx, "retry cancelled");
    }
}
