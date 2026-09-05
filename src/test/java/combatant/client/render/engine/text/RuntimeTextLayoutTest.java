/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text;

import combatant.client.render.engine.color.RenderColor;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class RuntimeTextLayoutTest {
    @Test
    void keepsVisibleSeparatorsButFlattensUnsafeSingleLineControls() {
        assertEquals("| prefix suffix end", RuntimeTextLayout.singleLine("| prefix\nsuffix\tend\u202E"));
    }

    @Test
    void preservesComponentTextWhileFlatteningLineBreaks() {
        Component input = Component.literal("| prefix").append(Component.literal("\nsuffix"));
        assertEquals("| prefix suffix", RuntimeTextLayout.singleLine(input).getString());
    }

    @Test
    void measuresWithTheRequestedRenderScaleAndSanitizedText() {
        FakeRenderer renderer = new FakeRenderer();
        RuntimeTextLayout.Metrics metrics = RuntimeTextLayout.measure(renderer, "A\nB", 0.75, true);

        assertEquals("A B", metrics.text());
        assertEquals(3.25, metrics.width());
        assertEquals(8.5, metrics.height());
        assertEquals(0.75, renderer.lastScale);
        assertFalse(renderer.isBuilding());
    }

    private static final class FakeRenderer implements TextRenderer {
        private boolean building;
        private double lastScale;

        @Override
        public void setAlpha(double a) {
        }

        @Override
        public void begin(double scale, boolean scaleOnly, boolean big) {
            building = true;
            lastScale = scale;
        }

        @Override
        public double getWidth(String text, int length, boolean shadow) {
            return length * lastScale + (shadow ? 1.0 : 0.0);
        }

        @Override
        public double getHeight(boolean shadow) {
            return 10.0 * lastScale + (shadow ? 1.0 : 0.0);
        }

        @Override
        public double render(String text, double x, double y, RenderColor color, boolean shadow) {
            return x + getWidth(text, text.length(), shadow);
        }

        @Override
        public boolean isBuilding() {
            return building;
        }

        @Override
        public void end() {
            building = false;
        }
    }
}
