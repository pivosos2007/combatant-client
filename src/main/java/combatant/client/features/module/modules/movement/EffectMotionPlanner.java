package combatant.client.features.module.modules.movement;

final class EffectMotionPlanner {
    enum Effect { LEVITATION, SLOW_FALLING }

    record Motion(double x, double y, double z) { }

    private Effect effect;
    private long lastTick = Long.MIN_VALUE;
    private int airborneTicks;
    private double levitationBudget;

    void reset() {
        effect = null;
        lastTick = Long.MIN_VALUE;
        airborneTicks = 0;
        levitationBudget = 0.0;
    }

    void clearAltitude() {
        airborneTicks = 0;
    }

    void noteCorrection() {
        lastTick = Long.MIN_VALUE;
        airborneTicks = 0;
        levitationBudget = 8.0;
    }

    Motion calculate(long tick, Effect currentEffect, int amplifier, double positionY,
                     double vanillaX, double vanillaY, double vanillaZ,
                     double inputX, double inputForward, double yawDegrees,
                     boolean jump, boolean sneak, boolean noInertia, boolean ignoreLevitation,
                     boolean onGround, boolean obstructed) {
        if (effect != currentEffect || tick < lastTick ||
                (lastTick != Long.MIN_VALUE && tick - lastTick > 2)) {
            reset();
            effect = currentEffect;
        }
        boolean advance = tick != lastTick;
        if (advance) {
            if (onGround || obstructed) airborneTicks = 0;
            else airborneTicks++;
            levitationBudget = Math.max(0.0, levitationBudget - 0.075);
        }
        lastTick = tick;

        if (onGround || obstructed) return new Motion(vanillaX, vanillaY, vanillaZ);

        double x = vanillaX;
        double z = vanillaZ;
        double currentSpeed = Math.hypot(x, z);
        double inputLength = Math.hypot(inputX, inputForward);
        if (inputLength > 1.0E-4 && currentSpeed < 0.245) {
            double yaw = Math.toRadians(yawDegrees);
            double sideways = inputX / Math.max(1.0, inputLength);
            double forward = inputForward / Math.max(1.0, inputLength);
            double targetX = sideways * Math.cos(yaw) - forward * Math.sin(yaw);
            double targetZ = sideways * Math.sin(yaw) + forward * Math.cos(yaw);
            double desiredX = targetX * 0.245;
            double desiredZ = targetZ * 0.245;
            double dx = desiredX - x;
            double dz = desiredZ - z;
            double change = Math.hypot(dx, dz);
            if (change > 1.0E-8) {
                double step = Math.min(0.008, change);
                double fraction = step / change;
                double nextX = x + dx * fraction;
                double nextZ = z + dz * fraction;
                double nextSpeed = Math.hypot(nextX, nextZ);
                double maxSpeed = Math.min(0.245, currentSpeed + 0.004);
                if (nextSpeed > maxSpeed && nextSpeed > 1.0E-8) {
                    nextX *= maxSpeed / nextSpeed;
                    nextZ *= maxSpeed / nextSpeed;
                }
                x = nextX;
                z = nextZ;
            }
        } else if (noInertia && inputLength <= 1.0E-4 && currentSpeed < 0.245) {
            double reduction = Math.min(0.008, currentSpeed);
            double factor = currentSpeed > 1.0E-8 ? (currentSpeed - reduction) / currentSpeed : 0.0;
            x *= factor;
            z *= factor;
        }

        double y = vanillaY;
        if (currentEffect == Effect.LEVITATION) {
            if (ignoreLevitation) {
                if (levitationBudget < 5.5 && vanillaY > 0.0) {
                    y = Math.max(0.0, vanillaY - 0.12);
                    if (advance) levitationBudget += 1.0;
                }
            } else if (jump != sneak) {
                if (jump) {
                    y = vanillaY + Math.min(0.11, 0.075 + 0.015 * amplifier);
                } else if (levitationBudget < 5.5) {
                    y = vanillaY - Math.min(0.065, Math.max(0.0, vanillaY + 0.10));
                    if (advance) levitationBudget += 1.0;
                }
            }
        } else if (sneak && !jump && vanillaY < 0.0) {
            y = Math.min(vanillaY, Math.max(-0.32, vanillaY - 0.035));
        } else if (jump && !sneak && vanillaY < -0.065) {
            y = Math.min(-0.055, vanillaY + 0.012);
        }

        if (currentEffect == Effect.SLOW_FALLING && airborneTicks > 40 && y > -0.04) {
            y = Math.min(vanillaY, -0.04);
        }
        return new Motion(x, y, z);
    }
}
