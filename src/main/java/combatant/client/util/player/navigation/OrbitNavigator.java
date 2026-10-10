/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.player.navigation;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A fast continuous orbit controller with bounded local path planning as the fallback.
 * Free ground should never depend on successfully finding an A* goal on a block grid.
 */
public final class OrbitNavigator {
    private static final int REPLAN_INTERVAL = 5;
    private static final int SEARCH_RANGE = 13;
    private static final int SEARCH_BUDGET = 450;
    private static final double WAYPOINT_RADIUS = 0.43;

    private List<LocalWalkPathfinder.Node> path = List.of();
    private int waypoint;
    private int lastPlanTick = -1000;
    private int ticks;
    private int stuckTicks;
    private Vec3 previousPlayerPosition;
    private double steeringDistance;
    private Vec3 continuousDirection;
    private Vec3 lastGroundOrbitDirection;
    private double lastGroundOrbitY;
    private LivingEntity trackedTarget;
    private Vec3 plannedTarget = Vec3.ZERO;
    private int plannedDirection;
    private double plannedRadius;
    private boolean plannedFront;
    private boolean plannedCube;
    private boolean plannedCenter;
    private boolean plannedJump;
    private float plannedTargetYaw;

    public void reset() {
        path = List.of();
        waypoint = 0;
        lastPlanTick = -1000;
        stuckTicks = 0;
        previousPlayerPosition = null;
        steeringDistance = 0.0;
        continuousDirection = null;
        lastGroundOrbitDirection = null;
        trackedTarget = null;
    }

    public void update(LocalPlayer player, LivingEntity target, double radius, int direction,
                       boolean front, boolean cube, boolean center, boolean allowJump) {
        ticks++;
        if (player == null || target == null || !target.isAlive()) {
            reset();
            return;
        }
        if (trackedTarget != target) {
            reset();
            trackedTarget = target;
        }
        Vec3 goalPosition = target.position();
        double minimumRadius = target.getBbWidth() * 0.5 + player.getBbWidth() * 0.5 + 0.18;
        double effectiveRadius = center ? Math.max(0.95, minimumRadius) : Math.max(radius, minimumRadius);
        boolean changed = direction != plannedDirection || Math.abs(plannedRadius - effectiveRadius) > 0.05
                || front != plannedFront || cube != plannedCube || center != plannedCenter || allowJump != plannedJump;
        boolean moved = goalPosition.distanceToSqr(plannedTarget) > 0.55 * 0.55;
        plannedTarget = goalPosition;
        plannedDirection = direction;
        plannedRadius = effectiveRadius;
        plannedFront = front;
        plannedCube = cube;
        plannedCenter = center;
        plannedJump = allowJump;
        plannedTargetYaw = target.getYRot();

        trackMotion(player);
        continuousDirection = null;
        if (HazardAvoidance.exposure(player.level(), player.getBoundingBox()) > 0) {
            // A player already inside a web/fire must be able to EXIT it; the normal
            // pathfinder intentionally rejects all hazard-containing standing nodes.
            continuousDirection = escapeHazard(player);
        } else if (!player.horizontalCollision || stuckTicks < 4) {
            continuousDirection = directOrbit(player);
            if (continuousDirection != null && player.onGround()) {
                lastGroundOrbitY = player.getY();
                lastGroundOrbitDirection = continuousDirection;
            }
        }
        if (continuousDirection != null) {
            steeringDistance = 1.0;
            // The fast path is already validated against collisions and edges.
            // Do not require a discretized goal to exist before orbiting.
            path = List.of();
            waypoint = 0;
            return;
        }
        if (!player.onGround() && !changed && waypoint < path.size()) return;
        advance(player);
        boolean finished = waypoint >= path.size();
        if (!finished && !changed && !moved && stuckTicks < 8) return;
        if (!changed && ticks - lastPlanTick < REPLAN_INTERVAL) return;
        lastPlanTick = ticks;
        path = List.of();
        waypoint = 0;

        double angle = Math.atan2(player.getZ() - goalPosition.z, player.getX() - goalPosition.x);
        double desired = front
                ? Math.atan2(Math.cos(Math.toRadians(target.getYRot())),
                        -Math.sin(Math.toRadians(target.getYRot()))) + direction * 0.35
                : angle + direction * 0.90;
        if (cube) desired = Math.round(desired / (Math.PI * 0.5)) * (Math.PI * 0.5);
        LocalWalkPathfinder.Goal goal = new LocalWalkPathfinder.Goal(
                goalPosition.x, goalPosition.y, goalPosition.z, effectiveRadius, desired, direction, !center);
        LocalWalkPathfinder.Node start = new LocalWalkPathfinder.Node(
                (int) Math.floor(player.getX()), (int) Math.floor(player.getY() + 0.05),
                (int) Math.floor(player.getZ()));
        path = LocalWalkPathfinder.search(start, goal, new MinecraftWalkTerrain(player),
                SEARCH_RANGE, SEARCH_BUDGET, allowJump);
        waypoint = path.size() > 1 ? 1 : path.size();
        advance(player);
    }

