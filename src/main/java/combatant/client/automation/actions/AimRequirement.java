/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.actions;

import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Rotation precondition shared by interaction actions: request the goal through {@link RotationManager} on the action's
 * tick, and allow the interaction once the rotation the server last saw is within tolerance (the same criterion the
 * block interaction pipeline uses). Owned by exactly one action, which is the RotationManager provider.
 */
final class AimRequirement {
    private final Supplier<Vec3> point;
    private final Supplier<Rotation> rotation;
    private final RotateAction.Settings settings;
    private Rotation desired;

    private AimRequirement(Supplier<Vec3> point, Supplier<Rotation> rotation, RotateAction.Settings settings) {
        this.point = point;
        this.rotation = rotation;
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    static AimRequirement atPoint(Supplier<Vec3> point, RotateAction.Settings settings) {
        return new AimRequirement(Objects.requireNonNull(point, "point"), null, settings);
    }

    static AimRequirement toRotation(Supplier<Rotation> rotation, RotateAction.Settings settings) {
        return new AimRequirement(null, Objects.requireNonNull(rotation, "rotation"), settings);
    }

    /**
     * Requests this tick's goal.
     *
     * @return false when the goal cannot be computed (no target point/rotation)
     */
    boolean request(LocalPlayer player, int priority, Object provider) {
        Vec3 aimPoint = null;
        Rotation goal;
        if (point != null) {
            aimPoint = point.get();
            if (aimPoint == null) return false;
            goal = Rotation.lookingAt(aimPoint, player.getEyePosition());
        } else {
            goal = rotation.get();
            if (goal == null) return false;
        }
        desired = goal.normalize();
        RotationManager.INSTANCE.setRotationTarget(new RotationTarget(desired, null, aimPoint, settings.processors(),
                settings.resetTicks(), settings.resetThreshold(), settings.considerInventory(),
                settings.movementCorrection(), null), priority, provider);
        return true;
    }

    /** True when {@code provider}'s last request is the active one and the server has seen it within tolerance. */
    boolean aligned(Object provider) {
        if (desired == null) return false;
        RotationManager manager = RotationManager.INSTANCE;
        Rotation server = manager.getServerRotation();
        return manager.ownsActiveRotation(provider) && server != null
                && ToleranceHold.angularError(server.yaw(), server.pitch(), desired.yaw(), desired.pitch())
                <= settings.tolerance();
    }

    void release(Object provider) {
        RotationManager.INSTANCE.release(provider, true);
    }
}
