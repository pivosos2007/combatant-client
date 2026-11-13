/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.aiming;

import combatant.client.events.EventHandler;
import combatant.client.events.Events;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.runtime.ClientRuntime;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.aiming.features.processors.RotationProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RotationResolutionTest {
    private static final Rotation CAMERA = new Rotation(0, 0, true);
    private static final Rotation ESCAPE = new Rotation(90, -15, true);
    private final RotationManager manager = RotationManager.INSTANCE;
    private final Safety safety = new Safety();

    @BeforeEach
    void prepare() {
        ClientRuntime.markActive("rotation resolution test");
        manager.clear();
        Events.BUS.register(safety);
    }

    @AfterEach
    void clean() {
        Events.BUS.unregister(safety);
        manager.clear();
    }

    @Test
    void freeFlightCanApplySafetyRequestInSameUpdate() {
        manager.update(CAMERA);
        assertEquals(CAMERA, safety.normal);
        assertEquals(ESCAPE, manager.getCurrentRotation());
        assertTrue(manager.ownsActiveRotation(safety));
    }

    @Test
    void reevaluationSeesNormalCourseRatherThanPreviousEmergencyCourse() {
        manager.update(CAMERA);
        manager.release(safety, false);
        manager.update(CAMERA);
        assertEquals(CAMERA, safety.normal);
        assertEquals(ESCAPE, manager.getCurrentRotation());

        manager.release(safety, false);
        safety.danger = false;
        manager.update(CAMERA);
        assertEquals(CAMERA, safety.normal);
        assertNull(manager.getCurrentRotation());
        assertNull(manager.getActiveRotationTarget());
    }

    @Test
    void emergencySuppressesNormalCallbackWithoutExpiringRequestTwice() {
        AtomicInteger actions = new AtomicInteger();
        Object pursuit = new Object();
        manager.setRotationTarget(plan(CAMERA, List.of(), actions::incrementAndGet), 80, pursuit);
        manager.update(CAMERA);
        assertEquals(0, actions.get());
        assertEquals(ESCAPE, manager.getCurrentRotation());

        manager.release(safety, false);
        safety.danger = false;
        manager.update(CAMERA);
        assertEquals(CAMERA, manager.getCurrentRotation());
        assertEquals(1, actions.get());
    }

    @Test
    void safetySeesProcessedHeadingButNormalProcessorsOnlyRunOnce() {
        AtomicInteger processing = new AtomicInteger();
        Rotation smoothed = new Rotation(10, 5, true);
        RotationProcessor smoother = (plan, from, to) -> {
            processing.incrementAndGet();
            return smoothed;
        };
        manager.setRotationTarget(plan(new Rotation(170, 30, true), List.of(smoother), null), 80, this);
        manager.update(CAMERA);
        assertEquals(smoothed, safety.normal);
        assertEquals(1, processing.get());
        assertEquals(ESCAPE, manager.getCurrentRotation());
    }

    @Test
    void safetyRespectsHigherPriorityRequests() {
        Rotation other = new Rotation(-30, 0, true);
        AtomicInteger actions = new AtomicInteger();
        manager.setRotationTarget(plan(other, List.of(), actions::incrementAndGet), 200, this);
        manager.update(CAMERA);
        assertEquals(other, manager.getCurrentRotation());
        assertTrue(manager.ownsActiveRotation(this));
        assertEquals(1, actions.get());
    }

    private static RotationTarget plan(Rotation rotation, List<RotationProcessor> processors, Runnable action) {
        return new RotationTarget(rotation, null, processors, 2, 2, false,
                MovementCorrection.SILENT, false, action == null ? null : new RestrictedSingleUseAction(action));
    }

    private final class Safety {
        private boolean danger = true;
        private Rotation normal;

        @EventHandler
        private void check(RotationUpdateEvent event) {
            if (event.getType() != RotationUpdateEvent.Type.RESOLVED) return;
            normal = event.getMovementRotation();
            if (danger) manager.setRotationTarget(plan(ESCAPE, List.of(), null), 140, this);
        }
    }
}
