package combatant.client.render.engine.renderer.ui.draw;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class UiGlassVisualTest {
    @Test
    void deferredDrawDataIsImmutableAndValueComparable() {
        var source = new ArrayList<>(List.of(1f, 2f));
        var visual = new UiGlassVisual(UiGlassVisual.RIPPLE.shader(), source, 24f);
        source.set(0, 100f);
        assertEquals(1f, visual.parameters().getFirst());
        assertEquals(16, visual.parameters().size());
        assertThrows(UnsupportedOperationException.class, () -> visual.parameters().set(0, 3f));
        assertEquals(visual, new UiGlassVisual(visual.shader(), List.of(1f, 2f), 24f));
        assertNotEquals(visual, visual.withVector(1, 1f, 0f, 0f, 0f));
        assertNotEquals(visual, UiGlassVisual.FLOW);
    }

    @Test
    void rejectsInvalidGpuInputsAndUnboundedSampling() {
        assertThrows(IllegalArgumentException.class, () -> UiGlassVisual.FLOW.withVector(0, Float.NaN, 0f, 0f, 0f));
        assertThrows(IllegalArgumentException.class, () -> UiGlassVisual.shader("example.frag", 257f));
        assertThrows(IllegalArgumentException.class, () -> UiGlassVisual.shader("example.frag", -1f));
        assertThrows(IllegalArgumentException.class, () -> UiGlassVisual.shader("example.frag", Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> UiGlassVisual.shader("example.vert", 1f));
        assertThrows(IllegalArgumentException.class, () -> UiGlassVisual.shader("example__analytic_clip.frag", 1f));
        assertThrows(IndexOutOfBoundsException.class, () -> UiGlassVisual.FLOW.withVector(4, 0f, 0f, 0f, 0f));
        assertThrows(IllegalArgumentException.class, () -> UiGlassVisual.DEFAULT.withVector(0, 1f, 0f, 0f, 0f));
    }

    @Test
    void materialCopiesPreserveTheSelectedVisual() {
        var visual = UiGlassVisual.RIPPLE.withVector(1, 0.5f, 0.1f, 0.2f, 0.3f);
        var material = UiLiquidGlassMaterial.DEFAULT.withVisual(visual);
        assertEquals(visual, material.withOptics(UiGlassOpticsSpec.LIQUID).visual());
        assertEquals(visual, material.withInteraction(UiGlassInteraction.FRAME_POINTER).visual());
        assertEquals(visual, material.withFrostedJitter(1f).visual());
        assertEquals(visual, material.withInnerGlow(0.5f, 4f, -1).visual());
        assertEquals(visual, material.withTintStrength(0.3f).visual());
        assertEquals(UiGlassVisual.DEFAULT, material.withVisual(null).visual());
        assertEquals(UiGlassVisual.DEFAULT, new UiLiquidGlassMaterial(UiGlassOpticsSpec.LIQUID,
                0f, 0f, 0f, -1, 0.2f, UiGlassInteraction.FRAME_POINTER).visual());
    }
}
