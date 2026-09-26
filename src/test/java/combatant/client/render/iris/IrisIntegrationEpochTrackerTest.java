/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.iris;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class IrisIntegrationEpochTrackerTest {
    @Test
    void stableFrameAndRuntimeDoNotAdvanceEpoch() {
        IrisIntegrationEpochTracker tracker = new IrisIntegrationEpochTracker();
        Object world = new Object();
        IrisRuntimeSnapshot runtime = runtime("Photon", "photon.v1_3b");

        tracker.observeFrame(world, "minecraft:overworld", 1920, 1080);
        long epoch = tracker.stamp(runtime).integrationEpoch();
        tracker.observeFrame(world, "minecraft:overworld", 1920, 1080);

        assertEquals(epoch, tracker.stamp(runtime).integrationEpoch());
    }

    @Test
    void lifecycleChangesAdvanceEpochWithTypedReason() {
        IrisIntegrationEpochTracker tracker = new IrisIntegrationEpochTracker();
        Object firstWorld = new Object();
        Object secondWorld = new Object();
        Object pipeline = new Object();

        tracker.stamp(runtime("Photon", "photon.v1_3b"));
        tracker.observeFrame(firstWorld, "minecraft:overworld", 1280, 720);
        long initial = tracker.epoch();

        tracker.observeFrame(secondWorld, "minecraft:overworld", 1280, 720);
        assertEquals(initial + 1L, tracker.epoch());
        assertEquals("world_switch", tracker.reason());

        tracker.observePipeline(pipeline);
        tracker.pipelineDestroyed(pipeline);
        assertEquals(initial + 2L, tracker.epoch());
        assertEquals("pipeline_destroyed", tracker.reason());

        tracker.invalidate("resource_reload");
        assertEquals(initial + 3L, tracker.epoch());
        assertEquals("resource_reload", tracker.reason());
    }

    @Test
    void shaderpackIdentityChangeAdvancesEpoch() {
        IrisIntegrationEpochTracker tracker = new IrisIntegrationEpochTracker();
        long photonEpoch = tracker.stamp(runtime("Photon", "photon.v1_3b")).integrationEpoch();
        IrisRuntimeSnapshot complementary = tracker.stamp(runtime(
                "ComplementaryReimagined", "complementary_reimagined.r5"));

        assertEquals(photonEpoch + 1L, complementary.integrationEpoch());
        assertEquals("shaderpack_state_changed", complementary.integrationEpochReason());
    }

    private static IrisRuntimeSnapshot runtime(String packName, String manifestId) {
        return new IrisRuntimeSnapshot(
                true, true, true, true, false, packName,
                IrisCompatibilityProfile.GENERIC_IRIS, manifestId, Set.of(),
                0L, "unstamped", "ok"
        );
    }
}
