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

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.util.aiming.data.Rotation;

public abstract class ProjectileAngleCalculator {

    public abstract Rotation calculateAngleFor(TrajectoryInfo projectileInfo, Vec3 sourcePos, ProjectileTarget target);

    public Rotation calculateAngleForStaticTarget(TrajectoryInfo projectileInfo, Vec3 targetPos, AABB targetBox, Vec3 sourcePos) {
        return calculateAngleFor(projectileInfo, sourcePos, ProjectileTarget.constant(targetPos, targetBox));
    }

    public Rotation calculateAngleForEntity(TrajectoryInfo projectileInfo, LivingEntity entity, Vec3 sourcePos) {
        return calculateAngleFor(projectileInfo, sourcePos, ProjectileTarget.forEntity(entity));
    }

    public Rotation calculateAngleForEntity(TrajectoryInfo projectileInfo, LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : calculateAngleForEntity(projectileInfo, entity, mc.player.getEyePosition());
    }
}
