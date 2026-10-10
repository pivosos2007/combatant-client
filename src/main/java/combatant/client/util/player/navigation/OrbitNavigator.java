/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.player.navigation;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Short-range reactive steering for TargetStrafe. No world graph, queue, pathfinder,
 * background work or deferred commands: either this tick's direction is safe or
 * the caller leaves the player's movement alone.
 */
public final class OrbitNavigator {
    private static final double[] AVOID_ANGLES = {40, -40, 80, -80, 120, -120};
    private static final double MAX_AIR_RISE = 1.65;
    private static final double MIN_AIR_FALL = -0.40;

    private LivingEntity trackedTarget;
    private Vec3 direction;
    private Vec3 previousPosition;
    private Vec3 previousDirection;
    private double takeoffY;
    private int cubeCorner = -1;
    private int oldDirection = 1;
    private int stuckTicks;
    private int detourTicks;
    private int detourSide = 1;
    private int currentExposure;
    private boolean needsJump;
    private boolean commandedLastTick;
    private int tick;
    private int yieldUntil;

    private record Probe(boolean safe, boolean jump, int exposure) {
        private static final Probe UNSAFE = new Probe(false, false, Integer.MAX_VALUE);
    }

    public void reset() {
        trackedTarget = null;
        direction = null;
        previousPosition = null;
        previousDirection = null;
        cubeCorner = -1;
        oldDirection = 1;
        stuckTicks = 0;
        detourTicks = 0;
        currentExposure = 0;
        needsJump = false;
        commandedLastTick = false;
        tick = 0;
        yieldUntil = 0;
    }

    public void update(LocalPlayer player, LivingEntity target, double radius, int orbitalDirection,
                       boolean front, boolean cube, boolean center, boolean autoJump, double stride) {
        direction = null;
        needsJump = false;
        if (player == null || target == null || !target.isAlive()
                || player.isPassenger() || player.isFallFlying() || player.isInWater()) {
            commandedLastTick = false;
            return;
        }
        if (trackedTarget != target) {
            reset();
            trackedTarget = target;
        }
        tick++;
        if (oldDirection != orbitalDirection) {
            cubeCorner = -1;
            oldDirection = orbitalDirection;
            detourTicks = 0;
        }
        if (player.onGround()) takeoffY = player.getY();
        else if (player.getY() > takeoffY + MAX_AIR_RISE || player.getY() < takeoffY + MIN_AIR_FALL) {
            commandedLastTick = false;
            return;
        }

        if (previousPosition != null && commandedLastTick && player.onGround()) {
            double dx = player.getX() - previousPosition.x;
            double dz = player.getZ() - previousPosition.z;
            stuckTicks = dx * dx + dz * dz < 0.018 * 0.018 ? stuckTicks + 1 : 0;
        } else if (!player.onGround() || !commandedLastTick) {
            stuckTicks = 0;
        }
        previousPosition = player.position();
        commandedLastTick = false;
        if (stuckTicks >= 5) {
            stuckTicks = 0;
            detourSide = -detourSide;
            detourTicks = 12;
            // A repeatedly stuck controller must never monopolize the player.
            if (yieldUntil > 0 && tick - yieldUntil < 30) yieldUntil = tick + 10;
            else yieldUntil = tick - 1;
        }
        if (tick <= yieldUntil) return;

        double minRadius = (target.getBbWidth() + player.getBbWidth()) * 0.5 + 0.18;
        double r = center ? 0 : Math.max(radius, minRadius);
        Vec3 desired = desiredDirection(player, target, r, orbitalDirection, front, cube, center);
        if (desired == null) return;

        Level level = player.level();
        int startingHazard = HazardAvoidance.exposure(level, player.getBoundingBox());
        currentExposure = startingHazard;
        double distance = Math.max(0.32, Math.min(1.0, stride));
        Probe straight = probe(player, desired, distance, startingHazard, autoJump);
        // In open space do not even evaluate alternative headings. In a web,
        // prefer the direction that actually decreases exposure instead.
        if (straight.safe && startingHazard == 0 && detourTicks == 0) {
            select(desired, straight);
            return;
        }

        // After a confirmed stall, deliberately choose a detour rather than
        // issuing the same mathematically safe but physically ineffective step.
        Vec3 best = straight.safe && detourTicks == 0 ? desired : null;
        Probe bestProbe = straight;
        double bestScore = best != null ? score(desired, desired, straight, startingHazard) : -Double.MAX_VALUE;
        int side = detourTicks > 0 ? detourSide : orbitalDirection;
        // Max six additional candidates, and only when the usual orbit is
        // blocked, stuck in a hazard, or a recent detour is being maintained.
        for (int i = 0; i < AVOID_ANGLES.length; i++) {
            double offset = AVOID_ANGLES[i] * side;
            Vec3 candidate = rotate(desired, Math.toRadians(offset));
            Probe p = probe(player, candidate, distance, startingHazard, autoJump);
            if (!p.safe) continue;
            double s = score(desired, candidate, p, startingHazard);
            if (detourTicks > 0) s += Math.signum(offset) * 0.25;
            if (previousDirection != null) s += 0.35 * candidate.dot(previousDirection);
            if (s > bestScore) {
                bestScore = s;
                best = candidate;
                bestProbe = p;
            }
        }
        if (best == null) {
            // No safe local move. No invented path, no zeroing momentum or WASD.
            detourTicks = 0;
            return;
        }
        if (detourTicks > 0) detourTicks--;
        else if (best != desired) {
            detourSide = side;
            detourTicks = 6;
        }
        select(best, bestProbe);
    }

