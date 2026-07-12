/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.math;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import combatant.client.render.engine.RenderState;

public enum RenderMath {
    ;

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static Vec3 getLerpedPos(Entity e, float tickDelta) {
        return e.getPosition(tickDelta);
    }

    public static Vec3 getLerpedPosRelative(Entity e, float tickDelta) {
        Vec3 p = getLerpedPos(e, tickDelta);
        return p.subtract(RenderState.cameraPos);
    }

    public static float smoothLerp(float current, float target, float dt, float speed) {
        if (current < 0.0f) return target;
        float t = 1.0f - (float) Math.exp(-speed * Math.max(0.0f, dt));
        return Mth.lerp(t, current, target);
    }
}
