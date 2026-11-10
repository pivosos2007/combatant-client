/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.movement.jesus;

/**
 * Solid-derived water support with separate stationary and moving states.
 *
 * <p>Server Jesus A looks for water ground movement above ~0.1 blocks/tick;
 * Jesus B also looks for near-zero Y movement combined with horizontal movement.
 * A stationary Solid anchor must therefore never be reused for moving support.
 * The moving state leaves ground to vanilla and applies only vertical buoyancy,
 * not horizontal acceleration or synthetic jumps. There is no client-side
 * representation of Vulcan's authoritative buffers or exemption timers.</p>
 */
public final class Vulcan297JesusController {
    public static final int CHECK_A = 1;
    public static final int CHECK_B = 2;
    public static final int CHECK_C = 4;
    public static final int CHECK_D = 8;
    public static final int CHECK_E = 16;
    public static final int ALL_CHECKS = CHECK_A | CHECK_B | CHECK_C | CHECK_D | CHECK_E;

    private static final double ANCHOR_BELOW = 0.085D;
    private static final double ANCHOR_ABOVE = 0.045D;
    private static final double ANCHOR_MAX_HORIZONTAL = 0.075D;
    private static final double MOVING_DEEP = 0.075D;
    private static final double MOVING_HIGH = -0.035D;
    private static final double FLOAT_UP = 0.042D;
    private static final double FLOAT_DOWN = -0.042D;
    private static final double DEEP_LIFT_MIN = 0.12D;
    private static final double DEEP_LIFT_MAX = 0.20D;
    private static final double FAR_ABOVE = -0.32D;
    private static final int CORRECTION_RELEASE_TICKS = 12;

    // Jesus E computes a dynamic horizontal bound from a 0.166f baseline.
    // Keep headroom for floating-point and packet-position differences.
    private static final double SURFACE_HORIZONTAL_BUDGET = 0.145D;

    /** World-space water surface; NaN means there is no observed nearby water. */
    public record Sample(double waterLevel, double feetY, double verticalY,
                         double horizontalSpeed, boolean movingInput,
                         boolean jump, boolean headSubmerged, boolean fullySubmerged,
                         boolean physicalSupport, boolean nearSolid,
                         boolean bubbleColumn, boolean dolphinsGrace, boolean depthStrider) {
        public double depth() {
            return waterLevel - feetY;
        }

        public boolean waterNearby() {
            return Double.isFinite(waterLevel) && Double.isFinite(feetY)
                    && Double.isFinite(verticalY) && Double.isFinite(horizontalSpeed)
                    && depth() >= FAR_ABOVE;
        }

        public boolean moving() {
            return movingInput || horizontalSpeed > ANCHOR_MAX_HORIZONTAL;
        }
    }

    /** Only affects vertical dynamics / stationary surface alignment. */
    public record Decision(boolean snapToSurface, double anchorY, boolean ground,
                           boolean releaseGround, boolean overrideY, double verticalY) {
        public static final Decision NONE = new Decision(false, 0.0D, false, false, false, 0.0D);
    }

    private boolean anchored;
    private boolean jumpWasDown;
    private boolean jumpAirborne;
    private boolean floatUp;
    private int jumpReleaseTicks;
    private int correctionRelease;
    private double movementTickStartX = Double.NaN;
    private double movementTickStartZ = Double.NaN;

    /** XZ component of the movement passed to Minecraft's collision solver. */
    public record HorizontalMovement(double x, double z) {}

    /** Reset the movement budget once at the start of a client movement tick. */
    public void beginMovementTick(double x, double z) {
        movementTickStartX = x;
        movementTickStartZ = z;
    }

    /**
     * Limit the actual requested horizontal displacement, not the velocity
     * predicted earlier in the tick. Several SELF moves must share one budget.
     * The direction is preserved rather than snapping the player backwards.
     */
    public HorizontalMovement limitSurfaceMovement(double playerX, double playerZ,
                                                   double requestedX, double requestedZ) {
        if (!Double.isFinite(movementTickStartX) || !Double.isFinite(movementTickStartZ)) {
            return new HorizontalMovement(requestedX, requestedZ);
        }
        return limitHorizontalDisplacement(playerX - movementTickStartX,
                playerZ - movementTickStartZ, requestedX, requestedZ);
    }

    /** Pure math for the per-tick surface movement budget. */
    public static HorizontalMovement limitHorizontalDisplacement(double usedX, double usedZ,
                                                                double requestedX, double requestedZ) {
        HorizontalMovement requested = new HorizontalMovement(requestedX, requestedZ);
        if (!Double.isFinite(usedX) || !Double.isFinite(usedZ)
                || !Double.isFinite(requestedX) || !Double.isFinite(requestedZ)) {
            return requested;
        }
        double movingSq = requestedX * requestedX + requestedZ * requestedZ;
        if (movingSq < 1.0E-16D) return requested;

        double maxSq = SURFACE_HORIZONTAL_BUDGET * SURFACE_HORIZONTAL_BUDGET;
        double usedSq = usedX * usedX + usedZ * usedZ;
        double finalX = usedX + requestedX;
        double finalZ = usedZ + requestedZ;
        if (finalX * finalX + finalZ * finalZ <= maxSq) return requested;

        // A previous move already consumed the budget. Don't send a backwards
        // impulse to correct previous displacement within this tick.
        if (usedSq >= maxSq) return new HorizontalMovement(0.0D, 0.0D);

        // Intersect the requested movement vector with the budget circle.
        // This preserves its heading even after another move in the same tick.
        double dot = usedX * requestedX + usedZ * requestedZ;
        double root = Math.sqrt(Math.max(0.0D, dot * dot + movingSq * (maxSq - usedSq)));
        double fraction = Math.max(0.0D, Math.min(1.0D, (root - dot) / movingSq));
        return new HorizontalMovement(requestedX * fraction, requestedZ * fraction);
    }

