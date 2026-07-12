/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.helpers;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HeadAnchor {

    private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);
    private static final Vec3 WORLD_RIGHT = new Vec3(1.0, 0.0, 0.0);
    private static final Vec3 WORLD_FORWARD = new Vec3(0.0, 0.0, 1.0);

    private static final Map<UUID, RotationSample> HEAD_YAW_SAMPLES = new HashMap<>();

    private final Vec3 origin;
    private final Vec3 right;
    private final Vec3 up;
    private final Vec3 forward;

    private HeadAnchor(Vec3 origin, Vec3 right, Vec3 up, Vec3 forward) {
        this.origin = origin;
        this.right = safeNormalize(right, WORLD_RIGHT);
        this.up = safeNormalize(up, WORLD_UP);
        this.forward = safeNormalize(forward, WORLD_FORWARD);
    }

    public static HeadAnchor of(Player player,
                                float tickDelta,
                                double topOffset,
                                double forwardOffset,
                                double rightOffset) {
        Vec3 feet = player.getPosition(tickDelta);

        float bodyYaw = Mth.rotLerp(
                tickDelta,
                player.yBodyRotO,
                player.yBodyRot
        );

        float headYaw = interpolatedHeadYaw(player, tickDelta);

        float pitch = Mth.lerp(
                tickDelta,
                player.xRotO,
                player.getXRot()
        );

        Vec3 eye = feet.add(0.0, player.getEyeHeight(player.getPose()), 0.0);

        Vec3 forward = Vec3.directionFromRotation(pitch, headYaw);
        if (forward.lengthSqr() < 1.0E-6) {
            forward = Vec3.directionFromRotation(0.0F, bodyYaw);
        }
        forward = safeNormalize(forward, WORLD_FORWARD);

        Vec3 right = WORLD_UP.cross(forward);
        if (right.lengthSqr() < 1.0E-6) {
            right = yawRight(bodyYaw);
        }
        right = safeNormalize(right, WORLD_RIGHT);

        Vec3 up = forward.cross(right);
        up = safeNormalize(up, WORLD_UP);

        Vec3 origin = eye
                .add(up.scale(topOffset))
                .add(forward.scale(forwardOffset))
                .add(right.scale(rightOffset));

        return new HeadAnchor(origin, right, up, forward);
    }

    public static HeadAnchor world(Vec3 origin) {
        return new HeadAnchor(
                origin,
                WORLD_RIGHT,
                WORLD_UP,
                WORLD_FORWARD
        );
    }

    private static float interpolatedHeadYaw(Player player, float tickDelta) {
        UUID uuid = player.getUUID();
        float current = player.getYHeadRot();

        RotationSample sample = HEAD_YAW_SAMPLES.get(uuid);
        if (sample == null) {
            sample = new RotationSample(player.tickCount, current, current);
            HEAD_YAW_SAMPLES.put(uuid, sample);
            return current;
        }

        if (sample.tick != player.tickCount) {
            sample.previous = sample.current;
            sample.current = current;
            sample.tick = player.tickCount;
        } else {
            sample.current = current;
        }

        return Mth.rotLerp(tickDelta, sample.previous, sample.current);
    }

    private static Vec3 yawRight(float yawDeg) {
        double yaw = Math.toRadians(yawDeg);
        return new Vec3(
                Math.cos(yaw),
                0.0,
                Math.sin(yaw)
        );
    }

    private static double[] rotateLocal(double x,
                                        double y,
                                        double z,
                                        float yawDeg,
                                        float pitchDeg,
                                        float rollDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double roll = Math.toRadians(rollDeg);

        double cosY = Math.cos(yaw);
        double sinY = Math.sin(yaw);

        double x1 = x * cosY - z * sinY;
        double y1 = y;
        double z1 = x * sinY + z * cosY;

        double cosP = Math.cos(pitch);
        double sinP = Math.sin(pitch);

        double x2 = x1;
        double y2 = y1 * cosP - z1 * sinP;
        double z2 = y1 * sinP + z1 * cosP;

        double cosR = Math.cos(roll);
        double sinR = Math.sin(roll);

        double x3 = x2 * cosR - y2 * sinR;
        double y3 = x2 * sinR + y2 * cosR;
        double z3 = z2;

        return new double[]{x3, y3, z3};
    }

    private static Vec3 safeNormalize(Vec3 vector, Vec3 fallback) {
        if (vector == null || vector.lengthSqr() < 1.0E-6) {
            return fallback;
        }

        return vector.normalize();
    }

    public Vec3 origin() {
        return origin;
    }

    public Vec3 right() {
        return right;
    }

    public Vec3 up() {
        return up;
    }

    public Vec3 forward() {
        return forward;
    }

    public Vec3 local(double x, double y, double z) {
        return origin
                .add(right.scale(x))
                .add(up.scale(y))
                .add(forward.scale(z));
    }

    public Vec3 localRotated(double x,
                             double y,
                             double z,
                             float yawDeg,
                             float pitchDeg,
                             float rollDeg) {
        double[] rotated = rotateLocal(x, y, z, yawDeg, pitchDeg, rollDeg);
        return local(rotated[0], rotated[1], rotated[2]);
    }

    private static final class RotationSample {
        private int tick;
        private float previous;
        private float current;

        private RotationSample(int tick, float previous, float current) {
            this.tick = tick;
            this.previous = previous;
            this.current = current;
        }
    }
}