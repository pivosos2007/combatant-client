/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.player;

import combatant.client.util.aiming.data.Rotation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.function.BiFunction;

/** Airborne velocity update from Minecraft 26.3 LivingEntity.updateFallFlyingMovement. */
public final class ElytraFlightPhysics {
    private ElytraFlightPhysics() {
    }

    public static Vec3 glide(Vec3 velocity, Rotation heading, double gravity) {
        return glide(velocity, heading.directionVector(), heading.pitch(), gravity);
    }

    public static Vec3 glide(Vec3 velocity, Vec3 look, float pitchDegrees, double gravity) {
        float pitch = pitchDegrees * Mth.DEG_TO_RAD;
        double lookHorizontal = look.horizontalDistance();
        double horizontalSpeed = velocity.horizontalDistance();
        double lift = Mth.square(Math.cos(pitch));
        Vec3 next = velocity.add(0.0, gravity * (-1.0 + lift * 0.75), 0.0);
        if (next.y < 0.0 && lookHorizontal > 0.0) {
            double transfer = next.y * -0.1 * lift;
            next = next.add(look.x * transfer / lookHorizontal, transfer,
                    look.z * transfer / lookHorizontal);
        }
        if (pitch < 0.0f && lookHorizontal > 0.0) {
            double transfer = horizontalSpeed * -Mth.sin(pitch) * 0.04;
            next = next.add(-look.x * transfer / lookHorizontal, transfer * 3.2,
                    -look.z * transfer / lookHorizontal);
        }
        if (lookHorizontal > 0.0) {
            next = next.add((look.x / lookHorizontal * horizontalSpeed - next.x) * 0.1,
                    0.0, (look.z / lookHorizontal * horizontalSpeed - next.z) * 0.1);
        }
        return next.multiply(0.99f, 0.98f, 0.99f);
    }

    public static Vec3 rocket(Vec3 velocity, Rotation heading) {
        return velocity.scale(0.5).add(heading.directionVector().scale(0.85));
    }

    /**
     * Boost runs between player movements; initial velocity includes earlier rocket ticks.
     * An unconfirmed launch can use Integer.MAX_VALUE as its possible boost duration.
     */
    public record FlightModel(double gravity, boolean slowFalling, int guaranteedBoostTicks, int possibleBoostTicks,
                              BiFunction<Vec3, Rotation, Vec3> boost) {
        public Vec3 glide(Vec3 velocity, Rotation heading) {
            double effectiveGravity = slowFalling && velocity.y <= 0.0 ? Math.min(gravity, 0.01) : gravity;
            return ElytraFlightPhysics.glide(velocity, heading, effectiveGravity);
        }

        public Vec3 afterMove(Vec3 velocity, Rotation heading, int tick, int boostTicks) {
            return tick <= boostTicks ? boost.apply(velocity, heading) : velocity;
        }
    }
}
