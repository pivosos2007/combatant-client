/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.actions;

import combatant.client.automation.AbstractAction;
import combatant.client.automation.ActionContext;
import combatant.client.automation.ActionResource;
import combatant.client.automation.ActionResult;

import java.util.function.BooleanSupplier;

/**
 * Presses jump on every tick {@code condition} holds and never finishes on its own: the owning module cancels the
 * task. The jump lease is re-acquired each tick, so a higher-priority holder simply takes the key over and this
 * action resumes when it lets go. The key goes into the input state, so the server sees an ordinary jump input.
 */
public final class HoldJumpAction extends AbstractAction {
    private final BooleanSupplier condition;

    public HoldJumpAction(String name, BooleanSupplier condition) {
        super(name);
        this.condition = condition;
    }

    @Override
    protected ActionResult onTick(ActionContext ctx) {
        if (!acquire(ctx, ActionResource.JUMP)) return ActionResult.RUNNING;
        if (condition.getAsBoolean()) ctx.setJump(this, true);
        return ActionResult.RUNNING;
    }
}
