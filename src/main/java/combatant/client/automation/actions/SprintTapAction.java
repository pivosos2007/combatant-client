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
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * Resets sprint knockback: either releases the forward key and the sprint key for a few ticks (the other
 * keys the player holds stay as they are), or taps sneak. Counts only ticks in which it holds the leases.
 */
public final class SprintTapAction extends AbstractAction {

    public enum Kind { FORWARD_RELEASE, SNEAK }

    private final Kind kind;
    private final int ticks;
    private int held;

    private SprintTapAction(Kind kind, int ticks) {
        super(kind == Kind.SNEAK ? "SneakTap" : "ForwardTap");
        this.kind = kind;
        this.ticks = Math.max(1, ticks);
    }

    public static SprintTapAction forwardRelease(int ticks) {
        return new SprintTapAction(Kind.FORWARD_RELEASE, ticks);
    }

    public static SprintTapAction sneak(int ticks) {
        return new SprintTapAction(Kind.SNEAK, ticks);
    }

    @Override
    protected ActionResult onTick(ActionContext ctx) {
        if (held >= ticks) return ActionResult.SUCCESS;
        if (kind == Kind.SNEAK) {
            if (!acquire(ctx, ActionResource.SNEAK)) return ActionResult.RUNNING;
            ctx.setSneak(this, true);
        } else {
            if (!acquire(ctx, ActionResource.MOVEMENT, ActionResource.SPRINT)) return ActionResult.RUNNING;
            Options options = Minecraft.getInstance().options;
            ctx.setMovement(this, false, options.keyDown.isDown(), options.keyLeft.isDown(), options.keyRight.isDown());
            ctx.setSprint(this, false);
        }
        held++;
        return ActionResult.RUNNING;
    }
}
