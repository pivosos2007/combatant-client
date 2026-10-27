/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.actions;

import combatant.client.automation.AbstractAction;
import combatant.client.automation.ActionContext;
import combatant.client.automation.ActionResult;

import java.util.function.BooleanSupplier;

/** Succeeds on the first tick the condition holds. Bound it with a timeout. */
public final class WaitUntilAction extends AbstractAction {
    private final BooleanSupplier condition;

    public WaitUntilAction(String name, BooleanSupplier condition) {
        super(name != null ? name : "WaitUntil");
        this.condition = condition;
    }

    @Override
    protected ActionResult onTick(ActionContext ctx) {
        return condition.getAsBoolean() ? ActionResult.SUCCESS : ActionResult.RUNNING;
    }
}
