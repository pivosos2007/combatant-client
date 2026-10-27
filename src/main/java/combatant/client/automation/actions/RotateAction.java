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
import combatant.client.automation.FailureReason;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.aiming.features.processors.RotationProcessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Holds {@link ActionResource#ROTATION} and requests the goal through {@link RotationManager} every tick (requests
 * expire after {@code resetTicks}). Succeeds when the rotation the server last saw is within {@code tolerance} degrees
 * of the goal for {@code holdTicks + 1} consecutive ticks. The goal may move: it is re-read each tick.
 */
public final class RotateAction extends AbstractAction {

    /**
     * @param tolerance      angular distance in degrees counted as aligned
     * @param holdTicks      extra consecutive aligned ticks required before success
     * @param resetTicks     lifetime of each per-tick request, at least 1
     * @param resetThreshold forwarded to {@link RotationTarget}
     * @param processors     smoothing/jitter stages supplied by the module; empty snaps to the goal
     */
    public record Settings(float tolerance, int holdTicks, int resetTicks, float resetThreshold,
                           List<RotationProcessor> processors, MovementCorrection movementCorrection,
                           boolean considerInventory) {
        public static final Settings DEFAULT =
                new Settings(2.0f, 0, 2, 1.0f, List.of(), MovementCorrection.SILENT, false);

        public Settings {
            tolerance = Math.max(0.0f, tolerance);
            holdTicks = Math.max(0, holdTicks);
            resetTicks = Math.max(1, resetTicks);
            processors = processors != null ? List.copyOf(processors) : List.of();
            movementCorrection = movementCorrection != null ? movementCorrection : MovementCorrection.SILENT;
        }

        public Settings withTolerance(float tolerance) {
            return new Settings(tolerance, holdTicks, resetTicks, resetThreshold, processors, movementCorrection,
                    considerInventory);
        }

        public Settings withHoldTicks(int holdTicks) {
            return new Settings(tolerance, holdTicks, resetTicks, resetThreshold, processors, movementCorrection,
                    considerInventory);
        }
    }

    private final Supplier<Rotation> goal;
    private final Supplier<Vec3> point;
    private final Settings settings;
    private final ToleranceHold hold;

    private RotateAction(String name, Supplier<Rotation> goal, Supplier<Vec3> point, Settings settings) {
        super(name != null ? name : "Rotate");
        this.goal = goal;
        this.point = point;
        this.settings = Objects.requireNonNull(settings, "settings");
        this.hold = new ToleranceHold(settings.holdTicks());
    }

    /** Looks at a world point, re-evaluated every tick from the current eye position. */
    public static RotateAction toPoint(Supplier<Vec3> point, Settings settings) {
        return new RotateAction(null, null, Objects.requireNonNull(point, "point"), settings);
    }

    /** Rotates to an explicit rotation, re-evaluated every tick; a {@code null} result fails the action. */
    public static RotateAction toRotation(Supplier<Rotation> goal, Settings settings) {
        return new RotateAction(null, Objects.requireNonNull(goal, "goal"), null, settings);
    }

    public static RotateAction toRotation(Rotation fixed, Settings settings) {
        Objects.requireNonNull(fixed, "fixed");
        return toRotation(() -> fixed, settings);
    }

    @Override
    protected ActionResult onTick(ActionContext ctx) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return ActionResult.failure(FailureReason.PRECONDITION, "no player");
        if (!acquire(ctx, ActionResource.ROTATION)) return ActionResult.RUNNING;

        Rotation desired;
        Vec3 aimPoint = null;
        if (point != null) {
            aimPoint = point.get();
            if (aimPoint == null) return ActionResult.failure(FailureReason.INVALID_TARGET, "no target point");
            desired = Rotation.lookingAt(aimPoint, player.getEyePosition());
        } else {
            desired = goal.get();
            if (desired == null) return ActionResult.failure(FailureReason.INVALID_TARGET, "no target rotation");
        }
        desired = desired.normalize();

        RotationTarget target = new RotationTarget(desired, null, aimPoint, settings.processors(),
                settings.resetTicks(), settings.resetThreshold(), settings.considerInventory(),
                settings.movementCorrection(), null);
        RotationManager manager = RotationManager.INSTANCE;
        manager.setRotationTarget(target, ctx.priority(), this);

        Rotation server = manager.getServerRotation();
        boolean aligned = server != null
                && ToleranceHold.angularError(server.yaw(), server.pitch(), desired.yaw(), desired.pitch())
                <= settings.tolerance();
        return hold.update(aligned) ? ActionResult.SUCCESS : ActionResult.RUNNING;
    }

    @Override
    protected void onEnd(ActionContext ctx, ActionResult result) {
        RotationManager.INSTANCE.release(this, true);
    }

    @Override
    protected void onRevoked(ActionLease lease) {
        RotationManager.INSTANCE.release(this, true);
    }
}