    public boolean isAnchored() {
        return anchored;
    }

    public void reset() {
        anchored = false;
        jumpWasDown = false;
        jumpAirborne = false;
        floatUp = false;
        jumpReleaseTicks = 0;
        correctionRelease = 0;
        movementTickStartX = Double.NaN;
        movementTickStartZ = Double.NaN;
    }

    public void onServerCorrection() {
        // Preserve anchored until tick() releases the synthetic onGround flag.
        jumpAirborne = false;
        jumpReleaseTicks = 0;
        correctionRelease = CORRECTION_RELEASE_TICKS;
    }

    /** True only if this controller had created an artificial ground state. */
    public boolean onInactiveTick() {
        boolean detach = anchored;
        anchored = false;
        jumpAirborne = false;
        jumpWasDown = false;
        jumpReleaseTicks = 0;
        movementTickStartX = Double.NaN;
        movementTickStartZ = Double.NaN;
        if (correctionRelease > 0) correctionRelease--;
        return detach;
    }

    /** Observed Jesus exemptions, not an indication that other Speed checks skip. */
    public static int observedExemptions(Sample s) {
        if (s.nearSolid()) return ALL_CHECKS;
        int mask = 0;
        if (s.fullySubmerged() || s.bubbleColumn()) mask |= CHECK_B | CHECK_C | CHECK_D | CHECK_E;
        if (s.dolphinsGrace()) mask |= CHECK_A | CHECK_B | CHECK_E;
        if (s.depthStrider()) mask |= CHECK_B | CHECK_E;
        return mask;
    }

    public Decision tick(Sample s) {
        if (!s.waterNearby() || s.physicalSupport()) {
            boolean wasAnchored = onInactiveTick();
            return wasAnchored && !s.physicalSupport() ? release() : Decision.NONE;
        }

        if (correctionRelease > 0) correctionRelease--;
        double depth = s.depth();
        boolean pressed = s.jump() && !jumpWasDown;
        jumpWasDown = s.jump();

        // Vanilla handles jump input. In particular, never fake ground while
        // space is held, and never launch with positive Y from fake ground.
        if (pressed || s.jump()) {
            anchored = false;
            jumpAirborne = true;
            if (pressed) jumpReleaseTicks = 2;
            return release();
        }

        // A one-tick space tap still detaches Solid for long enough to let
        // regular water physics advance rather than immediately reanchoring.
        if (jumpReleaseTicks > 0) {
            jumpReleaseTicks--;
            anchored = false;
            return release();
        }

        if (jumpAirborne) {
            // Permit the arc and a genuine water entry before resuming support.
            if (s.verticalY() > 0.012D || depth < -ANCHOR_ABOVE) {
                anchored = false;
                return release();
            }
            jumpAirborne = false;
        }

        // Deep-water recovery must never be prevented by a correction cooldown.
        if (depth > ANCHOR_BELOW) {
            anchored = false;
            double lift = DEEP_LIFT_MIN
                    + Math.min(1.0D, Math.max(0.0D, depth - ANCHOR_BELOW))
                    * (DEEP_LIFT_MAX - DEEP_LIFT_MIN);
            return vertical(Math.max(s.verticalY(), lift));
        }

        // Speed E is independent of Jesus exemptions. No accelerated movement
        // is generated here; active horizontal movement must not use fake ground.
        if (s.moving() || correctionRelease > 0) {
            anchored = false;
            if (depth > MOVING_DEEP) return vertical(Math.max(s.verticalY(), 0.074D));
            if (depth < MOVING_HIGH) return vertical(Math.min(s.verticalY(), FLOAT_DOWN));
            if (Math.abs(s.verticalY()) < 0.025D) {
                floatUp = !floatUp;
                return vertical(floatUp ? FLOAT_UP : FLOAT_DOWN);
            }
            return release();
        }

        if (depth < -ANCHOR_ABOVE || s.verticalY() > 0.025D) {
            anchored = false;
            return release();
        }

        // Solid-like stable support only when nearly stationary. This avoids
        // combining synthetic ground and a large horizontal speed on water.
        anchored = true;
        return new Decision(true, Math.ceil(s.waterLevel() * 64.0D) / 64.0D,
                true, false, true, 0.0D);
    }

    private static Decision release() {
        return new Decision(false, 0.0D, false, true, false, 0.0D);
    }

    private static Decision vertical(double y) {
        return new Decision(false, 0.0D, false, true, true, y);
    }
}
