/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.combat;

public final class SpearKinetics {

    private SpearKinetics() {
    }

    public record Condition(int maxDurationTicks, float minSpeed, float minRelativeSpeed) {
    }

    public enum WindowState {
        LIT,
        UNMET,
        EXPIRED,
        PENDING
    }

    public enum ReachBand {
        TOO_CLOSE,
        IN_RANGE,
        TOO_FAR
    }

    public static double speedAlong(double lookX, double lookY, double lookZ,
                                    double motionX, double motionY, double motionZ) {
        return lookX * motionX + lookY * motionY + lookZ * motionZ;
    }

    public static double relativeSpeed(double attackerSpeedAlongLook, double targetSpeedAlongLook) {
        return Math.max(0.0, attackerSpeedAlongLook - targetSpeedAlongLook);
    }

    public static float chargeProgress(int ticksUsingItem, int delayTicks) {
        if (delayTicks <= 0) return 1.0f;
        return Math.clamp((float) ticksUsingItem / (float) delayTicks, 0.0f, 1.0f);
    }

    public static float remainingFraction(Condition condition, int ticksUsingItem, int delayTicks) {
        if (condition == null || condition.maxDurationTicks() <= 0) return 0.0f;
        int active = ticksUsingItem - delayTicks;
        if (active < 0) return 1.0f;
        if (active >= condition.maxDurationTicks()) return 0.0f;
        return Math.clamp(1.0f - (float) active / (float) condition.maxDurationTicks(), 0.0f, 1.0f);
    }

    public static WindowState state(Condition condition, int ticksUsingItem, int delayTicks,
                                    double attackerSpeed, double relativeSpeed, boolean hasTarget) {
        if (condition == null) return WindowState.EXPIRED;
        int active = ticksUsingItem - delayTicks;
        if (active < 0) return WindowState.PENDING;
        if (active > condition.maxDurationTicks()) return WindowState.EXPIRED;

        boolean speedMet = attackerSpeed >= condition.minSpeed();
        boolean relMet = !hasTarget
                ? (condition.minRelativeSpeed() <= 0.0f)
                : (relativeSpeed >= condition.minRelativeSpeed());
        return (speedMet && relMet) ? WindowState.LIT : WindowState.UNMET;
    }

    public static boolean shouldRecharge(boolean enabled, boolean keyUseDown, boolean hasTarget,
                                         int ticksUsingItem, int delayTicks,
                                         Condition dismount, Condition knockback, Condition damage) {
        if (!enabled || !keyUseDown) return false;
        int active = ticksUsingItem - delayTicks;
        if (active < 0) return false;

        boolean hasAny = false;
        if (dismount != null) {
            hasAny = true;
            if (active <= dismount.maxDurationTicks()) return false;
        }
        if (knockback != null) {
            hasAny = true;
            if (active <= knockback.maxDurationTicks()) return false;
        }
        if (damage != null) {
            hasAny = true;
            if (active <= damage.maxDurationTicks()) return false;
        }
        return hasAny;
    }

    public static double chargingMaxReach(double baseMaxReach, double forwardBlocksPerTick) {
        return baseMaxReach + Math.max(0.0, forwardBlocksPerTick);
    }

    public static ReachBand classifyReach(double distance, double minRange, double maxRange, double hitboxMargin) {
        if (distance < minRange) {
            return ReachBand.TOO_CLOSE;
        }
        if (distance > maxRange + hitboxMargin) {
            return ReachBand.TOO_FAR;
        }
        return ReachBand.IN_RANGE;
    }

    public static float stabDamage(double baseDamage, double relativeSpeed, float damageMultiplier) {
        return (float) (baseDamage + Math.floor(Math.max(0.0, relativeSpeed) * (double) damageMultiplier));
    }
}
