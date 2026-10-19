/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc.serverutil;

/** Decides which AutoLeave trigger, if any, fires for a world snapshot. Priority: health, staff, chat, player. */
public final class AutoLeaveRules {

    public enum Trigger { HEALTH, STAFF, CHAT, PLAYER }

    public record Config(boolean health, float healthThreshold,
                         boolean player, double playerDistance,
                         boolean staff, boolean chat) {
    }

    /**
     * @param health                current health (absorption excluded)
     * @param nearestPlayerDistance blocks to the nearest non-exempt player, NaN when none is loaded
     * @param staffOnline           a staff member is visible/online per the staff tracker
     * @param chatKeyword           matched chat keyword since the last evaluation, null when none
     */
    public record Snapshot(float health, double nearestPlayerDistance, boolean staffOnline, String chatKeyword) {
    }

    private AutoLeaveRules() {
    }

    /** The trigger that fires, or null. A dead player (health <= 0) never triggers. */
    public static Trigger evaluate(Config config, Snapshot s) {
        if (config.health() && s.health() > 0.0f && s.health() <= config.healthThreshold()) return Trigger.HEALTH;
        if (config.staff() && s.staffOnline()) return Trigger.STAFF;
        if (config.chat() && s.chatKeyword() != null) return Trigger.CHAT;
        if (config.player() && !Double.isNaN(s.nearestPlayerDistance())
                && s.nearestPlayerDistance() <= config.playerDistance()) {
            return Trigger.PLAYER;
        }
        return null;
    }
}