    private void select(Vec3 heading, Probe probe) {
        direction = heading;
        previousDirection = heading;
        needsJump = probe.jump;
        commandedLastTick = true;
    }

    private Vec3 desiredDirection(LocalPlayer player, LivingEntity target, double radius,
                                  int side, boolean front, boolean cube, boolean center) {
        Vec3 current = player.position();
        Vec3 goal = target.position();
        double dx = current.x - goal.x;
        double dz = current.z - goal.z;
        double dist = Math.hypot(dx, dz);
        if (dist < 0.01) { dx = 1; dz = 0; dist = 1; }
        double x;
        double z;
        if (front) {
            double yaw = Math.toRadians(target.getYRot());
            double angle = Math.atan2(Math.cos(yaw), -Math.sin(yaw))
                    + (center && side < 0 ? Math.PI : center ? 0 : side * 0.35);
            x = goal.x + Math.cos(angle) * Math.max(radius, 0.8) - current.x;
            z = goal.z + Math.sin(angle) * Math.max(radius, 0.8) - current.z;
        } else if (center) {
            x = -dx;
            z = -dz;
        } else if (cube) {
            if (cubeCorner < 0) {
                double a = Math.atan2(dz, dx);
                double slot = (a - Math.PI / 4) / (Math.PI / 2);
                cubeCorner = Math.floorMod(side > 0 ? (int) Math.floor(slot) + 1 : (int) Math.ceil(slot) - 1, 4);
            }
            double angle = Math.PI / 4 + cubeCorner * Math.PI / 2;
            double cornerRadius = radius * Math.sqrt(2.0);
            double gx = goal.x + Math.cos(angle) * cornerRadius;
            double gz = goal.z + Math.sin(angle) * cornerRadius;
            if (Math.hypot(current.x - gx, current.z - gz) < 0.60) {
                cubeCorner = Math.floorMod(cubeCorner + side, 4);
                angle = Math.PI / 4 + cubeCorner * Math.PI / 2;
                gx = goal.x + Math.cos(angle) * cornerRadius;
                gz = goal.z + Math.sin(angle) * cornerRadius;
            }
            x = gx - current.x;
            z = gz - current.z;
        } else {
            // Tangential orbit plus a small radial component for target approach.
            double radial = Math.max(-1.4, Math.min(1.4, (radius - dist) * 1.25));
            double tangent = dist > radius + 3 ? 0.30 : 1.0;
            x = -dz / dist * side * tangent + dx / dist * radial;
            z = dx / dist * side * tangent + dz / dist * radial;
        }
        double length = Math.hypot(x, z);
        return length > 0.08 ? new Vec3(x / length, 0, z / length) : null;
    }

