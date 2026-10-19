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
import combatant.client.automation.FailureReason;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * One jump: waits for the ground, presses jump on a grounded tick and succeeds once the player is airborne. Fails
 * {@link FailureReason#TIMEOUT} when the ground is never reached or the jump never leaves it (low ceiling, webs).
 */
public final class JumpAction extends AbstractAction {
    private final int timeoutTicks;
    private boolean pressed;

    public JumpAction() {
        this(20);
    }

    public JumpAction(int timeoutTicks) {
        super("Jump");
        this.timeoutTicks = Math.max(1, timeoutTicks);
    }

    @Override
    protected ActionResult onTick(ActionContext ctx) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return ActionResult.failure(FailureReason.PRECONDITION, "no player");
        if (elapsedTicks(ctx) >= timeoutTicks) {
            return ActionResult.failure(FailureReason.TIMEOUT, pressed ? "jump did not leave the ground" : "no ground");
        }
        if (!acquire(ctx, ActionResource.JUMP)) return ActionResult.RUNNING;
        if (pressed && !player.onGround()) return ActionResult.SUCCESS;
        if (player.onGround()) {
            ctx.setJump(this, true);
            pressed = true;
        }
        return ActionResult.RUNNING;
    }
}
