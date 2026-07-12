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

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.util.entity.simulation.MountedEntityPrediction;
import combatant.client.util.player.simulation.PlayerSimulationCache;

public interface ProjectileTarget {

    static ProjectileTarget forEntity(Entity entity) {
        return new ProjectileTarget() {
            @Override
            public Vec3 getPositionInTicks(double ticks) {
                if (entity.isPassenger()) {
                    return MountedEntityPrediction.predictMountedPosition(entity, (int) Math.round(ticks));
                }

                if (entity instanceof Player player) {
                    return PlayerSimulationCache.getSimulationForOtherPlayers(player)
                            .getSnapshotAt((int) Math.round(ticks))
                            .pos();
                }

                return entity.position().add(entity.getDeltaMovement().scale(ticks));
            }

            @Override
            public AABB getBoxInTicks(double ticks) {
                Vec3 predicted = getPositionInTicks(ticks);
                return entity.getBoundingBox().move(predicted.subtract(entity.position()));
            }
        };
    }

    static ProjectileTarget constant(Vec3 position, AABB box) {
        return new ProjectileTarget() {
            @Override
            public Vec3 getPositionInTicks(double ticks) {
                return position;
            }

            @Override
            public AABB getBoxInTicks(double ticks) {
                return box;
            }
        };
    }

    Vec3 getPositionInTicks(double ticks);

    AABB getBoxInTicks(double ticks);
}