    private static Vec3 rotate(Vec3 v, double angle) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return new Vec3(v.x * cos - v.z * sin, 0, v.x * sin + v.z * cos);
    }

    private static double score(Vec3 intended, Vec3 candidate, Probe p, int startingHazard) {
        return intended.dot(candidate) * 3.0
                - (p.jump ? 0.65 : 0)
                - (startingHazard > 0 ? p.exposure * 4.0 : 0);
    }

    private Probe probe(LocalPlayer player, Vec3 heading, double stride, int startHazard, boolean allowJump) {
        Level level = player.level();
        AABB box = player.getBoundingBox();
        // The first point catches thin obstacles; the second verifies the
        // actual velocity step. Capped at three samples regardless of speed.
        int samples = stride > 0.68 ? 3 : 2;
        boolean requiresJump = false;
        int endExposure = startHazard;
        for (int i = 1; i <= samples; i++) {
            double t = stride * i / samples;
            AABB moved = box.move(heading.x * t, 0, heading.z * t);
            if (!loaded(level, moved)) return Probe.UNSAFE;
            // Always test ground-level hazards even when contemplating a jump.
            int exposure = HazardAvoidance.exposure(level, moved);
            if ((startHazard == 0 && exposure > 0) || exposure > startHazard) return Probe.UNSAFE;
            endExposure = exposure;

            boolean clear = level.noCollision(player, moved);
            boolean supported = hasSupport(player, moved, player.onGround() ? player.getY() : takeoffY);
            if (!clear || !supported) {
                if (!allowJump || !player.onGround() || exposure > 0) return Probe.UNSAFE;
                // A single-block step is optional. No gap-jumping or forcing a
                // route into void; the raised position must have real support.
                AABB raised = moved.move(0, 1.0, 0);
                if (!level.noCollision(player, raised)
                        || !level.noCollision(player, box.move(0, 1.0, 0))
                        || !hasSupport(player, raised, player.getY() + 1.0)) return Probe.UNSAFE;
                requiresJump = true;
            }
        }
        return new Probe(true, requiresJump, endExposure);
    }

    private static boolean hasSupport(LocalPlayer player, AABB box, double feetY) {
        double x = (box.minX + box.maxX) * 0.5;
        double z = (box.minZ + box.maxZ) * 0.5;
        AABB feet = new AABB(x - 0.18, feetY - 0.15, z - 0.18,
                             x + 0.18, feetY - 0.012, z + 0.18);
        return !player.level().noCollision(player, feet);
    }

    private static boolean loaded(Level level, AABB box) {
        int y = (int) Math.floor(box.minY);
        return level.hasChunkAt(BlockPos.containing(box.minX, y, box.minZ))
                && level.hasChunkAt(BlockPos.containing(box.maxX, y, box.maxZ))
                && level.hasChunkAt(BlockPos.containing(box.minX, y, box.maxZ))
                && level.hasChunkAt(BlockPos.containing(box.maxX, y, box.minZ));
    }

    public Vec3 direction(LocalPlayer player) {
        return player == null ? null : direction;
    }

    public boolean isTrapped() { return currentExposure > 0; }

    public boolean needsJump(LocalPlayer player) {
        return player != null && needsJump && player.onGround();
    }

    public boolean safeMotion(LocalPlayer player, Vec3 velocity) {
        if (player == null || velocity.horizontalDistanceSqr() < 1.0e-8) return false;
        return probe(player, velocity.normalize(), Math.min(1.0, velocity.horizontalDistance()),
                currentExposure, true).safe;
    }
}
