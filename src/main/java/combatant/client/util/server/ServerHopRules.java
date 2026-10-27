/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Game-free decision rules shared by the teleport-command modules (AutoRtp, AutoTpa): when a random teleport has
 * put the player somewhere good, whether a position change was a teleport, and chat phrase matching.
 */
public final class ServerHopRules {

    private ServerHopRules() {
    }

    /** What AutoRtp waits for before it stops sending the command. */
    public enum StopCondition {
        /** No other player within the radius. */
        NO_PLAYERS_NEARBY,
        /** At least the minimum horizontal distance from the spawn point. */
        FAR_FROM_SPAWN,
        /** Both of the above. */
        BOTH,
        /** Either of the above. */
        EITHER,
        /** Never: only the attempt limit or the player stops it. */
        NEVER
    }

    public static boolean conditionMet(StopCondition condition, int playersNearby, double distanceFromSpawn,
                                       double minSpawnDistance) {
        boolean alone = playersNearby <= 0;
        boolean far = distanceFromSpawn >= minSpawnDistance;
        return switch (condition) {
            case NO_PLAYERS_NEARBY -> alone;
            case FAR_FROM_SPAWN -> far;
            case BOTH -> alone && far;
            case EITHER -> alone || far;
            case NEVER -> false;
        };
    }

    public static double horizontalDistance(double x, double z, double otherX, double otherZ) {
        double dx = x - otherX;
        double dz = z - otherZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** A single-tick position change this large cannot be walking, sprinting or a fall: it is a teleport. */
    public static boolean isTeleport(double dx, double dy, double dz, double threshold) {
        return dx * dx + dy * dy + dz * dz >= threshold * threshold;
    }

    /** True when a command is due: {@code now} has reached {@code nextAt}. */
    public static boolean due(long now, long nextAt) {
        return now >= nextAt;
    }

    /** Splits a comma separated phrase list into trimmed, lower-cased, non-empty phrases. */
    public static List<String> parsePhrases(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null) return out;
        for (String part : csv.split(",")) {
            String phrase = part.strip().toLowerCase(Locale.ROOT);
            if (!phrase.isEmpty()) out.add(phrase);
        }
        return out;
    }

    /** Case-insensitive "contains any phrase" check. */
    public static boolean containsAny(String message, List<String> phrases) {
        if (message == null || message.isEmpty() || phrases.isEmpty()) return false;
        String lower = message.toLowerCase(Locale.ROOT);
        for (String phrase : phrases) {
            if (lower.contains(phrase)) return true;
        }
        return false;
    }

    /** Fills {@code %s} (or appends the argument) of a command template; null target keeps the template as is. */
    public static String fillCommand(String template, String argument) {
        String base = template == null ? "" : template.strip();
        if (argument == null || argument.isBlank()) return base;
        if (base.contains("%s")) return base.replace("%s", argument.strip());
        return base.isEmpty() ? base : base + " " + argument.strip();
    }
}
