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

import java.util.function.BooleanSupplier;

/**
 * Checks a precondition before every child tick and stops the child with
 * {@link FailureReason#PRECONDITION} as soon as it no longer holds.
 */
public final class GuardAction extends AbstractAction {
    private final BooleanSupplier precondition;
    private final Action child;

    public GuardAction(String name, BooleanSupplier precondition, Action child) {
        super(name != null ? name : "Guard");
        this.precondition = precondition;
        this.child = child;
    }

    @Override
    protected ActionResult onTick(ActionContext ctx) {
        if (!precondition.getAsBoolean()) {
            child.cancel(ctx, "precondition failed");
            return ActionResult.failure(FailureReason.PRECONDITION, name());
        }
        return child.tick(ctx);
    }

    @Override
    protected void onCancel(ActionContext ctx) {
        child.cancel(ctx, "guard cancelled");
    }
}
