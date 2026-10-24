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
 * Builds its child when it first ticks, from the world as it is then. A factory that returns null means the step is
 * not needed and the action succeeds at once, so sequences can skip steps whose work is already done.
 */
public final class DeferAction extends AbstractAction {
    private final Supplier<? extends Action> factory;
    private Action child;

    public DeferAction(String name, Supplier<? extends Action> factory) {
        super(name != null ? name : "Defer");
        this.factory = factory;
    }

    @Override
    protected ActionResult onTick(ActionContext ctx) {
        if (child == null) {
            child = factory.get();
            if (child == null) return ActionResult.SUCCESS;
        }
        return child.tick(ctx);
    }

    @Override
    protected void onCancel(ActionContext ctx) {
        if (child != null) child.cancel(ctx, "defer cancelled");
    }
}
