/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.temporal;

import org.joml.Vector2f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TemporalJitterSequenceTest {
    @Test
    void r2SequenceIsBoundedAndHasNoLongTermDirectionalDrift() {
        float sumX = 0.0f;
        float sumY = 0.0f;
        int sampleCount = 1024;
        for (int frame = 0; frame < sampleCount; frame++) {
            Vector2f sample = TemporalJitterSequence.sample(frame);
            assertTrue(Math.abs(sample.x) <= 0.5f);
            assertTrue(Math.abs(sample.y) <= 0.5f);
            sumX += sample.x;
            sumY += sample.y;
        }
        assertTrue(Math.abs(sumX / sampleCount) < 0.002f);
        assertTrue(Math.abs(sumY / sampleCount) < 0.002f);
    }

    @Test
    void sequenceDoesNotRepeatAfterEightFrames() {
        for (int frame = 0; frame < 8; frame++) {
            assertNotEquals(TemporalJitterSequence.sample(frame), TemporalJitterSequence.sample(frame + 8));
        }
    }
}
