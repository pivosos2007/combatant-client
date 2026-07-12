/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.pvp;

import net.minecraft.world.item.Item;

/**
 * Immutable public state for local/opponent item cooldown visualizers.
 * <p>
 * A rule can be in two phases:
 * 1) use window: server-like charge counter, e.g. 1/2 uses within 10s;
 * 2) cooldown: real locked phase after the configured use count is reached.
 */
public record ItemCooldownSnapshot(
        Item item,
        boolean cooling,
        long cooldownRemainingMs,
        long cooldownTotalMs,
        int uses,
        int maxUses,
        boolean useWindowActive,
        long useWindowRemainingMs,
        long useWindowTotalMs
) {
    public static ItemCooldownSnapshot empty(Item item) {
        return new ItemCooldownSnapshot(item, false, 0L, 0L, 0, 1, false, 0L, 0L);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    public boolean visible() {
        return cooling || useWindowActive;
    }

    /**
     * Remaining fraction for vertical cooldown fill rendering.
     */
    public float cooldownProgress() {
        if (!cooling || cooldownTotalMs <= 0L) return 0f;
        return clamp01((float) cooldownRemainingMs / (float) cooldownTotalMs);
    }

    /**
     * Elapsed fraction for charge/window visualizers.
     */
    public float useWindowProgress() {
        if (!useWindowActive || useWindowTotalMs <= 0L) return usesProgress();
        return clamp01(1.0f - ((float) useWindowRemainingMs / (float) useWindowTotalMs));
    }

    public float usesProgress() {
        if (maxUses <= 0) return 0f;
        return clamp01((float) uses / (float) maxUses);
    }

    public float primaryProgress() {
        return cooling ? cooldownProgress() : usesProgress();
    }

    public float remainingSeconds() {
        long ms = cooling ? cooldownRemainingMs : useWindowRemainingMs;
        return Math.max(0f, ms / 1000.0f);
    }

    public String compactText() {
        if (cooling) {
            float seconds = remainingSeconds();
            if (seconds >= 10f) return String.valueOf((int) seconds);
            return String.format(java.util.Locale.ROOT, "%.1f", Math.round(seconds * 10f) / 10f);
        }
        if (useWindowActive && maxUses > 1) {
            return uses + "/" + maxUses;
        }
        return "";
    }
}
