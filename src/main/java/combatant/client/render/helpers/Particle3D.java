/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.helpers;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import combatant.client.util.time.Timer;

public class Particle3D {
    private final Timer timer = new Timer();
    private final long lifeMs;
    private final long fadeInMs;
    private final long fadeOutMs;
    private final float size;
    private final int color;
    private final Identifier texture;
    private final int textureIndex;
    private final float drag;
    private final float gravity;
    private final float rotationSpeed;
    private Vec3 pos;
    private Vec3 prevPos;
    private Vec3 velocity;
    private float rotation;

    public Particle3D(Vec3 pos,
                      Vec3 velocity,
                      long lifeMs,
                      long fadeInMs,
                      long fadeOutMs,
                      float size,
                      int color,
                      Identifier texture,
                      int textureIndex,
                      float drag,
                      float gravity,
                      float rotationSpeed) {
        this.pos = pos;
        this.prevPos = pos;
        this.velocity = velocity;
        this.lifeMs = Math.max(1L, lifeMs);
        this.fadeInMs = Math.max(0L, fadeInMs);
        this.fadeOutMs = Math.max(0L, fadeOutMs);
        this.size = size;
        this.color = color;
        this.texture = texture;
        this.textureIndex = textureIndex;
        this.drag = drag;
        this.gravity = gravity;
        this.rotationSpeed = rotationSpeed;
    }

    public boolean update() {
        prevPos = pos;
        pos = pos.add(velocity);
        velocity = velocity.multiply(drag, drag, drag).add(0.0, -gravity, 0.0);
        rotation += rotationSpeed;
        return timer.passedMs(lifeMs);
    }

    public Vec3 interpolate(float tickDelta) {
        return prevPos.lerp(pos, tickDelta);
    }

    public float alpha() {
        long age = timer.getPassedTimeMs();
        float a = 1.0f;
        if (fadeInMs > 0L) {
            a = Math.min(a, age / (float) fadeInMs);
        }
        if (fadeOutMs > 0L) {
            long start = Math.max(0L, lifeMs - fadeOutMs);
            if (age >= start) {
                float out = (lifeMs - age) / (float) fadeOutMs;
                a = Math.min(a, out);
            }
        }
        return Mth.clamp(a, 0.0f, 1.0f);
    }

    public float size() {
        return size;
    }

    public int color() {
        return color;
    }

    public Identifier texture() {
        return texture;
    }

    public int textureIndex() {
        return textureIndex;
    }

    public float rotation() {
        return rotation;
    }
}
