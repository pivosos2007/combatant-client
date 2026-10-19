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
import combatant.client.automation.FailureReason;

/** Gives the child at most {@code ticks} ticks, then cancels it and fails with {@link FailureReason#TIMEOUT}. */
public final class TimeoutAction extends AbstractAction {
    private final Action child;
    private final int ticks;

    public TimeoutAction(String name, Action child, int ticks) {
        super(name != null ? name : "Timeout");
        if (ticks < 1) throw new IllegalArgumentException("ticks must be >= 1");
        this.child = child;
        this.ticks = ticks;
    }

    @Override
    protected ActionResult onTick(ActionContext ctx) {
        if (elapsedTicks(ctx) >= ticks) {
            child.cancel(ctx, "timed out");
            return ActionResult.failure(FailureReason.TIMEOUT, child.name() + " after " + ticks + " ticks");
        }
        return child.tick(ctx);
    }

    @Override
    protected void onCancel(ActionContext ctx) {
        child.cancel(ctx, "timeout cancelled");
    }
}
