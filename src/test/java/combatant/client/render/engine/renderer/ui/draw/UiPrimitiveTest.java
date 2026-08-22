/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class UiPrimitiveTest {
    @Test
    void hexagonPresetUsesPixelCutAndAnalyticPath() {
        UiPrimitive primitive = UiPrimitive.builder(10, 20, 100, 40)
                .preset(UiPrimitive.Preset.HEXAGON)
                .cut(10)
                .rounding(2)
                .build();

        assertEquals(6, primitive.pointCount());
        assertTrue(primitive.isConvex());
        assertTrue(primitive.shaderEligible());
        assertEquals(2.0f, primitive.rounding());
        assertPoint(primitive, 0, 20, 20);
        assertPoint(primitive, 1, 100, 20);
        assertPoint(primitive, 2, 110, 40);
    }

    @Test
    void semanticCornerOffsetsCreateAsymmetricPanel() {
        UiPrimitive primitive = UiPrimitive.builder(0, 0, 100, 40)
                .cornerOffset(UiPrimitive.Corner.BOTTOM_LEFT, -12, 8)
                .cornerOffset(UiPrimitive.Corner.BOTTOM_RIGHT, 20, 4)
                .build();

        assertEquals(4, primitive.pointCount());
        assertPoint(primitive, 2, 120, 44);
        assertPoint(primitive, 3, -12, 48);
        assertEquals(-12.0f, primitive.bounds().x());
        assertEquals(132.0f, primitive.bounds().width());
        assertTrue(primitive.shaderEligible());
    }

    @Test
    void sideCornersOverrideOnlyTheRequestedSide() {
        UiPrimitive primitive = UiPrimitive.builder(0, 0, 100, 40)
                .sideCorners(UiPrimitive.Side.TOP,
                        UiCornerSpec.chamfered(8), UiCornerSpec.chamfered(12))
                .build();

        assertEquals(6, primitive.pointCount());
        assertPoint(primitive, 0, 8, 0);
        assertPoint(primitive, 1, 88, 0);
        assertPoint(primitive, 2, 100, 12);
        assertTrue(primitive.shaderEligible());
    }

    @Test
    void concavePresetFallsBackWithoutChangingBuilderContract() {
        UiPrimitive primitive = UiPrimitive.builder(0, 0, 120, 50)
                .preset(UiPrimitive.Preset.NOTCHED_TOP)
                .cut(8)
                .build();

        assertTrue(primitive.pointCount() >= 8);
        assertFalse(primitive.isConvex());
        assertFalse(primitive.shaderEligible());
    }

    @Test
    void customConvexPointsAreNormalizedAgainstLayoutBounds() {
        UiPrimitive primitive = UiPrimitive.builder(20, 30, 200, 100)
                .customConvex(0.1, 0, 1, 0, 0.9, 1, 0, 1)
                .rounding(3.5)
                .build();

        assertEquals(4, primitive.pointCount());
        assertPoint(primitive, 0, 40, 30);
        assertPoint(primitive, 2, 200, 130);
        assertTrue(primitive.shaderEligible());
    }

    @Test
    void edgeFactoriesPreserveTopologyIntent() {
        assertEquals(UiEdgeKind.CUT, UiEdgeSpec.cutCenter(12, 4).kind());
        assertEquals(UiEdgeKind.PROTRUSION, UiEdgeSpec.protrusionCenter(12, 4).kind());
        assertEquals(UiEdgeKind.INSET, UiEdgeSpec.inset(4).kind());
    }

    @Test
    void legacyBoxLoweringNowHonorsInsetCutAndProtrusionEdges() {
        UiBoxShape box = UiBoxShape.rect(0, 0, 100, 40)
                .top(UiEdgeSpec.cutCenter(20, 6))
                .right(UiEdgeSpec.inset(5))
                .bottom(UiEdgeSpec.protrusionCenter(20, 4))
                .build();
        double[] path = new double[64];
        int count = UiBoxPathBuilder.write(box, path, path.length / 2);

        assertTrue(count >= 10);
        assertTrue(containsPoint(path, count, 50, 6));
        assertTrue(containsPoint(path, count, 95, 0 + 0));
        assertTrue(containsPoint(path, count, 40, 44));
        assertTrue(containsPoint(path, count, 60, 44));
    }

    @Test
    void removedCornerIsNotLoweredAsChamfer() {
        UiBoxShape box = UiBoxShape.rect(0, 0, 100, 40)
                .topRight(UiCornerSpec.notched(10, 8))
                .build();
        double[] path = new double[32];
        int count = UiBoxPathBuilder.write(box, path, path.length / 2);

        assertTrue(containsPoint(path, count, 90, 8));
        assertTrue(containsPoint(path, count, 100, 8));
        assertFalse(containsPoint(path, count, 100, 0));
    }

    private static void assertPoint(UiPrimitive primitive, int index, double x, double y) {
        double[] points = primitive.points();
        assertEquals(x, points[index * 2], 0.0001);
        assertEquals(y, points[index * 2 + 1], 0.0001);
    }

    private static boolean containsPoint(double[] points, int count, double x, double y) {
        for (int i = 0; i < count; i++) {
            if (Math.abs(points[i * 2] - x) <= 0.0001 && Math.abs(points[i * 2 + 1] - y) <= 0.0001) {
                return true;
            }
        }
        return false;
    }
}
