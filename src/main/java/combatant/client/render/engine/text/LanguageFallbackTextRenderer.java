/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text;

import combatant.client.render.engine.color.RenderColor;

/**
 * Keeps the selected Combatant font for glyphs it owns and delegates missing
 * Unicode runs to Minecraft's lazy font provider. The vanilla provider creates
 * glyph atlas pages on demand, so CJK and other large scripts do not make every
 * custom UI font permanently reserve a full Unicode atlas on the GPU.
 */
public final class LanguageFallbackTextRenderer implements TextRenderer {
    private final TextRenderer primary;
    private final TextRenderer fallback;

    private boolean building;
    private double scale = 1.0;
    private boolean scaleOnly;
    private boolean big;
    private double alpha = 1.0;
    private TextRenderer activeRenderer;
    private boolean ownsActiveRenderer;

    public LanguageFallbackTextRenderer(TextRenderer primary) {
        this(primary, VanillaTextRenderer.INSTANCE);
    }

    LanguageFallbackTextRenderer(TextRenderer primary, TextRenderer fallback) {
        if (primary == null) throw new IllegalArgumentException("Primary text renderer must not be null");
        this.primary = primary;
        this.fallback = fallback != null && fallback != primary ? fallback : primary;
    }

    public TextRenderer primary() {
        return primary;
    }

    public static CustomTextRenderer customPrimary(TextRenderer renderer) {
        TextRenderer current = renderer;
        while (current instanceof LanguageFallbackTextRenderer languageFallback) {
            current = languageFallback.primary;
        }
        return current instanceof CustomTextRenderer custom ? custom : null;
    }

    @Override
    public void setAlpha(double value) {
        alpha = value;
        primary.setAlpha(value);
        if (fallback != primary) fallback.setAlpha(value);
    }

    @Override
    public void begin(double scale, boolean scaleOnly, boolean big) {
        if (building) throw new IllegalStateException("LanguageFallbackTextRenderer.begin() called twice");
        this.scale = scale;
        this.scaleOnly = scaleOnly;
        this.big = big;
        this.activeRenderer = null;
        this.ownsActiveRenderer = false;
        this.building = true;
    }

    @Override
    public double getWidth(String text, int length, boolean shadow) {
        if (text == null || text.isEmpty() || length <= 0) return 0.0;
        int end = safeUtf16End(text, length);
        double width = visitRuns(text, end, (renderer, start, finish) ->
                withRenderer(renderer, () -> renderer.getWidth(text.substring(start, finish), false)));
        return width + (shadow ? 1.0 : 0.0);
    }

    @Override
    public double getHeight(boolean shadow) {
        // Preserve the selected family's layout metrics. Fallback glyphs are
        // baseline-aligned during rendering and must not make HUD rows jump.
        return withRenderer(primary, () -> primary.getHeight(shadow));
    }

    @Override
    public boolean hasGlyph(int codePoint) {
        // Report primary coverage so rich-text callers can still select their
        // explicit SVG / VanillaSymbols fallbacks. render() itself is universal.
        return primary.hasGlyph(codePoint);
    }

    @Override
    public double render(String text, double x, double y, RenderColor color, boolean shadow) {
        if (text == null || text.isEmpty()) return x;
        boolean implicitBegin = !building;
        if (implicitBegin) begin();
        try {
            final double[] cursor = {x};
            visitRuns(text, text.length(), (renderer, start, finish) -> {
                String run = text.substring(start, finish);
                withRenderer(renderer, () -> {
                    double advance = renderer.getWidth(run, false);
                    renderer.render(run, cursor[0], y, color, shadow);
                    cursor[0] += advance;
                    return 0.0;
                });
                return 0.0;
            });
            return cursor[0];
        } finally {
            if (implicitBegin) end();
        }
    }

    @Override
    public boolean isBuilding() {
        return building;
    }

    @Override
    public void end() {
        if (!building) throw new IllegalStateException("LanguageFallbackTextRenderer.end() called without begin()");
        closeActiveRenderer();
        building = false;
        scale = 1.0;
        scaleOnly = false;
        big = false;
    }

