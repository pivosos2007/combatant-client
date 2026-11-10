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

package combatant.client.util.entity.simulation;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Binds a single entity to the position, eye-position and bounding-box predictions
 * of {@link PositionPredictor}. Each query uses the predictor's current sample state.
 */
public enum PositionExtrapolation {
    ;

    public static Extrapolation getBestForEntity(Entity entity) {
        return new Extrapolation(entity);
    }

    public static final class Extrapolation {
        private final Entity entity;

        private Extrapolation(Entity entity) {
            this.entity = entity;
        }

        public Vec3 getPositionInTicks(double ticks) {
            return PositionPredictor.predictEntityPos(entity, ticks);
        }

        public Vec3 getEyePositionInTicks(double ticks) {
            if (entity instanceof LivingEntity living) {
                return PositionPredictor.predictEyePos(living, ticks);
            }
            return getPositionInTicks(ticks);
        }

        public AABB getBoxInTicks(double ticks) {
            return PositionPredictor.predictBox(entity, ticks);
        }
    }
}
