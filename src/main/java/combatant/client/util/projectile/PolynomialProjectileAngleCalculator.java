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

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import combatant.client.util.aiming.data.Rotation;

public final class PolynomialProjectileAngleCalculator extends ProjectileAngleCalculator {

    public static final PolynomialProjectileAngleCalculator INSTANCE = new PolynomialProjectileAngleCalculator();

    private PolynomialProjectileAngleCalculator() {
    }

    @Override
    public Rotation calculateAngleFor(TrajectoryInfo projectileInfo, Vec3 sourcePos, ProjectileTarget target) {
        Vec3 basePos = target.getPositionInTicks(0.0);
        double estimatedTicksUntilImpact = basePos.distanceTo(sourcePos) / projectileInfo.initialVelocity();
        Vec3 diff = target.getPositionInTicks(estimatedTicksUntilImpact).subtract(sourcePos);

        double horizontalDistance = Math.hypot(diff.x, diff.z);
        double velocity = projectileInfo.initialVelocity();
        double gravity = projectileInfo.gravity();
        double velocity2 = velocity * velocity;
        double velocity4 = velocity2 * velocity2;
        double y = diff.y;

        double sqrtValue = velocity4 - gravity * (gravity * horizontalDistance * horizontalDistance + 2.0 * y * velocity2);
        if (sqrtValue < 0.0) {
            return null;
        }

        double pitchRad = Math.atan((velocity2 - Math.sqrt(sqrtValue)) / (gravity * horizontalDistance));
        double yawRad = Math.atan2(diff.z, diff.x);

        return new Rotation(
                Mth.wrapDegrees((float) Math.toDegrees(yawRad) - 90.0f),
                Mth.wrapDegrees((float) -Math.toDegrees(pitchRad))
        );
    }
}
