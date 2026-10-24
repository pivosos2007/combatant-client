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

/**
 * Runs children in order and fails with the first failure. Children that finish instantly let
 * the next one start in the same tick, so zero-duration checks do not cost a tick each.
 */
public final class SequenceAction extends AbstractAction {
    private final Action[] children;
    private int index;

    public SequenceAction(String name, Action... children) {
        super(name != null ? name : "Sequence");
        this.children = children.clone();
    }

    /** Index of the running child, or the child count once finished. */
    public int currentIndex() {
        return index;
    }

    @Override
    protected ActionResult onTick(ActionContext ctx) {
        while (index < children.length) {
            ActionResult result = children[index].tick(ctx);
            if (result.isRunning()) return ActionResult.RUNNING;
            if (!result.isSuccess()) return result;
            index++;
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected void onCancel(ActionContext ctx) {
        if (index < children.length) children[index].cancel(ctx, "sequence cancelled");
    }
}
