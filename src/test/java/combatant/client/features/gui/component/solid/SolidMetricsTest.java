package combatant.client.features.gui.component.solid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SolidMetricsTest {
    @Test
    void preservesDesignGeometryAndPixelAlignedDividers() {
        SolidMetrics metrics = SolidMetrics.floating(500, 330);
        assertEquals(6.0f, metrics.x());
        assertEquals(6.0f, metrics.y());
        assertEquals(488.0f, metrics.width());
        assertEquals(318.0f, metrics.height());
        assertEquals(33.0f, metrics.navigationWidth());
        assertEquals(101.0f, metrics.collectionWidth());
        assertEquals(354.0f, metrics.detailWidth());
        assertEquals(24.0f, metrics.headerHeight());
        assertEquals(38.0f, metrics.firstDividerX());
        assertEquals(139.0f, metrics.secondDividerX());
    }

    @Test
    void clampsThenScalesPaneRatios() {
        SolidMetrics metrics = SolidMetrics.floating(200, 150);
        assertEquals(360.0f, metrics.width());
        assertEquals(235.0f, metrics.height());
        assertEquals(360.0f * 33.0f / 488.0f, metrics.navigationWidth(), 0.0001f);
        assertEquals(360.0f * 101.0f / 488.0f, metrics.collectionWidth(), 0.0001f);
        assertEquals(235.0f * 24.0f / 318.0f, metrics.headerHeight(), 0.0001f);
    }
}