    public void destroy() {
        if (primary instanceof CustomTextRenderer custom) {
            custom.destroy();
        } else if (primary instanceof LanguageFallbackTextRenderer nested) {
            nested.destroy();
        }
    }

    private double visitRuns(String text, int end, RunVisitor visitor) {
        if (VanillaTextRenderer.containsRightToLeftCodePoint(text.substring(0, end))) {
            return visitor.visit(fallback, 0, end);
        }

        double result = 0.0;
        int runStart = 0;
        TextRenderer runRenderer = null;
        for (int offset = 0; offset < end; ) {
            int clusterEnd = nextClusterEnd(text, offset, end);
            TextRenderer renderer = rendererForCluster(text, offset, clusterEnd);
            if (runRenderer != null && renderer != runRenderer) {
                result += visitor.visit(runRenderer, runStart, offset);
                runStart = offset;
            }
            runRenderer = renderer;
            offset = clusterEnd;
        }
        if (runRenderer != null && runStart < end) {
            result += visitor.visit(runRenderer, runStart, end);
        }
        return result;
    }

    private TextRenderer rendererForCluster(String text, int start, int end) {
        for (int offset = start; offset < end; ) {
            int codePoint = text.codePointAt(offset);
            if (!primary.hasGlyph(codePoint)) return fallback;
            offset += Character.charCount(codePoint);
        }
        return primary;
    }

    private static int nextClusterEnd(String text, int start, int limit) {
        int offset = start + Character.charCount(text.codePointAt(start));
        boolean afterJoiner = false;
        while (offset < limit) {
            int codePoint = text.codePointAt(offset);
            if (afterJoiner) {
                offset += Character.charCount(codePoint);
                afterJoiner = false;
                continue;
            }
            if (codePoint == 0x200D) {
                offset += Character.charCount(codePoint);
                afterJoiner = true;
                continue;
            }
            int type = Character.getType(codePoint);
            boolean combining = type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK;
            boolean variationSelector = codePoint >= 0xFE00 && codePoint <= 0xFE0F
                    || codePoint >= 0xE0100 && codePoint <= 0xE01EF;
            boolean emojiModifier = codePoint >= 0x1F3FB && codePoint <= 0x1F3FF;
            if (!combining && !variationSelector && !emojiModifier) break;
            offset += Character.charCount(codePoint);
        }
        return offset;
    }

    private double withRenderer(TextRenderer renderer, DoubleSupplier action) {
        if (!building) {
            boolean alreadyBuilding = renderer.isBuilding();
            if (!alreadyBuilding) {
                renderer.setAlpha(alpha);
                renderer.begin(scale, scaleOnly, big);
            }
            try {
                return action.getAsDouble();
            } finally {
                if (!alreadyBuilding) renderer.end();
            }
        }

        if (activeRenderer != renderer) {
            closeActiveRenderer();
            activeRenderer = renderer;
            ownsActiveRenderer = !renderer.isBuilding();
            if (ownsActiveRenderer) {
                renderer.setAlpha(alpha);
                renderer.begin(scale, scaleOnly, big);
            }
        }
        return action.getAsDouble();
    }

    private void closeActiveRenderer() {
        if (activeRenderer != null && ownsActiveRenderer) {
            activeRenderer.end();
        }
        activeRenderer = null;
        ownsActiveRenderer = false;
    }

    private static int safeUtf16End(String text, int requestedLength) {
        int end = Math.max(0, Math.min(requestedLength, text.length()));
        if (end > 0 && end < text.length()
                && Character.isHighSurrogate(text.charAt(end - 1))
                && Character.isLowSurrogate(text.charAt(end))) {
            end--;
        }
        return end;
    }

    @FunctionalInterface
    private interface RunVisitor {
        double visit(TextRenderer renderer, int start, int end);
    }

    @FunctionalInterface
    private interface DoubleSupplier {
        double getAsDouble();
    }
}
