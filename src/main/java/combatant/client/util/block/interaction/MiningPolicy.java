/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.block.interaction;

/** Pure, testable admission rules used before a block is locked as a mining target. */
public final class MiningPolicy {
    private MiningPolicy() { }

    public static boolean withinReach(double distanceSquared, double reach) {
        return Double.isFinite(distanceSquared) && Double.isFinite(reach) && reach > 0.0
                && distanceSquared <= reach * reach + 1.0E-7;
    }

    public static int estimatedTicks(float progressPerTick) {
        if (!Float.isFinite(progressPerTick) || progressPerTick <= 0.0f) return Integer.MAX_VALUE;
        return progressPerTick >= 1.0f ? 1 : (int) Math.ceil(1.0 / progressPerTick);
    }

    public static boolean admissible(float progressPerTick, int maxTicks,
                                     boolean skipSlow, boolean requireCorrectTool,
                                     boolean hasCorrectTool) {
        return (!requireCorrectTool || hasCorrectTool)
                && Float.isFinite(progressPerTick) && progressPerTick > 0.0f
                && (!skipSlow || estimatedTicks(progressPerTick) <= maxTicks);
    }

    public static boolean admissible(float progressPerTick, int maxTicks,
                                     boolean skipSlow, boolean requireCorrectTool,
                                     boolean hasCorrectTool, boolean requireSuitableTool,
                                     boolean hasSuitableTool) {
        return (!requireSuitableTool || hasSuitableTool)
                && admissible(progressPerTick, maxTicks, skipSlow, requireCorrectTool, hasCorrectTool);
    }

    /** Timeout allows for client/server tick variance without spinning indefinitely. */
    public static boolean miningTimedOut(long activeTicks, int expectedMaxTicks) {
        return activeTicks > Math.max(20L, (long) expectedMaxTicks * 2L + 20L);
    }

    /** A tunnel is at or above the player's feet, never in the supporting floor. */
    public static boolean tunnelHeight(int playerFeetY, int targetY) {
        return targetY >= playerFeetY && targetY <= playerFeetY + 1;
    }
}
