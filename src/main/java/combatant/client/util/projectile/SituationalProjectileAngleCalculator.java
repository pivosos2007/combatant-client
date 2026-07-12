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

import net.minecraft.world.phys.Vec3;
import combatant.client.util.aiming.data.Rotation;

public final class SituationalProjectileAngleCalculator extends ProjectileAngleCalculator {

    public static final SituationalProjectileAngleCalculator INSTANCE = new SituationalProjectileAngleCalculator();

    private SituationalProjectileAngleCalculator() {
    }

    @Override
    public Rotation calculateAngleFor(TrajectoryInfo projectileInfo, Vec3 sourcePos, ProjectileTarget target) {
        Vec3 basePos = target.getPositionInTicks(0.0);
        ProjectileAngleCalculator implementation = basePos.distanceToSqr(sourcePos) < 25.0
                ? PolynomialProjectileAngleCalculator.INSTANCE
                : CydhranianProjectileAngleCalculator.INSTANCE;
        return implementation.calculateAngleFor(projectileInfo, sourcePos, target);
    }
}
