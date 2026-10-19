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

/** Succeeds once {@code ticks} ticks have passed since it started; 0 succeeds immediately. */
public final class WaitTicksAction extends AbstractAction {
    private final int ticks;

    public WaitTicksAction(int ticks) {
        super("Wait" + ticks);
        this.ticks = Math.max(0, ticks);
    }

    @Override
    protected ActionResult onTick(ActionContext ctx) {
        return elapsedTicks(ctx) >= ticks ? ActionResult.SUCCESS : ActionResult.RUNNING;
    }
}
