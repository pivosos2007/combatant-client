/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.helpers;

import net.minecraft.world.phys.Vec3;

public class TrailPoint {
    private final Vec3 from;
    private final Vec3 to;
    private final int color;
    private final int maxTicks;
    private int ticks;
    private int prevTicks;

    public TrailPoint(Vec3 from, Vec3 to, int color, int lifetimeTicks) {
        this.from = from;
        this.to = to;
        this.color = color;
        this.maxTicks = Math.max(1, lifetimeTicks);
        this.ticks = this.maxTicks;
        this.prevTicks = this.ticks;
    }

    public Vec3 interpolate(float pt) {
        double x = from.x + ((to.x - from.x) * pt);
        double y = from.y + ((to.y - from.y) * pt);
        double z = from.z + ((to.z - from.z) * pt);
        return new Vec3(x, y, z);
    }

    public float animation(float pt) {
        return (float) ((this.prevTicks + (this.ticks - this.prevTicks) * pt) / (double) maxTicks);
    }

    public Vec3 from() {
        return from;
    }

    public Vec3 to() {
        return to;
    }

    public boolean update() {
        this.prevTicks = this.ticks;
        return this.ticks-- <= 0;
    }

    public int color() {
        return color;
    }
}
