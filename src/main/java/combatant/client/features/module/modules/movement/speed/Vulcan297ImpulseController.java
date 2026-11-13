/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement.speed;

import net.minecraft.world.phys.Vec3;

/** Tracks brief teleport-related impulse windows and limits repeated horizontal boosts. */
public final class Vulcan297ImpulseController {
    public enum Source { PEARL, CHORUS, TELEPORT }

    private static final double FRICTION = 0.9100000262260437D;
    private static final double FRICTION_ALLOWANCE = 0.026D;
    private static final double FRICTION_BUFFER_MAX = 5.0D;
    private static final double FRICTION_BUFFER_DECAY = 0.125D;
    private static final double FRICTION_RESERVE = 1.0D;

    private Source source;
    private Vec3 target;
    private int ticksRemaining;
    private int totalTicks;
    private int delay;
    private int cooldown;
    private int gaugeTicks;
    private int lastTick = Integer.MIN_VALUE;
    private int lastMoveTick = Integer.MIN_VALUE;
    private double lastHorizontal;
    private double frictionBuffer;

    public void reset() {
        source = null;
        target = null;
        ticksRemaining = 0;
        totalTicks = 0;
        delay = 0;
        cooldown = 0;
        gaugeTicks = 0;
        lastTick = Integer.MIN_VALUE;
        lastMoveTick = Integer.MIN_VALUE;
        lastHorizontal = 0.0D;
        frictionBuffer = 0.0D;
    }

    public void tick(int currentTick) {
        if (lastTick == currentTick) return;
        if (lastTick != Integer.MIN_VALUE && (currentTick < lastTick || currentTick - lastTick > 5)) {
            reset();
        }
        if (lastMoveTick != Integer.MIN_VALUE && lastMoveTick < currentTick - 1) {
            int skipped = Math.min(20, currentTick - lastMoveTick - 1);
            frictionBuffer = Math.max(0.0D, frictionBuffer - FRICTION_BUFFER_DECAY * skipped);
            lastHorizontal = 0.0D;
        }
        lastTick = currentTick;
        if (cooldown > 0) cooldown--;
        if (gaugeTicks > 0) gaugeTicks--;
        if (delay > 0) {
            delay--;
            return;
        }
        if (ticksRemaining > 0) {
            ticksRemaining--;
            if (ticksRemaining == 0) {
                source = null;
                target = null;
            }
        }
    }

    public void arm(Source next, Vec3 nextTarget) {
        if (next == null || cooldown > 0) return;
        source = next;
        target = nextTarget;
        totalTicks = next == Source.TELEPORT ? 4 : 5;
        ticksRemaining = totalTicks;
        delay = next == Source.TELEPORT ? 1 : 0;
        lastMoveTick = Integer.MIN_VALUE;
        lastHorizontal = 0.0D;
    }

    public void correction() {
        source = null;
        target = null;
        ticksRemaining = 0;
        totalTicks = 0;
        delay = 0;
        cooldown = 35;
        frictionBuffer = Math.max(frictionBuffer, 4.0D);
    }

    public Vec3 target() {
        return active() ? target : null;
    }

    public boolean active() {
        return source != null && ticksRemaining > 0 && delay == 0 && cooldown == 0;
    }

    public double prepare(double horizontal, int tick) {
        if (lastMoveTick == tick) return 0.0D;
        lastMoveTick = tick;
        if (!Double.isFinite(horizontal) || horizontal < 0.0D) return 0.0D;

        double predicted = lastHorizontal * FRICTION + FRICTION_ALLOWANCE;
        if (!active()) {
            record(horizontal, predicted);
            return 0.0D;
        }

        double peak = switch (source) {
            case PEARL -> 3.35D;
            case CHORUS -> 3.05D;
            case TELEPORT -> 2.80D;
        };
        double progress = (double) ticksRemaining / totalTicks;
        double desired = peak * (0.58D + 0.42D * progress);
        if (!canBoost()) {
            record(horizontal, predicted);
            return 0.0D;
        }
        if (desired <= horizontal + 0.05D) {
            record(horizontal, predicted);
            return 0.0D;
        }
        record(desired, predicted);
        gaugeTicks = 10;
        return desired;
    }

    public void observeUnmodified(double horizontal, int tick) {
        if (lastMoveTick == tick || !Double.isFinite(horizontal) || horizontal < 0.0D) return;
        lastMoveTick = tick;
        record(horizontal, lastHorizontal * FRICTION + FRICTION_ALLOWANCE);
    }

    private void record(double horizontal, double predicted) {
        if (horizontal > 0.25D && horizontal > predicted + 1.0E-5D) {
            frictionBuffer = Math.min(FRICTION_BUFFER_MAX, frictionBuffer + 1.0D);
        } else {
            frictionBuffer = Math.max(0.0D, frictionBuffer - FRICTION_BUFFER_DECAY);
        }
        lastHorizontal = horizontal;
    }

    public float available() {
        return (float) Math.max(0.0D, Math.min(1.0D, 1.0D - frictionBuffer / FRICTION_BUFFER_MAX));
    }

    public float consumptionThreshold() {
        return (float) (FRICTION_RESERVE / FRICTION_BUFFER_MAX);
    }

    public boolean canBoost() {
        return frictionBuffer < FRICTION_BUFFER_MAX - FRICTION_RESERVE;
    }

    public boolean visible() {
        return gaugeTicks > 0;
    }
}