    private void trackMotion(LocalPlayer player) {
        if (previousPlayerPosition != null && player.onGround()) {
            double dx = player.getX() - previousPlayerPosition.x;
            double dz = player.getZ() - previousPlayerPosition.z;
            stuckTicks = dx * dx + dz * dz < 0.018 * 0.018 ? stuckTicks + 1 : 0;
        } else if (!player.onGround()) {
            stuckTicks = 0;
        }
        previousPlayerPosition = player.position();
    }

    private void advance(LocalPlayer player) {
        while (waypoint < path.size()) {
            LocalWalkPathfinder.Node next = path.get(waypoint);
            double distance = Math.hypot(next.centerX() - player.getX(), next.centerZ() - player.getZ());
            if (distance >= WAYPOINT_RADIUS || Math.abs(player.getY() - next.y()) > 0.85) break;
            waypoint++;
        }
    }

    public Vec3 direction(LocalPlayer player) {
        if (continuousDirection != null) return continuousDirection;
        steeringDistance = 0.0;
        if (player == null || waypoint >= path.size()) return null;
        LocalWalkPathfinder.Node node = path.get(waypoint);
        double dx = node.centerX() - player.getX();
        double dz = node.centerZ() - player.getZ();
        steeringDistance = Math.hypot(dx, dz);
        return steeringDistance < 0.05 ? null : new Vec3(dx / steeringDistance, 0.0, dz / steeringDistance);
    }

    private Vec3 directOrbit(LocalPlayer player) {
        boolean airborneOrbit = !player.onGround() && lastGroundOrbitDirection != null
                && player.getY() >= lastGroundOrbitY - 0.25
                && player.getY() <= lastGroundOrbitY + 1.45;
        if (!player.onGround() && !airborneOrbit) return null;
        double dx = player.getX() - plannedTarget.x;
        double dz = player.getZ() - plannedTarget.z;
        double distance = Math.hypot(dx, dz);
        if (distance < 0.01) distance = 0.01;
        double ux = dx / distance;
        double uz = dz / distance;
        Vec3 desired;
        if (plannedCube || plannedFront || plannedCenter) {
            double goalX;
            double goalZ;
            if (plannedFront) {
                double front = Math.toRadians(plannedTargetYaw);
                double angle = Math.atan2(Math.cos(front), -Math.sin(front))
                        + (plannedCenter ? 0.0 : plannedDirection * 0.35);
                goalX = plannedTarget.x + Math.cos(angle) * plannedRadius;
                goalZ = plannedTarget.z + Math.sin(angle) * plannedRadius;
            } else if (plannedCenter) {
                goalX = plannedTarget.x;
                goalZ = plannedTarget.z;
            } else {
                double angle = Math.atan2(dz, dx);
                double quarter = Math.PI * 0.5;
                double sector = (angle - Math.PI * 0.25) / quarter;
                int next = plannedDirection > 0
                        ? (int) Math.floor(sector) + 1
                        : (int) Math.ceil(sector) - 1;
                double corner = Math.PI * 0.25 + next * quarter;
                goalX = plannedTarget.x + Math.copySign(plannedRadius, Math.cos(corner));
                goalZ = plannedTarget.z + Math.copySign(plannedRadius, Math.sin(corner));
            }
            double towardX = goalX - player.getX();
            double towardZ = goalZ - player.getZ();
            double length = Math.hypot(towardX, towardZ);
            if (length < 0.18) return null;
            desired = new Vec3(towardX / length, 0.0, towardZ / length);
        } else {
            // Constant tangential velocity with proportional radial correction.
            // If farther than the desired ring, converge before resuming the orbit.
            double radial = Math.max(-1.5, Math.min(1.5,
                    (plannedRadius - distance) * 1.20));
            double tangential = distance > plannedRadius + 3.0 ? 0.30 : 1.0;
            desired = new Vec3(-uz * plannedDirection * tangential + ux * radial, 0.0,
                    ux * plannedDirection * tangential + uz * radial).normalize();
        }
        MinecraftWalkTerrain terrain = new MinecraftWalkTerrain(player);
        // Look ahead by one to two ticks, not all the way to a grid waypoint.
        // Longer probes stopped an otherwise valid orbit around a nearby corner.
        if (!safeMotion(terrain, player, desired.scale(0.60), true)) return null;
        if (airborneOrbit) {
            // During a bunny hop, check future LANDING support at the takeoff
            // height as well as collision clearance at the current flight height.
            for (double d = 0.15; d <= 0.60; d += 0.15) {
                if (!terrain.safeMotionPose(player.getX() + desired.x * d,
                        lastGroundOrbitY, player.getZ() + desired.z * d, true)) return null;
            }
        }
        return desired;
    }

