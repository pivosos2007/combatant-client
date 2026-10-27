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
import combatant.client.util.aiming.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Walks toward a position with WASD keys picked from {@link RotationManager#getMovementRotation()} (the view the
 * movement code actually uses), so it never needs a rotation of its own. Distance is horizontal. Succeeds within
 * {@code arrivalRadius}; fails {@link FailureReason#UNREACHABLE} when the distance has not shrunk for
 * {@code maxStallTicks} ticks. The target may move: it is re-read each tick.
 */
public final class MoveAction extends AbstractAction {

    /**
     * @param sprint            press sprint while heading forward and farther than {@code sprintMinDistance}
     * @param jumpObstacles     jump when walking into a wall on the ground
     * @param minProgress       blocks the distance must shrink by to count as progress
     */
    public record Settings(double arrivalRadius, boolean sprint, double sprintMinDistance, boolean jumpObstacles,
                           int maxStallTicks, double minProgress) {
        public static final Settings DEFAULT = new Settings(0.3, false, 2.5, false, 20, 0.02);

        public Settings {
            arrivalRadius = Math.max(0.0, arrivalRadius);
            sprintMinDistance = Math.max(0.0, sprintMinDistance);
            maxStallTicks = Math.max(1, maxStallTicks);
            minProgress = Math.max(0.0, minProgress);
        }

        public Settings withArrivalRadius(double radius) {
            return new Settings(radius, sprint, sprintMinDistance, jumpObstacles, maxStallTicks, minProgress);
        }

        public Settings withSprint(boolean sprint) {
            return new Settings(arrivalRadius, sprint, sprintMinDistance, jumpObstacles, maxStallTicks, minProgress);
        }

        public Settings withJumpObstacles(boolean jump) {
            return new Settings(arrivalRadius, sprint, sprintMinDistance, jump, maxStallTicks, minProgress);
        }

        public Settings withMaxStallTicks(int ticks) {
            return new Settings(arrivalRadius, sprint, sprintMinDistance, jumpObstacles, ticks, minProgress);
        }
    }

    /** A target jump larger than this restarts the stall watch: the goal moved, not the player. */
    private static final double TARGET_JUMP_RESET = 2.0;

    private final Supplier<Vec3> target;
    private final Settings settings;
    private final ProgressWatchdog watchdog;
    private final ActionResource[] resources;
    private double lastTargetX = Double.NaN;
    private double lastTargetZ = Double.NaN;

    public MoveAction(Supplier<Vec3> target, Settings settings) {
        super("Move");
        this.target = Objects.requireNonNull(target, "target");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.watchdog = new ProgressWatchdog(settings.maxStallTicks(), settings.minProgress());
        ArrayList<ActionResource> needed = new ArrayList<>(3);
        needed.add(ActionResource.MOVEMENT);
        if (settings.sprint()) needed.add(ActionResource.SPRINT);
        if (settings.jumpObstacles()) needed.add(ActionResource.JUMP);
        this.resources = needed.toArray(new ActionResource[0]);
    }

    public MoveAction(Vec3 fixed, Settings settings) {
        this(constant(fixed), settings);
    }

    private static Supplier<Vec3> constant(Vec3 fixed) {
        Objects.requireNonNull(fixed, "fixed");
        return () -> fixed;
    }

    @Override
    protected ActionResult onTick(ActionContext ctx) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return ActionResult.failure(FailureReason.PRECONDITION, "no player");
        Vec3 goal = target.get();
        if (goal == null) return ActionResult.failure(FailureReason.INVALID_TARGET, "no target position");
        if (!acquire(ctx, resources)) return ActionResult.RUNNING;

        double dx = goal.x - player.getX();
        double dz = goal.z - player.getZ();
        double distance = Math.hypot(dx, dz);
        if (distance <= settings.arrivalRadius()) return ActionResult.SUCCESS;

        if (!Double.isNaN(lastTargetX) && Math.hypot(goal.x - lastTargetX, goal.z - lastTargetZ) > TARGET_JUMP_RESET) {
            watchdog.reset();
        }
        lastTargetX = goal.x;
        lastTargetZ = goal.z;
        if (watchdog.update(distance)) {
            return ActionResult.failure(FailureReason.UNREACHABLE,
                    "no progress for " + watchdog.stalledTicks() + " ticks at " + String.format("%.2f", distance));
        }

        int keys = MovementKeys.keysToward(RotationManager.INSTANCE.getMovementRotation().yaw(), dx, dz);
        MovementKeys.apply(ctx, this, keys);
        if (settings.sprint()) {
            ctx.setSprint(this, (keys & MovementKeys.FORWARD) != 0 && distance > settings.sprintMinDistance());
        }
        if (settings.jumpObstacles()) {
            ctx.setJump(this, keys != 0 && player.horizontalCollision && player.onGround());
        }
        return ActionResult.RUNNING;
    }
}
