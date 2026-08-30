/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text;

import combatant.client.render.engine.color.RenderColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LanguageFallbackTextRendererTest {
    @Test
    void splitsMissingUnicodeIntoContiguousFallbackRuns() {
        FakeRenderer primary = new FakeRenderer(cp -> cp < 0x80, 1.0, 10.0);
        FakeRenderer fallback = new FakeRenderer(cp -> true, 2.0, 12.0);
        LanguageFallbackTextRenderer renderer = new LanguageFallbackTextRenderer(primary, fallback);

        renderer.begin(1.0, false, false);
        assertEquals(6.0, renderer.getWidth("A中文B", false));
        assertEquals(10.0, renderer.render("A中文B", 4.0, 7.0, new RenderColor(0xFFFFFFFF), false));
        renderer.end();

        assertEquals(List.of("A", "B"), primary.rendered);
        assertEquals(List.of("中文"), fallback.rendered);
    }

    @Test
    void neverCutsASupplementaryCodePointDuringMeasurement() {
        FakeRenderer primary = new FakeRenderer(cp -> cp < 0x80, 1.0, 10.0);
        FakeRenderer fallback = new FakeRenderer(cp -> true, 2.0, 12.0);
        LanguageFallbackTextRenderer renderer = new LanguageFallbackTextRenderer(primary, fallback);
        String text = "A\uD83D\uDE00B";

        renderer.begin(1.0, false, false);
        assertEquals(1.0, renderer.getWidth(text, 2, false));
        assertEquals(3.0, renderer.getWidth(text, 3, false));
        renderer.end();
    }

    @Test
    void exposesPrimaryCoverageAndMetricsToRichTextCallers() {
        FakeRenderer primary = new FakeRenderer(cp -> cp < 0x80, 1.0, 10.0);
        FakeRenderer fallback = new FakeRenderer(cp -> true, 2.0, 18.0);
        LanguageFallbackTextRenderer renderer = new LanguageFallbackTextRenderer(primary, fallback);

        renderer.begin(1.0, false, false);
        assertTrue(renderer.hasGlyph('A'));
        assertFalse(renderer.hasGlyph('中'));
        assertEquals(10.0, renderer.getHeight(false));
        renderer.end();
    }

    @Test
    void keepsRtlSentencesAndCombiningClustersInOneFallbackRun() {
        FakeRenderer primary = new FakeRenderer(cp -> cp < 0x80, 1.0, 10.0);
        FakeRenderer fallback = new FakeRenderer(cp -> true, 1.0, 12.0);
        LanguageFallbackTextRenderer renderer = new LanguageFallbackTextRenderer(primary, fallback);

        renderer.begin(1.0, false, false);
        renderer.render("status: العربية ready", 0.0, 0.0, new RenderColor(0xFFFFFFFF), false);
        renderer.render("a\u0304", 0.0, 0.0, new RenderColor(0xFFFFFFFF), false);
        renderer.end();

        assertEquals(List.of("status: العربية ready", "a\u0304"), fallback.rendered);
        assertTrue(primary.rendered.isEmpty());
    }

    @Test
    void preservesNativeVertexGradientsAcrossLanguageRuns() {
        FakeRenderer primary = new FakeRenderer(cp -> cp < 0x80, 1.0, 10.0);
        FakeRenderer fallback = new FakeRenderer(cp -> true, 2.0, 12.0);
        LanguageFallbackTextRenderer renderer = new LanguageFallbackTextRenderer(primary, fallback);

        renderer.begin(1.0, false, false);
        assertEquals(4.0, renderer.renderQuadGradient(
                "A中B",
                0.0,
                0.0,
                (index, codePoint, x0, y0, x1, y1, out) -> {
                    out[0] = 0xFFFF0000 | index;
                    out[1] = 0xFF00FF00 | index;
                    out[2] = 0xFF0000FF | index;
                    out[3] = 0xFFFFFFFF - index;
                },
                false
        ));
        renderer.end();

        assertEquals(List.of(0, 0), primary.quadGradientIndices);
        assertEquals(List.of(0), fallback.quadGradientIndices);
        assertEquals(0xFFFF0000, primary.quadGradientColors.get(0)[0]);
        assertEquals(0xFF00FF01, fallback.quadGradientColors.get(0)[1]);
        assertEquals(0xFFFFFFFD, primary.quadGradientColors.get(1)[3]);
    }

    private static final class FakeRenderer implements TextRenderer {
        private final IntPredicate coverage;
        private final double advance;
        private final double height;
        private final List<String> rendered = new ArrayList<>();
        private final List<Integer> quadGradientIndices = new ArrayList<>();
        private final List<int[]> quadGradientColors = new ArrayList<>();
        private boolean building;

        private FakeRenderer(IntPredicate coverage, double advance, double height) {
            this.coverage = coverage;
            this.advance = advance;
            this.height = height;
        }

        @Override
        public void setAlpha(double a) {
        }

        @Override
        public void begin(double scale, boolean scaleOnly, boolean big) {
            if (building) throw new IllegalStateException("already building");
            building = true;
        }

        @Override
        public double getWidth(String text, int length, boolean shadow) {
            int codePoints = text.substring(0, Math.min(length, text.length())).codePointCount(0, Math.min(length, text.length()));
            return codePoints * advance + (shadow ? 1.0 : 0.0);
        }

        @Override
        public double getHeight(boolean shadow) {
            return height + (shadow ? 1.0 : 0.0);
        }

        @Override
        public boolean hasGlyph(int codePoint) {
            return coverage.test(codePoint);
        }

        @Override
        public double render(String text, double x, double y, RenderColor color, boolean shadow) {
            rendered.add(text);
            return x + getWidth(text, false);
        }

        @Override
        public double renderQuadGradient(String text,
                                         double x,
                                         double y,
                                         Font.GlyphQuadGradient gradient,
                                         boolean shadow) {
            int[] colors = new int[4];
            int index = 0;
            double cursor = x;
            for (int offset = 0; offset < text.length(); ) {
                int codePoint = text.codePointAt(offset);
                gradient.colors(index, codePoint, cursor, y, cursor + advance, y + height, colors);
                quadGradientIndices.add(index);
                quadGradientColors.add(colors.clone());
                cursor += advance;
                offset += Character.charCount(codePoint);
                index++;
            }
            return cursor;
        }

        @Override
        public boolean isBuilding() {
            return building;
        }

        @Override
        public void end() {
            if (!building) throw new IllegalStateException("not building");
            building = false;
        }
    }
}