    private Vec3 escapeHazard(LocalPlayer player) {
        MinecraftWalkTerrain terrain = new MinecraftWalkTerrain(player);
        double bestScore = Double.NEGATIVE_INFINITY;
        Vec3 best = null;
        double towardX = player.getX() - plannedTarget.x;
        double towardZ = player.getZ() - plannedTarget.z;
        double len = Math.hypot(towardX, towardZ);
        double preferredX = len > 0.01 ? -towardZ / len * plannedDirection : 1.0;
        double preferredZ = len > 0.01 ? towardX / len * plannedDirection : 0.0;
        int initial = HazardAvoidance.exposure(player.level(), player.getBoundingBox());
        for (int i = 0; i < 16; i++) {
            double angle = i * (Math.PI * 2.0 / 16.0);
            Vec3 heading = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
            if (!safeMotion(terrain, player, heading.scale(0.30), true)) continue;
            // Prefer headings that leave the hazardous region, not simply directions
            // toward the target (which can be behind a cobweb or lava).
            double exposure = 0;
            boolean canExit = true;
            for (double d : new double[]{0.40, 0.80, 1.20}) {
                if (!safeMotion(terrain, player, heading.scale(d), true)) {
                    canExit = false;
                    break;
                }
                exposure += HazardAvoidance.exposure(player.level(), player.getBoundingBox()
                        .move(heading.x * d, 0.0, heading.z * d));
            }
            if (!canExit) continue;
            double score = (initial * 3.0 - exposure) * 4.0
                    + heading.x * preferredX + heading.z * preferredZ;
            if (score > bestScore) {
                bestScore = score;
                best = heading;
            }
        }
        return best;
    }

    public double waypointDistance(LocalPlayer player) {
        return steeringDistance;
    }

    public boolean needsJump(LocalPlayer player) {
        return player != null && continuousDirection == null && waypoint < path.size()
                && path.get(waypoint).y() > player.getY() + 0.55;
    }

    public void invalidate() {
        path = List.of();
        waypoint = 0;
        steeringDistance = 0.0;
        continuousDirection = null;
        lastPlanTick = -1000;
    }

    public boolean safeMotion(LocalPlayer player, Vec3 velocity) {
        return player != null && safeMotion(new MinecraftWalkTerrain(player), player, velocity, false);
    }

    private boolean safeMotion(MinecraftWalkTerrain terrain, LocalPlayer player, Vec3 velocity, boolean direct) {
        int samples = Math.max(2, (int) Math.ceil(velocity.horizontalDistance() / 0.12));
        // The jump happens during the tick; do not try to cross a solid rise at
        // ground-level before the upward velocity has taken effect.
        if (!direct && needsJump(player) && player.onGround()) return false;
        for (int i = 1; i <= samples; i++) {
            double t = (double) i / samples;
            if (!terrain.safeMotionPose(player.getX() + velocity.x * t,
                    player.getY(), player.getZ() + velocity.z * t, player.onGround())) return false;
        }
        return true;
    }
}
