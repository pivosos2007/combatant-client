/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.mesh;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RigSkinBindingTest {
    private final RigSkinBinding binding = RigSkinBinding.chain(
            new int[]{10, 11, 12, 13},
            new float[]{0f, 0.25f, 0.67f, 1f}
    );

    @Test
    void keepsPrimaryBoneInSlotZeroAcrossWholeChain() {
        assertSample(0f, 10, 1f, RigVertex.UNUSED_BONE, 0f);
        assertSample(0.125f, 10, 0.5f, 11, 0.5f);
        assertSample(0.25f, 11, 1f, RigVertex.UNUSED_BONE, 0f);
        assertSample(0.46f, 11, 0.5f, 12, 0.5f);
        assertSample(0.67f, 12, 1f, RigVertex.UNUSED_BONE, 0f);
        assertSample(1f, 13, 1f, RigVertex.UNUSED_BONE, 0f);
    }

    private void assertSample(float longitudinal, int bone0, float weight0, int bone1, float weight1) {
        RigSkinBinding.Sample sample = new RigSkinBinding.Sample();
        binding.sample(longitudinal, sample);
        assertEquals(bone0, sample.bone(0));
        assertEquals(weight0, sample.weight(0), 1.0e-6f);
        assertEquals(bone1, sample.bone(1));
        assertEquals(weight1, sample.weight(1), 1.0e-6f);
    }
}
