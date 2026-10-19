/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc.serverutil;

/**
 * Heuristic classification of a server's anti-cheat from the pattern of clientbound ping ("transaction")
 * packets. Pure statistics over the observed ids: it only ever reports a pattern family with a confidence,
 * never a certain product name.
 */
public final class AntiCheatFingerprint {

    public enum Kind {
        /** Not enough data yet. */
        UNKNOWN,
        /** Ids count down by one through the negative short range, wrapping at 0 (Grim-style transactions). */
        DECREMENTING_NEGATIVE,
        /** Ids count up by one (sequential transaction counter). */
        INCREMENTING,
        /** Frequent pings with ids that follow no counter. */
        RANDOM_ID_PINGS,
        /** Long observation without any ping traffic. */
        NO_PING_TRAFFIC
    }

    public enum Confidence { NONE, LOW, MEDIUM, HIGH }

    public record Verdict(Kind kind, Confidence confidence, int samples, double pingsPerSecond) {
    }

    private static final int MIN_SAMPLES = 12;
    private static final int HIGH_SAMPLES = 40;
    private static final double PATTERN_RATIO = 0.8;
    private static final double MIN_RATE = 2.0;
    private static final long SILENCE_MS = 30_000L;
    private static final int SHORT_WRAP_FLOOR = -32767;

    private long startMs = -1L;
    private long lastMs;
    private int total;
    private int steps;
    private int decrementSteps;
    private int incrementSteps;
    private int previous;
    private boolean hasPrevious;

    public void reset() {
        startMs = -1L;
        lastMs = 0L;
        total = 0;
        steps = 0;
        decrementSteps = 0;
        incrementSteps = 0;
        previous = 0;
        hasPrevious = false;
    }

    /** Marks the start of observation (join time) so a silent server can be recognised. */
    public void begin(long nowMs) {
        if (startMs < 0L) startMs = nowMs;
    }

    public void observe(int id, long nowMs) {
        begin(nowMs);
        lastMs = nowMs;
        total++;
        if (hasPrevious) {
            steps++;
            if (isDecrement(previous, id)) decrementSteps++;
            else if (isIncrement(previous, id)) incrementSteps++;
        }
        previous = id;
        hasPrevious = true;
    }

    public Verdict verdict(long nowMs) {
        if (startMs < 0L) return new Verdict(Kind.UNKNOWN, Confidence.NONE, 0, 0.0);
        double seconds = Math.max(0.001, (Math.max(lastMs, nowMs) - startMs) / 1000.0);
        double rate = total / seconds;

        if (total == 0) {
            if (nowMs - startMs >= SILENCE_MS) return new Verdict(Kind.NO_PING_TRAFFIC, Confidence.LOW, 0, 0.0);
            return new Verdict(Kind.UNKNOWN, Confidence.NONE, 0, 0.0);
        }
        if (total < MIN_SAMPLES || steps == 0) return new Verdict(Kind.UNKNOWN, Confidence.NONE, total, rate);

        Confidence strong = total >= HIGH_SAMPLES ? Confidence.HIGH : Confidence.MEDIUM;
        if (decrementSteps >= steps * PATTERN_RATIO) return new Verdict(Kind.DECREMENTING_NEGATIVE, strong, total, rate);
        if (incrementSteps >= steps * PATTERN_RATIO) return new Verdict(Kind.INCREMENTING, strong, total, rate);
        if (rate >= MIN_RATE) {
            // Frequent but patternless: weaker evidence than a clean counter.
            return new Verdict(Kind.RANDOM_ID_PINGS, total >= HIGH_SAMPLES ? Confidence.MEDIUM : Confidence.LOW, total, rate);
        }
        return new Verdict(Kind.UNKNOWN, Confidence.LOW, total, rate);
    }

    private static boolean isDecrement(int prev, int id) {
        if (prev > 0 || id > 0) return false;
        if (prev == SHORT_WRAP_FLOOR && id == 0) return true;
        return id == prev - 1 && id >= SHORT_WRAP_FLOOR;
    }

    private static boolean isIncrement(int prev, int id) {
        return id == prev + 1 && prev >= 0;
    }
}
