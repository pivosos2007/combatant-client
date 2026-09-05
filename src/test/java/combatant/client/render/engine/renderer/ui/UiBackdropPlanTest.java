/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import combatant.client.render.engine.command.UiCommandBuffer;
import combatant.client.render.engine.command.UiEffectRegionCommand;
import combatant.client.render.engine.renderer.ui.draw.UiBackdropRequest;
import combatant.client.render.engine.renderer.ui.draw.UiBlurQuality;
import combatant.client.render.engine.renderer.ui.draw.UiEffectSpec;
import combatant.client.render.engine.renderer.ui.draw.UiRect;
import combatant.client.render.engine.renderer.ui.draw.UiShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class UiBackdropPlanTest {
    @Test
    void adjacentCompatibleRequestsShareOneGroupAndUnionBounds() {
        UiCommandBuffer commands = new UiCommandBuffer();
        UiBackdropRequest first = UiBackdropRequest.capturedSceneGlass(
                UiRect.of(10, 20, 30, 40), UiBlurQuality.HIGH, 1.15f);
        UiBackdropRequest second = first.withCaptureBounds(UiRect.of(35, 15, 20, 15));

        commands.add(effect(first));
        commands.add(effect(second));

        UiBackdropPlan plan = UiBackdropPlan.compile(commands);
        assertEquals(2, plan.requestCount());
        assertEquals(1, plan.groups().size());
        assertEquals(new UiRect(10, 15, 45, 45), plan.groups().getFirst().captureBounds());
        assertEquals(2, plan.groups().getFirst().requestCount());
    }

    @Test
    void underlayPolicySeparatesOtherwiseCompatibleRequests() {
        UiCommandBuffer commands = new UiCommandBuffer();
        UiBackdropRequest sceneOnly = UiBackdropRequest.capturedSceneGlass(
                UiRect.of(0, 0, 20, 20), UiBlurQuality.HIGH, 1.15f);
        UiBackdropRequest passThrough = sceneOnly.withUiUnderlay(
                UiBackdropRequest.UiUnderlayMode.PASS_THROUGH,
                UiBackdropRequest.BlurParameters.NONE,
                1.0f);

        commands.add(effect(sceneOnly));
        commands.add(effect(passThrough));

        UiBackdropPlan plan = UiBackdropPlan.compile(commands);
        assertEquals(2, plan.groups().size());
        assertFalse(sceneOnly.compatibleInputs(passThrough));
        assertFalse(passThrough.uiBlur().enabled());
    }

    private static UiEffectRegionCommand effect(UiBackdropRequest request) {
        UiShape shape = UiShape.rect(0, 0, 10, 10);
        return new UiEffectRegionCommand(UiEffectSpec.liquidGlass(
                shape, 4.0, 1.0, 1.0, 0xFFFFFFFF, request));
    }
}
