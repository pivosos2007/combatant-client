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

/** Holds the sneak key for a number of ticks, or for as long as a condition holds. */
public final class SneakAction extends AbstractAction {
    private final int ticks;
    private final BooleanSupplier condition;
    private int held;

    private SneakAction(int ticks, BooleanSupplier condition) {
        super("Sneak");
        this.ticks = ticks;
        this.condition = condition;
    }

    /** Sneaks for exactly {@code ticks} ticks of holding the key (waiting for the lease does not count). */
    public static SneakAction forTicks(int ticks) {
        return new SneakAction(Math.max(0, ticks), null);
    }

    /** Sneaks while {@code condition} is true; succeeds on the first tick it is false. */
    public static SneakAction whileTrue(BooleanSupplier condition) {
        return new SneakAction(0, java.util.Objects.requireNonNull(condition, "condition"));
    }

    @Override
    protected ActionResult onTick(ActionContext ctx) {
        if (condition != null ? !condition.getAsBoolean() : held >= ticks) return ActionResult.SUCCESS;
        if (!acquire(ctx, ActionResource.SNEAK)) return ActionResult.RUNNING;
        ctx.setSneak(this, true);
        held++;
        return ActionResult.RUNNING;
    }
}
