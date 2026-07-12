/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.projectile;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record TrajectoryInfo(
        double gravity,
        double hitboxRadius,
        double initialVelocity,
        double drag,
        double dragInWater,
        float roll,
        boolean copiesPlayerVelocity
) {

    public static final TrajectoryInfo GENERIC = new TrajectoryInfo(0.03, 0.25);
    public static final TrajectoryInfo PERSISTENT = new TrajectoryInfo(0.05, 0.5);
    public static final TrajectoryInfo POTION = GENERIC.withGravity(0.05).withInitialVelocity(0.5).withRoll(-20.0f);
    public static final TrajectoryInfo EXP_BOTTLE = POTION.withInitialVelocity(0.7);
    public static final TrajectoryInfo FISHING_ROD = GENERIC.withGravity(0.04).withDrag(0.92);
    public static final TrajectoryInfo TRIDENT = PERSISTENT.withInitialVelocity(2.5).withGravity(0.05).withDragInWater(0.99);
    public static final TrajectoryInfo BOW_FULL_PULL = PERSISTENT.withInitialVelocity(3.0);
    public static final TrajectoryInfo FIREBALL = new TrajectoryInfo(0.0, 1.0);
    public static final TrajectoryInfo WIND_CHARGE = new TrajectoryInfo(0.0, 1.0, 1.5, 0.99, 0.6, 0.0f, false);

    public TrajectoryInfo(double gravity, double hitboxRadius) {
        this(gravity, hitboxRadius, 1.5, 0.99, 0.6, 0.0f, true);
    }

    public static TrajectoryInfo bowWithUsageDuration(Player player, int usageDurationTicks) {
        float power = BowItem.getPowerForTime(usageDurationTicks);
        if (power < 0.1f) {
            return null;
        }

        double v0 = power * BOW_FULL_PULL.initialVelocity;
        return BOW_FULL_PULL.withInitialVelocity(v0);
    }

    public AABB hitbox(Vec3 center) {
        return new AABB(
                center.x - hitboxRadius,
                center.y - hitboxRadius,
                center.z - hitboxRadius,
                center.x + hitboxRadius,
                center.y + hitboxRadius,
                center.z + hitboxRadius
        );
    }

    public Typed typed(TrajectoryType type) {
        return new Typed(this, type);
    }

    public TrajectoryInfo withGravity(double value) {
        return new TrajectoryInfo(value, hitboxRadius, initialVelocity, drag, dragInWater, roll, copiesPlayerVelocity);
    }

    public TrajectoryInfo withInitialVelocity(double value) {
        return new TrajectoryInfo(gravity, hitboxRadius, value, drag, dragInWater, roll, copiesPlayerVelocity);
    }

    public TrajectoryInfo withDrag(double value) {
        return new TrajectoryInfo(gravity, hitboxRadius, initialVelocity, value, dragInWater, roll, copiesPlayerVelocity);
    }

    public TrajectoryInfo withDragInWater(double value) {
        return new TrajectoryInfo(gravity, hitboxRadius, initialVelocity, drag, value, roll, copiesPlayerVelocity);
    }

    public TrajectoryInfo withRoll(float value) {
        return new TrajectoryInfo(gravity, hitboxRadius, initialVelocity, drag, dragInWater, value, copiesPlayerVelocity);
    }

    public record Typed(TrajectoryInfo info, TrajectoryType type) {
    }
}
