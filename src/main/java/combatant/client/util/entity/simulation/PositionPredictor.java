/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.entity.simulation;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Simple position extrapolation based on current velocity.
 */
public enum PositionPredictor {
    ;

    private static final Map<Entity, State> STATES = new WeakHashMap<>();

    public static Vec3 predictEntityPos(Entity entity, double ticks) {
        if (entity == null || ticks <= 0) {
            return entity != null ? entity.position() : Vec3.ZERO;
        }
        State state = updateState(entity);
        Vec3 base = pickBaseMovement(entity, state);
        Vec3 accel = state.prevMovement != null
                ? state.lastMovement.subtract(state.prevMovement)
                : Vec3.ZERO;

        double t = ticks;
        Vec3 predicted = state.lastPos.add(base.scale(t));
        if (state.prevMovement != null) {
            predicted = predicted.add(accel.scale(0.5 * t * t));
        }
        return predicted;
    }

    public static Vec3 predictEyePos(LivingEntity entity, double ticks) {
        if (entity == null) return Vec3.ZERO;
        Vec3 pos = predictEntityPos(entity, ticks);
        Vec3 eyeOffset = entity.getEyePosition().subtract(entity.position());
        return pos.add(eyeOffset);
    }

    public static AABB predictBox(Entity entity, double ticks) {
        if (entity == null) return new AABB(0, 0, 0, 0, 0, 0);
        Vec3 predicted = predictEntityPos(entity, ticks);
        Vec3 current = entity.position();
        Vec3 delta = predicted.subtract(current);
        return entity.getBoundingBox().move(delta);
    }

    private static State updateState(Entity entity) {
        State state = STATES.get(entity);
        if (state == null) {
            state = new State();
            state.ref = new WeakReference<>(entity);
            STATES.put(entity, state);
        }

        int age = entity.tickCount;
        if (state.lastAge != age) {
            state.lastAge = age;
            state.lastPos = entity.position();
            state.prevMovement = state.lastMovement;
            state.lastMovement = entity.getKnownMovement();
            state.lastVelocity = entity.getDeltaMovement();
        }

        return state;
    }

    private static Vec3 pickBaseMovement(Entity entity, State state) {
        Vec3 movement = state.lastMovement != null ? state.lastMovement : entity.getKnownMovement();
        Vec3 velocity = state.lastVelocity != null ? state.lastVelocity : entity.getDeltaMovement();

        double moveLen = movement.lengthSqr();
        double velLen = velocity.lengthSqr();
        if (moveLen < 1.0e-6 && velLen > moveLen) {
            return velocity;
        }
        return movement;
    }

    private static final class State {
        private WeakReference<Entity> ref;
        private int lastAge = Integer.MIN_VALUE;
        private Vec3 lastPos = Vec3.ZERO;
        private Vec3 lastMovement = Vec3.ZERO;
        private Vec3 prevMovement;
        private Vec3 lastVelocity = Vec3.ZERO;
    }
}
