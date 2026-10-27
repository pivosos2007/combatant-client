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
 * Tries children in order until one succeeds. Fails with the last child's failure when every
 * alternative failed. A failed child hands over to the next one in the same tick.
 */
public final class SelectorAction extends AbstractAction {
    private final Action[] children;
    private int index;

    public SelectorAction(String name, Action... children) {
        super(name != null ? name : "Selector");
        if (children.length == 0) throw new IllegalArgumentException("Selector needs at least one child");
        this.children = children.clone();
    }

    @Override
    protected ActionResult onTick(ActionContext ctx) {
        ActionResult last = ActionResult.RUNNING;
        while (index < children.length) {
            last = children[index].tick(ctx);
            if (last.isRunning()) return ActionResult.RUNNING;
            if (last.isSuccess()) return ActionResult.SUCCESS;
            switch (last.reason()) {
                case MANUAL_OVERRIDE, CANCELLED, ERROR -> {
                    return last;
                }
                default -> index++;
            }
        }
        return last;
    }

    @Override
    protected void onCancel(ActionContext ctx) {
        if (index < children.length) children[index].cancel(ctx, "selector cancelled");
    }
}
