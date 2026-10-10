/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.helpers;

import net.minecraft.world.phys.Vec3;

/** Fixed history sample for Trails. The world position never moves after capture. */
public final class TrailPoint {
    private final Vec3 position;
    private final int color;
    private final int maxTicks;
    private int ticks;
    private int prevTicks;

    public TrailPoint(Vec3 position, int color, int lifetimeTicks) {
        if (position == null) throw new IllegalArgumentException("Trail position must not be null");
        this.position = position;
        this.color = color;
        this.maxTicks = Math.max(1, lifetimeTicks);
        this.ticks = this.maxTicks;
        this.prevTicks = this.ticks;
    }

    public Vec3 position() {
        return position;
    }

    public float animation(float pt) {
        return (float) ((prevTicks + (ticks - prevTicks) * pt) / (double) maxTicks);
    }

    public boolean update() {
        prevTicks = ticks;
        return ticks-- <= 0;
    }

    public int color() {
        return color;
    }
}
