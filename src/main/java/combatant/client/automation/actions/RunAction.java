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

import java.util.function.Supplier;

/**
 * Evaluates a step function every tick until it returns a finished result. Use for module-specific
 * glue that needs no resources (bookkeeping, state checks); anything touching player controls
 * belongs in a resource-holding action.
 */
public final class RunAction extends AbstractAction {
    private final Supplier<ActionResult> step;

    public RunAction(String name, Supplier<ActionResult> step) {
        super(name != null ? name : "Run");
        this.step = step;
    }

    public static RunAction once(String name, Runnable work) {
        return new RunAction(name, () -> {
            work.run();
            return ActionResult.SUCCESS;
        });
    }

    @Override
    protected ActionResult onTick(ActionContext ctx) {
        return step.get();
    }
}
