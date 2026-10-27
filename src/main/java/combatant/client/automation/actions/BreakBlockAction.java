/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.actions;

import combatant.client.automation.AbstractAction;
import combatant.client.automation.ActionContext;
import combatant.client.automation.ActionLease;
import combatant.client.automation.ActionResource;
import combatant.client.automation.ActionResult;
import combatant.client.automation.ExecutionPhase;
import combatant.client.automation.FailureReason;
import combatant.client.util.block.interaction.BlockInteractionPipeline;
import combatant.client.util.block.interaction.BlockInteractionPipeline.MiningPlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/**
 * Breaks one block through {@link BlockInteractionPipeline#mine}: vanilla start/continue destroy, tool lease and
 * rotation as the {@link MiningPlan} says, no packet tricks. The pipeline is driven every tick in the
 * {@link ExecutionPhase#AFTER_ROTATION} phase, where it can validate against this tick's rotation request.
 *
 * <p>Resources: INTERACTION, HOTBAR when the plan auto-selects a tool, ROTATION when the plan rotates.
 *
 * <p>Success: the block has been absent from the client world for {@code confirmTicks} consecutive ticks (the server
 * can still revert the client's prediction in that window, which resets the count). Fails
 * {@link FailureReason#REJECTED} when it keeps reappearing, {@link FailureReason#UNREACHABLE} when the pipeline
 * cannot make progress for {@code maxStallTicks} ticks and {@link FailureReason#TIMEOUT} after {@code timeoutTicks}.
 */
public final class BreakBlockAction extends AbstractAction {

    private static final int MAX_REAPPEARANCES = 3;

    private final BlockPos pos;
    private final Direction face;
    private final MiningPlan plan;
    private final int timeoutTicks;
    private final int maxStallTicks;
    private final RemovalConfirmation confirmation;
    private final ActionResource[] resources;
    private int stalledTicks;

    /**
     * @param face          preferred face, or null to let the pipeline pick one
     * @param confirmTicks  consecutive ticks the block must stay gone
     */
    public BreakBlockAction(BlockPos pos, Direction face, MiningPlan plan, int confirmTicks, int timeoutTicks,
                            int maxStallTicks) {
        super("BreakBlock");
        this.pos = Objects.requireNonNull(pos, "pos").immutable();
        this.face = face;
        this.plan = Objects.requireNonNull(plan, "plan");
        this.timeoutTicks = Math.max(1, timeoutTicks);
        this.maxStallTicks = Math.max(1, maxStallTicks);
        this.confirmation = new RemovalConfirmation(confirmTicks, MAX_REAPPEARANCES);
        this.resources = resourcesFor(plan);
    }

    static ActionResource[] resourcesFor(MiningPlan plan) {
        boolean tool = plan.autoTool();
        boolean rotate = plan.rotation().enabled();
        if (tool && rotate) return new ActionResource[]{ActionResource.INTERACTION, ActionResource.HOTBAR,
                ActionResource.ROTATION};
        if (tool) return new ActionResource[]{ActionResource.INTERACTION, ActionResource.HOTBAR};
        if (rotate) return new ActionResource[]{ActionResource.INTERACTION, ActionResource.ROTATION};
        return new ActionResource[]{ActionResource.INTERACTION};
    }

    @Override
    protected ActionResult onTick(ActionContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.gameMode == null) {
            return ActionResult.failure(FailureReason.PRECONDITION, "no world");
        }

        BlockState state = level.getBlockState(pos);
        boolean gone = !BlockInteractionPipeline.isMineable(level, pos, state);
        switch (confirmation.update(gone)) {
            case CONFIRMED -> {
                return ActionResult.SUCCESS;
            }
            case REJECTED -> {
                return ActionResult.failure(FailureReason.REJECTED, "block keeps reappearing");
            }
            case PENDING -> {
            }
        }
        if (gone) {
            // Confirmation is bounded by its own logic; do not time out a break that already happened.
            return ActionResult.RUNNING;
        }
        if (elapsedTicks(ctx) >= timeoutTicks) {
            return ActionResult.failure(FailureReason.TIMEOUT, "block still there after " + timeoutTicks + " ticks");
        }
        if (stalledTicks >= maxStallTicks) {
            return ActionResult.failure(FailureReason.UNREACHABLE, "no mining progress for " + stalledTicks + " ticks");
        }
        if (!acquire(ctx, resources)) return ActionResult.RUNNING;

        ctx.defer(ExecutionPhase.AFTER_ROTATION, this, this::mine);
        return ActionResult.RUNNING;
    }

    private void mine() {
        boolean progressed = BlockInteractionPipeline.INSTANCE.mine(this, pos, face, plan);
        if (progressed) {
            stalledTicks = 0;
        } else {
            stalledTicks++;
        }
    }

    @Override
    protected void onRevoked(ActionLease lease) {
        BlockInteractionPipeline.INSTANCE.stopMining(this);
    }

    @Override
    protected void onEnd(ActionContext ctx, ActionResult result) {
        BlockInteractionPipeline.INSTANCE.stopMining(this);
    }
}
