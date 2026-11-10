/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.combat.instacart;

/**
 * Packet-window bounds and policy for the one automatic InstaCart cycle.
 * The bow release cannot be followed by a held-slot change in the same window.
 * Rail confirmation and cart placement likewise require separate client ticks.
 * Keep this pure so both action orders are regression-testable.
 */
public final class InstaCartCycleRules {
    private InstaCartCycleRules() { }

    public static int minimumFastFlightTicks(boolean railAlreadyPlaced, int extraMargin) {
        // The shot is released in PostSync. The next PRE rotation reaches the
        // server first; the item interaction happens the following GameTick.
        // Two placements require one more synced rotation/window, even when
        // the rail is predicted by the client immediately after interaction.
        // Leave room for rotation acknowledgement, slot transactions and
        // confirmation from the integrated / remote server. Shorter arcs are
        // handled by preparing the rail/cart before firing, not blind shots.
        return (railAlreadyPlaced ? 6 : 8) + Math.max(0, extraMargin);
    }

    /** Aim one tick ahead of the PRE sync callback: the actual release is
     * executed by EventPostSync with the subsequent usage duration. */
    public static int expectedReleaseCharge(int currentCharge) {
        return Math.min(20, Math.max(0, currentCharge + 1));
    }

    /** Minimum reliable draw for a TNT-cart shot. Six-tick arrows fly slowly,
     * spend longer exposed to player movement and random bow inaccuracy, and
     * caused the repeated misses in latest(10). This is a soft preference,
     * not an unconditional requirement of full bow charge. */
    public static int preferredCartCharge(double distance, int configuredMaximum) {
        int max = Math.max(6, Math.min(20, configuredMaximum));
        int preferred = distance <= 3.0 ? 10 : distance <= 6.0 ? 12
                : distance <= 10.0 ? 16 : 18;
        return Math.min(preferred, max);
    }

    /** Search the preferred draw first, then stronger and weaker alternatives.
     * Do not fall back to an unreliable six-tick release unless the user
     * configured the maximum below ten ticks. */
    public static int[] chargeOrder(double distance, int configuredMaximum) {
        int max = Math.max(6, Math.min(20, configuredMaximum));
        int floor = Math.min(10, max);
        int preferred = preferredCartCharge(distance, max);
        java.util.List<Integer> values = new java.util.ArrayList<>();
        values.add(preferred);
        for (int step = 2; step <= max - floor + 1; step += 2) {
            int stronger = preferred + step;
            int weaker = preferred - step;
            if (stronger <= max) values.add(stronger);
            if (weaker >= floor) values.add(weaker);
        }
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Negative Minecraft entity IDs are valid; only our -1 sentinel is absent. */
    public static boolean isKnownTargetId(int entityId) {
        return entityId != -1;
    }


    /** One mode, two execution orders. No usable fast shot means preparing the
     * explosive first, not dropping a safe site and repeatedly redrawing. */
    public static boolean shouldPrepareBeforeShot(boolean existingCart, boolean verifiedShot) {
        return !existingCart && !verifiedShot;
    }
    public enum AfterShot { SHOOT_EXISTING, PLACE_CART, PLACE_RAIL }

    public static AfterShot afterRelease(boolean existingCart, boolean railExists) {
        if (existingCart) return AfterShot.SHOOT_EXISTING;
        return railExists ? AfterShot.PLACE_CART : AfterShot.PLACE_RAIL;
    }

    public static boolean retryExistingCart(boolean cartPresent, int attempts, int maxAttempts) {
        return cartPresent && attempts < maxAttempts;
    }

    /** Keep vanilla keyUse down only while a confirmed bow draw is active. */
    public static boolean shouldHoldBow(boolean drawing, boolean selectedBowSlot) {
        return drawing && selectedBowSlot;
    }

    /** A bow release is legal only after the planned rotation has been sent. */
    public static boolean canRelease(boolean drawing, boolean usingBow,
                                     boolean correctSlot, boolean rotationSent) {
        return drawing && usingBow && correctSlot && rotationSent;
    }

    /** Retry a dispatched interaction only after its acknowledgement window.
     * A local SUCCESS is not proof that the server accepted the placement. */
    public static boolean canRetryPlacement(int elapsed, int nextAttempt, int attempts, int maxAttempts) {
        return elapsed >= nextAttempt && attempts < maxAttempts;
    }

    /** Once charged, a repeatedly invalid path must be replanned, not held forever. */
    public static boolean shouldReplanShot(int failures, int threshold, boolean alreadyFired) {
        return !alreadyFired && failures >= threshold;
    }

    /** A failed bow slot acquisition is a retry, not an abort/replan. */
    public static boolean shouldRetryBow(boolean slotAvailable, int elapsedTicks, int timeoutTicks) {
        return !slotAvailable && elapsedTicks <= timeoutTicks;
    }
}
