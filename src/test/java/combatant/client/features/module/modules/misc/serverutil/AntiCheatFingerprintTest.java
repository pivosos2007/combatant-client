/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc.serverutil;

import combatant.client.features.module.modules.misc.serverutil.AntiCheatFingerprint.Confidence;
import combatant.client.features.module.modules.misc.serverutil.AntiCheatFingerprint.Kind;
import combatant.client.features.module.modules.misc.serverutil.AntiCheatFingerprint.Verdict;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiCheatFingerprintTest {

    @Test
    void tooFewSamplesIsUnknown() {
        AntiCheatFingerprint fp = new AntiCheatFingerprint();
        for (int i = 0; i < 5; i++) fp.observe(-i, i * 50L);
        assertEquals(Kind.UNKNOWN, fp.verdict(1000).kind());
        assertEquals(Confidence.NONE, fp.verdict(1000).confidence());
    }

    @Test
    void decrementingNegativeIdsAreRecognisedWithGrowingConfidence() {
        AntiCheatFingerprint fp = new AntiCheatFingerprint();
        for (int i = 0; i < 20; i++) fp.observe(-1 - i, i * 50L);
        Verdict medium = fp.verdict(1000);
        assertEquals(Kind.DECREMENTING_NEGATIVE, medium.kind());
        assertEquals(Confidence.MEDIUM, medium.confidence());

        for (int i = 20; i < 60; i++) fp.observe(-1 - i, i * 50L);
        assertEquals(Confidence.HIGH, fp.verdict(3000).confidence());
    }

    @Test
    void decrementingCounterWrapsAtShortRange() {
        AntiCheatFingerprint fp = new AntiCheatFingerprint();
        int id = -32760;
        for (int i = 0; i < 30; i++) {
            fp.observe(id, i * 50L);
            id = id == -32767 ? 0 : id - 1;
        }
        assertEquals(Kind.DECREMENTING_NEGATIVE, fp.verdict(2000).kind());
    }

    @Test
    void incrementingIdsAreRecognised() {
        AntiCheatFingerprint fp = new AntiCheatFingerprint();
        for (int i = 0; i < 20; i++) fp.observe(100 + i, i * 100L);
        assertEquals(Kind.INCREMENTING, fp.verdict(2000).kind());
    }

    @Test
    void frequentPatternlessIdsAreLowConfidenceRandom() {
        AntiCheatFingerprint fp = new AntiCheatFingerprint();
        Random r = new Random(7);
        for (int i = 0; i < 25; i++) fp.observe(r.nextInt(60000) - 30000, i * 100L);
        Verdict v = fp.verdict(2500);
        assertEquals(Kind.RANDOM_ID_PINGS, v.kind());
        assertEquals(Confidence.LOW, v.confidence());
        assertTrue(v.pingsPerSecond() >= 2.0);
    }

    @Test
    void silenceIsOnlyReportedAfterLongObservation() {
        AntiCheatFingerprint fp = new AntiCheatFingerprint();
        fp.begin(0L);
        assertEquals(Kind.UNKNOWN, fp.verdict(10_000L).kind());
        Verdict v = fp.verdict(31_000L);
        assertEquals(Kind.NO_PING_TRAFFIC, v.kind());
        assertEquals(Confidence.LOW, v.confidence());
    }

    @Test
    void resetClearsState() {
        AntiCheatFingerprint fp = new AntiCheatFingerprint();
        for (int i = 0; i < 20; i++) fp.observe(-1 - i, i * 50L);
        fp.reset();
        assertEquals(Kind.UNKNOWN, fp.verdict(100_000L).kind());
    }
}
