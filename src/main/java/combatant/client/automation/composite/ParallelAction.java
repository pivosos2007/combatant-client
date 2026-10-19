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
 * Ticks all children every tick, in declaration order. Children hold their own leases, so two
 * children that need the same resource compete like separate tasks of equal priority (the first
 * holder keeps it).
 */
public final class ParallelAction extends AbstractAction {

    public enum Policy {
        /** Succeeds when every child succeeds; the first failure cancels the rest. */
        REQUIRE_ALL,
        /** Succeeds when any child succeeds and cancels the rest; fails when all failed. */
        REQUIRE_ONE
    }

    private final Policy policy;
    private final Action[] children;
    private final ActionResult[] results;

    public ParallelAction(String name, Policy policy, Action... children) {
        super(name != null ? name : "Parallel");
        if (children.length == 0) throw new IllegalArgumentException("Parallel needs at least one child");
        this.policy = policy;
        this.children = children.clone();
        this.results = new ActionResult[children.length];
    }

    @Override
    protected ActionResult onTick(ActionContext ctx) {
        int succeeded = 0;
        int failed = 0;
        ActionResult lastFailure = null;
        for (int i = 0; i < children.length; i++) {
            ActionResult result = results[i];
            if (result == null || result.isRunning()) result = results[i] = children[i].tick(ctx);
            if (result.isRunning()) continue;
            if (result.isSuccess()) {
                succeeded++;
                if (policy == Policy.REQUIRE_ONE) {
                    cancelChildren(ctx, "parallel satisfied");
                    return ActionResult.SUCCESS;
                }
            } else {
                failed++;
                lastFailure = result;
                if (policy == Policy.REQUIRE_ALL) {
                    cancelChildren(ctx, "sibling failed");
                    return result;
                }
            }
        }
        if (policy == Policy.REQUIRE_ALL && succeeded == children.length) return ActionResult.SUCCESS;
        if (policy == Policy.REQUIRE_ONE && failed == children.length) return lastFailure;
        return ActionResult.RUNNING;
    }

    @Override
    protected void onCancel(ActionContext ctx) {
        cancelChildren(ctx, "parallel cancelled");
    }

    private void cancelChildren(ActionContext ctx, String reason) {
        for (Action child : children) child.cancel(ctx, reason);
    }
}
