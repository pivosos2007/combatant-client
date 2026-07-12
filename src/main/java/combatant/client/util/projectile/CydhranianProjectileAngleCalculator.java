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

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.util.aiming.data.Rotation;

public final class CydhranianProjectileAngleCalculator extends ProjectileAngleCalculator {

    public static final CydhranianProjectileAngleCalculator INSTANCE = new CydhranianProjectileAngleCalculator();

    private CydhranianProjectileAngleCalculator() {
    }

    @Override
    public Rotation calculateAngleFor(TrajectoryInfo projectileInfo, Vec3 sourcePos, ProjectileTarget target) {
        Vec3 lookVec = predictArrowDirection(projectileInfo, sourcePos, target);
        return lookVec != null ? Rotation.fromRotationVec(lookVec) : null;
    }

    private Vec3 getDirectionByTime(TrajectoryInfo trajectoryInfo, Vec3 enemyPosition, Vec3 playerHeadPosition, double time) {
        double initialVelocity = trajectoryInfo.initialVelocity();
        double resistanceFactor = trajectoryInfo.drag();
        double gravity = trajectoryInfo.gravity();
        double resistancePow = Math.pow(resistanceFactor, time);
        double denominator = initialVelocity * (resistancePow - 1.0);

        return new Vec3(
                (enemyPosition.x - playerHeadPosition.x) * (resistanceFactor - 1.0) / denominator,
                (enemyPosition.y - playerHeadPosition.y) * (resistanceFactor - 1.0) / denominator
                        + gravity * (resistancePow - resistanceFactor * time + time - 1.0)
                        / (initialVelocity * (resistanceFactor - 1.0) * (resistancePow - 1.0)),
                (enemyPosition.z - playerHeadPosition.z) * (resistanceFactor - 1.0) / denominator
        );
    }

    private Vec3 getVelocityOnImpact(TrajectoryInfo trajectoryInfo, double ticksPassed, Vec3 initialDir) {
        double dx = initialDir.x;
        double dy = initialDir.y;
        double dz = initialDir.z;
        double drag = trajectoryInfo.drag();
        double velocity = trajectoryInfo.initialVelocity();
        double gravity = trajectoryInfo.gravity();
        double t = ticksPassed;
        double dragMinusOne = drag - 1.0;
        double dragPow = Math.pow(drag, t);
        double lnDrag = Math.log(drag);

        return new Vec3(
                (dx * dragPow * lnDrag * velocity) / dragMinusOne,
                (dy * dragMinusOne * dragPow * lnDrag * velocity - gravity * (dragPow * lnDrag - drag + 1.0))
                        / (dragMinusOne * dragMinusOne),
                (dz * dragPow * lnDrag * velocity) / dragMinusOne
        );
    }

    private Double calculatePossibleTravelTimeToTarget(
            TrajectoryInfo trajectoryInfo,
            Vec3 playerHeadPosition,
            ProjectileTarget target,
            Vec3 defaultBoxOffset
    ) {
        double distance = target.getPositionInTicks(0.0).subtract(playerHeadPosition).length();
        double maxTravelTime = distance / trajectoryInfo.initialVelocity() * 1.75;

        ProjectileMath.MinimumResult minimum = ProjectileMath.findFunctionMinimumByBisect(
                0.0,
                maxTravelTime,
                1.0E-4,
                ticks -> {
                    Vec3 newLimit = getDirectionByTime(
                            trajectoryInfo,
                            target.getPositionInTicks(ticks).add(defaultBoxOffset),
                            playerHeadPosition,
                            ticks
                    );
                    return Math.abs(newLimit.length() - 1.0);
                }
        );

        if (minimum.y() > 1.0E-1) {
            return null;
        }

        return minimum.x();
    }

    private Vec3 predictArrowDirection(TrajectoryInfo trajectoryInfo, Vec3 playerHeadPosition, ProjectileTarget target) {
        AABB initialBox = target.getBoxInTicks(0.0);
        Vec3 defaultBoxOffset = initialBox.getCenter().subtract(target.getPositionInTicks(0.0));

        Double ticksUntilImpact = calculatePossibleTravelTimeToTarget(
                trajectoryInfo,
                playerHeadPosition,
                target,
                defaultBoxOffset
        );
        if (ticksUntilImpact == null) {
            return null;
        }

        Vec3 entityPositionOnImpact = target.getPositionInTicks(ticksUntilImpact);
        Vec3 finalDirection = getDirectionByTime(
                trajectoryInfo,
                entityPositionOnImpact.add(defaultBoxOffset),
                playerHeadPosition,
                ticksUntilImpact
        );

        Vec3 directionOnImpact = getVelocityOnImpact(trajectoryInfo, ticksUntilImpact, finalDirection).normalize();
        AABB targetBox = target.getBoxInTicks(ticksUntilImpact).inflate(trajectoryInfo.hitboxRadius());
        Vec3 finalTargetPos = ProjectileTargetPointFinder.findHittablePosition(
                playerHeadPosition,
                directionOnImpact,
                entityPositionOnImpact,
                targetBox
        );
        if (finalTargetPos == null) {
            return null;
        }

        return getDirectionByTime(trajectoryInfo, finalTargetPos, playerHeadPosition, Math.rint(ticksUntilImpact));
    }
}
