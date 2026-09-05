/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.clip;

import combatant.client.render.engine.command.UiCommandBuffer;
import combatant.client.render.engine.command.UiPrimitiveCommand;
import combatant.client.render.engine.renderer.ui.draw.UiRect;
import combatant.client.render.engine.renderer.ui.draw.UiShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class UiClipStackTest {
    @Test
    void nestedSnapshotsAreImmutableIntersectionsWithStableParentIdentity() {
        UiClipStack stack = new UiClipStack(8);
        UiClipStack.Layer outer = stack.push(UiShape.roundedRect(10, 20, 100, 80, 12));
        assertNotNull(outer);
        UiClipSnapshot outerSnapshot = stack.current();

        UiClipStack.Layer inner = stack.push(UiShape.circle(60, 60, 30));
        assertNotNull(inner);
        UiClipSnapshot nested = stack.current();

        assertEquals(UiClipStrategy.ANALYTIC, nested.strategy());
        assertEquals(2, nested.primitives().size());
        assertEquals(new UiRect(30f, 30f, 60f, 60f), nested.logicalBounds());
        assertNotEquals(outerSnapshot.id(), nested.id());
        assertThrows(UnsupportedOperationException.class,
                () -> nested.primitives().add(UiShape.rect(0, 0, 1, 1)));

        assertSame(inner, stack.pop());
        assertSame(outerSnapshot, stack.current());
    }

    @Test
    void complexChildPromotesOnlyItsScopeToStableMsaaStrategy() {
        UiClipStack stack = new UiClipStack(8);
        stack.push(UiShape.roundedRect(0, 0, 100, 100, 8));
        UiClipSnapshot analyticParent = stack.current();

        stack.push(UiShape.polyline(new double[]{10, 10, 90, 10, 50, 90}, 3, true));
        UiClipSnapshot complexChild = stack.current();
        assertEquals(UiClipStrategy.MSAA_STENCIL, complexChild.strategy());
        assertEquals(2, complexChild.msaaSamples());

        stack.push(UiShape.circle(50, 50, 10));
        assertEquals(UiClipStrategy.MSAA_STENCIL, stack.current().strategy());

        stack.pop();
        stack.pop();
        assertSame(analyticParent, stack.current());
        assertEquals(UiClipStrategy.ANALYTIC, stack.current().strategy());
    }

    @Test
    void commandEntryCapturesScissorAndShapeClipIndependently() {
        UiClipStack stack = new UiClipStack(4);
        stack.push(UiShape.roundedRect(0, 0, 20, 20, 4));
        UiScissorSnapshot scissor = new UiScissorSnapshot(42L, 1, 2, 30, 40);
        UiCommandBuffer commands = new UiCommandBuffer();

        commands.add(new UiPrimitiveCommand(0, 0, 5, 5, 0xFFFFFFFF), scissor, stack.current());

        assertEquals(1, commands.entries().size());
        assertSame(scissor, commands.entries().getFirst().scissorSnapshot());
        assertSame(stack.current(), commands.entries().getFirst().clipSnapshot());
    }

    @Test
    void analyticIntersectionHasFixedUniformCapacity() {
        UiClipStack stack = new UiClipStack(8);
        for (int i = 0; i < UiClipStack.MAX_ANALYTIC_PRIMITIVES; i++) {
            stack.push(UiShape.roundedRect(i, i, 100 - i * 2, 100 - i * 2, 5));
            assertEquals(UiClipStrategy.ANALYTIC, stack.current().strategy());
        }

        stack.push(UiShape.circle(50, 50, 20));
        assertEquals(UiClipStrategy.MSAA_STENCIL, stack.current().strategy());
    }

    @Test
    void analyticRequirementCanBeCheckedWithoutSilentMsaaPromotion() {
        UiClipStack stack = new UiClipStack(8);
        UiShape polygon = UiShape.polyline(new double[]{0, 0, 20, 0, 10, 20}, 3, true);
        assertFalse(stack.canPushAnalytic(polygon));

        assertTrue(stack.canPushAnalytic(UiShape.roundedRect(0, 0, 40, 40, 8)));
        stack.push(UiShape.roundedRect(0, 0, 40, 40, 8), UiClipStrategy.MSAA_STENCIL);
        assertFalse(stack.canPushAnalytic(UiShape.circle(20, 20, 10)));
    }
}
