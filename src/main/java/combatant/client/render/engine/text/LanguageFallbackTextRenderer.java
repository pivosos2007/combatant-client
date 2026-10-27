/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text;

import combatant.client.features.module.modules.misc.NameProtect;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.renderer.RenderWarpStack;
import combatant.client.render.engine.renderer.ui.blend.UiBackdropBlendSpec;
import combatant.client.render.engine.renderer.ui.draw.UiBackdropRequest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps the selected Combatant font for glyphs it owns and delegates missing
 * Unicode runs to Minecraft's lazy font provider. The vanilla provider creates
 * glyph atlas pages on demand, so CJK and other large scripts do not make every
 * custom UI font permanently reserve a full Unicode atlas on the GPU.
 */
public final class LanguageFallbackTextRenderer implements TextRenderer {
    // JDK's extended grapheme cluster matcher follows Unicode text boundaries,
    // including ZWJ emoji, regional-indicator flags and combining marks.
    private static final Pattern GRAPHEME_CLUSTER = Pattern.compile("\\X");
    private static final int WIDTH_CACHE_SLOTS = 512;

    private record WidthEntry(String text, int length, int flags, long scaleBits, long densityBits,
                              long fontsGeneration, long nameProtectRevision, long fontState,
                              double width) {
    }

    private final TextRenderer primary;
    private final TextRenderer fallback;

    private boolean building;
    private double scale = 1.0;
    private boolean scaleOnly;
    private boolean big;
    private double alpha = 1.0;
    private TextRenderer activeRenderer;
    private boolean ownsActiveRenderer;
    private boolean primedPrimary;
    private TextBackendPreference backendPreference = TextBackendPreference.AUTO;
    private boolean measuredWithFallback;
    private final WidthEntry[] widthCache = new WidthEntry[WIDTH_CACHE_SLOTS];

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
        begin(scale, scaleOnly, big, TextBackendPreference.AUTO);
    }

    @Override
    public void begin(double scale, boolean scaleOnly, boolean big, TextBackendPreference preference) {
        if (building) throw new IllegalStateException("LanguageFallbackTextRenderer.begin() called twice");
        this.scale = scale;
        this.scaleOnly = scaleOnly;
        this.big = big;
        this.backendPreference = preference != null ? preference : TextBackendPreference.AUTO;
        this.activeRenderer = null;
        this.ownsActiveRenderer = false;
        this.primedPrimary = false;
        this.building = true;
        // Resolve bitmap/MSDF coverage before splitting Unicode into font runs.
        if (primary instanceof CustomTextRenderer && !primary.isBuilding()) {
            try {
                primary.setAlpha(alpha);
                primary.begin(scale, scaleOnly, big, backendPreference);
                primedPrimary = true;
            } catch (RuntimeException failure) {
                building = false;
                throw failure;
            }
        }
    }

    @Override
    public double getWidth(String text, int length, boolean shadow) {
        if (text == null || text.isEmpty() || length <= 0) return 0.0;
        CustomTextRenderer custom = widthCacheablePrimary();
        if (custom == null) return measureWidth(text, length, shadow);

        int requested = Math.min(length, text.length());
        TextBackendPreference measuredPreference = building ? backendPreference : TextBackendPreference.AUTO;
        int flags = (shadow ? 1 : 0)
                | (big ? 2 : 0)
                | (building ? 4 : 0)
                | (custom.isBuilding() ? 8 : 0)
                | (RenderWarpStack.active() ? 16 : 0)
                | (measuredPreference.ordinal() << 5);
        long scaleBits = Double.doubleToLongBits(scale);
        long densityBits = Double.doubleToLongBits(FontRasterSelection.activePixelDensity());
        long fontsGeneration = Fonts.generation();
        long nameProtectRevision = NameProtect.revision();
        long fontState = custom.measureStateToken();

        int hash = text.hashCode();
        int slot = ((hash ^ (hash >>> 16)) + requested * 0x9E3779B1) & (WIDTH_CACHE_SLOTS - 1);
        WidthEntry cached = widthCache[slot];
        if (cached != null
                && cached.length == requested
                && cached.flags == flags
                && cached.scaleBits == scaleBits
                && cached.densityBits == densityBits
                && cached.fontsGeneration == fontsGeneration
                && cached.nameProtectRevision == nameProtectRevision
                && cached.fontState == fontState
                && (cached.text == text || cached.text.equals(text))) {
            return cached.width;
        }

        measuredWithFallback = false;
        double width = measureWidth(text, length, shadow);
        // Runs measured by the vanilla fallback depend on Minecraft's font state, which has no
        // invalidation signal here, so only primary-font measurements are cached.
        if (!measuredWithFallback) {
            widthCache[slot] = new WidthEntry(text, requested, flags, scaleBits, densityBits,
                    fontsGeneration, nameProtectRevision, fontState, width);
        }
        return width;
    }

    /**
     * The custom primary whose measurement state is fully described by the cache key, or null when
     * an external begin() owns the primary (its scale/font selection is unknown here).
     */
    private CustomTextRenderer widthCacheablePrimary() {
        if (!(primary instanceof CustomTextRenderer custom)) return null;
        if (!custom.isBuilding()) return custom;
        boolean ownedByThis = building && (primedPrimary || (activeRenderer == primary && ownsActiveRenderer));
        return ownedByThis ? custom : null;
    }

    private double measureWidth(String text, int length, boolean shadow) {
        int rawEnd = safeUtf16End(text, length);
        String measured = NameProtect.applyMeasure(text.substring(0, rawEnd));
        boolean implicitBegin = !building;
        if (implicitBegin) begin(scale, true, big);
        try {
            return measureRuns(measured, shadow);
        } finally {
            if (implicitBegin) end();
        }
    }

    private double measureRuns(String measured, boolean shadow) {
        int end = measured.length();
        double width = visitRuns(measured, end, (renderer, start, finish) -> {
            if (renderer != primary) measuredWithFallback = true;
            return measureWithRenderer(renderer, () -> renderer.getWidth(measured.substring(start, finish), false));
        });
        if (!shadow) return width;
        // The primary font owns line metrics, including its scaled shadow offset.
        // A fixed +1 is incorrect for both unscaledLogical and non-default font scales.
        return width + measureWithRenderer(primary, () ->
                primary.getWidth(" ", true) - primary.getWidth(" ", false));
    }

    @Override
    public double getHeight(boolean shadow) {
        // Preserve the selected family's layout metrics. Fallback glyphs are
        // baseline-aligned during rendering and must not make HUD rows jump.
        return measureWithRenderer(primary, () -> primary.getHeight(shadow));
    }

    @Override
    public boolean hasGlyph(int codePoint) {
        // Report primary coverage so rich-text callers can still select their
        // explicit SVG / VanillaSymbols fallbacks. render() itself is universal.
        return primary.hasGlyph(codePoint);
    }

    @Override
    public double render(String text, double x, double y, RenderColor color, boolean shadow) {
        String displayText = NameProtect.applyDisplay(text);
        if (displayText == null || displayText.isEmpty()) return x;
        boolean implicitBegin = !building;
        if (implicitBegin) begin();
        try {
            final double[] cursor = {x};
            visitRuns(displayText, displayText.length(), (renderer, start, finish) -> {
                String run = displayText.substring(start, finish);
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
    public double renderGradient(String text,
                                 double x,
                                 double y,
                                 Font.GlyphGradient gradient,
                                 boolean shadow) {
        String displayText = NameProtect.applyDisplay(text);
        if (displayText == null || displayText.isEmpty() || gradient == null) return x;
        boolean implicitBegin = !building;
        if (implicitBegin) begin();
        try {
            final double[] cursor = {x};
            final int[] glyphBase = {0};
            visitRuns(displayText, displayText.length(), (renderer, start, finish) -> {
                String run = displayText.substring(start, finish);
                int runGlyphBase = glyphBase[0];
                withRenderer(renderer, () -> {
                    cursor[0] = renderer.renderGradient(
                            run,
                            cursor[0],
                            y,
                            (index, codePoint, glyphX, out) ->
                                    gradient.colors(runGlyphBase + index, codePoint, glyphX, out),
                            shadow
                    );
                    return 0.0;
                });
                glyphBase[0] += run.codePointCount(0, run.length());
                return 0.0;
            });
            return cursor[0];
        } finally {
            if (implicitBegin) end();
        }
    }

    @Override
    public double renderQuadGradient(String text,
                                     double x,
                                     double y,
                                     Font.GlyphQuadGradient gradient,
                                     boolean shadow) {
        String displayText = NameProtect.applyDisplay(text);
        if (displayText == null || displayText.isEmpty() || gradient == null) return x;
        boolean implicitBegin = !building;
        if (implicitBegin) begin();
        try {
            final double[] cursor = {x};
            final int[] glyphBase = {0};
            visitRuns(displayText, displayText.length(), (renderer, start, finish) -> {
                String run = displayText.substring(start, finish);
                int runGlyphBase = glyphBase[0];
                withRenderer(renderer, () -> {
                    cursor[0] = renderer.renderQuadGradient(
                            run,
                            cursor[0],
                            y,
                            (index, codePoint, x0, y0, x1, y1, out) ->
                                    gradient.colors(runGlyphBase + index, codePoint, x0, y0, x1, y1, out),
                            shadow
                    );
                    return 0.0;
                });
                glyphBase[0] += run.codePointCount(0, run.length());
                return 0.0;
            });
            return cursor[0];
        } finally {
            if (implicitBegin) end();
        }
    }

    @Override
    public double renderLiquidGlassQuadGradient(String text,
                                                 double x,
                                                 double y,
                                                 Font.GlyphQuadGradient gradient,
                                                 double boundsX,
                                                 double boundsY,
                                                 double boundsWidth,
                                                 double boundsHeight) {
        return renderLiquidGlassQuadGradient(text, x, y, gradient, boundsX, boundsY, boundsWidth, boundsHeight, null);
    }

    @Override
    public double renderLiquidGlassQuadGradient(String text,
                                                 double x,
                                                 double y,
                                                 Font.GlyphQuadGradient gradient,
                                                 double boundsX,
                                                 double boundsY,
                                                 double boundsWidth,
                                                 double boundsHeight,
                                                 RenderPipeline pipelineOverride) {
        return renderLiquidGlassQuadGradient(text, x, y, gradient, boundsX, boundsY,
                boundsWidth, boundsHeight, pipelineOverride, null);
    }

    @Override
    public double renderLiquidGlassQuadGradient(String text,
                                                 double x,
                                                 double y,
                                                 Font.GlyphQuadGradient gradient,
                                                 double boundsX,
                                                 double boundsY,
                                                 double boundsWidth,
                                                 double boundsHeight,
                                                 RenderPipeline pipelineOverride,
                                                 UiBackdropRequest backdrop) {
        String displayText = NameProtect.applyDisplay(text);
        if (displayText == null || displayText.isEmpty() || gradient == null) return x;
        boolean implicitBegin = !building;
        if (implicitBegin) begin();
        try {
            final double[] cursor = {x};
            final int[] glyphBase = {0};
            visitRuns(displayText, displayText.length(), (renderer, start, finish) -> {
                String run = displayText.substring(start, finish);
                int runGlyphBase = glyphBase[0];
                withRenderer(renderer, () -> {
                    cursor[0] = renderer.renderLiquidGlassQuadGradient(
                            run,
                            cursor[0],
                            y,
                            (index, codePoint, x0, y0, x1, y1, out) ->
                                    gradient.colors(runGlyphBase + index, codePoint, x0, y0, x1, y1, out),
                            boundsX,
                            boundsY,
                            boundsWidth,
                            boundsHeight,
                            pipelineOverride,
                            backdrop
                    );
                    return 0.0;
                });
                glyphBase[0] += run.codePointCount(0, run.length());
                return 0.0;
            });
            return cursor[0];
        } finally {
            if (implicitBegin) end();
        }
    }

    @Override
    public double renderLiquidGlassBlendQuadGradient(String text,
                                                      double x,
                                                      double y,
                                                      Font.GlyphQuadGradient gradient,
                                                      double boundsX,
                                                      double boundsY,
                                                      double boundsWidth,
                                                      double boundsHeight,
                                                      UiBackdropBlendSpec blend,
                                                      UiBackdropRequest backdrop) {
        String displayText = NameProtect.applyDisplay(text);
        if (displayText == null || displayText.isEmpty() || gradient == null) return x;
        boolean implicitBegin = !building;
        if (implicitBegin) begin();
        try {
            final double[] cursor = {x};
            final int[] glyphBase = {0};
            visitRuns(displayText, displayText.length(), (renderer, start, finish) -> {
                String run = displayText.substring(start, finish);
                int runGlyphBase = glyphBase[0];
                withRenderer(renderer, () -> {
                    cursor[0] = renderer.renderLiquidGlassBlendQuadGradient(
                            run,
                            cursor[0],
                            y,
                            (index, codePoint, x0, y0, x1, y1, out) ->
                                    gradient.colors(runGlyphBase + index, codePoint, x0, y0, x1, y1, out),
                            boundsX,
                            boundsY,
                            boundsWidth,
                            boundsHeight,
                            blend,
                            backdrop
                    );
                    return 0.0;
                });
                glyphBase[0] += run.codePointCount(0, run.length());
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
        try {
            closeActiveRenderer();
        } finally {
            if (primedPrimary) {
                primedPrimary = false;
                if (primary.isBuilding()) primary.end();
            }
        }
        building = false;
        scale = 1.0;
        scaleOnly = false;
        big = false;
        backendPreference = TextBackendPreference.AUTO;
    }

    public void destroy() {
        if (primary instanceof CustomTextRenderer custom) {
            custom.destroy();
        } else if (primary instanceof LanguageFallbackTextRenderer nested) {
            nested.destroy();
        }
    }

    private double visitRuns(String text, int end, RunVisitor visitor) {
        if (end <= 0) return 0.0;
        if (VanillaTextRenderer.containsRightToLeftCodePoint(text.substring(0, end))) {
            return visitor.visit(fallback, 0, end);
        }

        // Simple code-point runs (including Cyrillic, Latin extensions and CJK)
        // do not require regex segmentation or per-frame Matcher allocations.
        // Only complex grapheme sequences take the Unicode segmentation path.
        if (!mayContainMultiCodepointGraphemes(text, end)) {
            double result = 0.0;
            int runStart = 0;
            TextRenderer runRenderer = null;
            for (int offset = 0; offset < end; ) {
                int codePoint = text.codePointAt(offset);
                TextRenderer renderer = primary.hasGlyph(codePoint) ? primary : fallback;
                if (runRenderer != null && runRenderer != renderer) {
                    result += visitor.visit(runRenderer, runStart, offset);
                    runStart = offset;
                }
                runRenderer = renderer;
                offset += Character.charCount(codePoint);
            }
            if (runRenderer != null && runStart < end) result += visitor.visit(runRenderer, runStart, end);
            return result;
        }

        Matcher matcher = GRAPHEME_CLUSTER.matcher(text);
        matcher.region(0, end);
        double result = 0.0;
        int runStart = 0;
        TextRenderer runRenderer = null;
        while (matcher.find()) {
            int clusterStart = matcher.start();
            int clusterEnd = matcher.end();
            TextRenderer renderer = rendererForCluster(text, clusterStart, clusterEnd);
            if (runRenderer != null && renderer != runRenderer) {
                result += visitor.visit(runRenderer, runStart, clusterStart);
                runStart = clusterStart;
            }
            runRenderer = renderer;
        }
        if (runRenderer != null && runStart < end) {
            result += visitor.visit(runRenderer, runStart, end);
        }
        return result;
    }

    private static boolean mayContainMultiCodepointGraphemes(String text, int end) {
        for (int i = 0; i < end; ) {
            int cp = text.codePointAt(i);
            int type = Character.getType(cp);
            if (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK || cp == 0x200C || cp == 0x200D
                    || (cp >= 0x1F1E6 && cp <= 0x1F1FF)
                    || (cp >= 0x1F3FB && cp <= 0x1F3FF)
                    || (cp >= 0x1100 && cp <= 0x11FF)
                    || (cp >= 0xFE00 && cp <= 0xFE0F)
                    || (cp >= 0xE0020 && cp <= 0xE007F)
                    || (cp >= 0xE0100 && cp <= 0xE01EF)) return true;
            i += Character.charCount(cp);
        }
        return false;
    }

    private TextRenderer rendererForCluster(String text, int start, int end) {
        // Extended graphemes are indivisible font runs. The custom renderer now
        // supports GSUB/GPOS and dynamic glyph-ID rasterization, so combining
        // marks and contextual forms stay with the selected font when available.
        // Keep emoji ZWJ sequences with Minecraft's provider: mere glyph coverage
        // does not imply a valid multi-color emoji composition in a R8 atlas.
        int first = text.codePointAt(start);
        if (start + Character.charCount(first) != end) {
            if (text.substring(start, end).indexOf('\u200d') >= 0) return fallback;
            for (int i = start; i < end; ) {
                int cp = text.codePointAt(i);
                if (!primary.hasGlyph(cp)) return fallback;
                i += Character.charCount(cp);
            }
            if (primary instanceof CustomTextRenderer custom
                    && custom.canShapeCluster(text.substring(start, end))) return primary;
            return fallback;
        }
        return primary.hasGlyph(first) ? primary : fallback;
    }

    /** Measurement never changes the active draw run or forces a pending glyph batch to flush. */
    private double measureWithRenderer(TextRenderer renderer, DoubleSupplier action) {
        if (renderer.isBuilding()) return action.getAsDouble();
        renderer.setAlpha(alpha);
        renderer.begin(scale, true, big, backendPreference);
        try {
            return action.getAsDouble();
        } finally {
            renderer.end();
        }
    }

    private double withRenderer(TextRenderer renderer, DoubleSupplier action) {
        if (!building) {
            boolean alreadyBuilding = renderer.isBuilding();
            if (!alreadyBuilding) {
                renderer.setAlpha(alpha);
                renderer.begin(scale, scaleOnly, big, backendPreference);
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
                renderer.begin(scale, scaleOnly, big, backendPreference);
            }
        }
        return action.getAsDouble();
    }

    private void closeActiveRenderer() {
        if (activeRenderer != null) {
            if (ownsActiveRenderer) {
                activeRenderer.end();
            } else if (activeRenderer == primary && primedPrimary) {
                // Primary was primed in begin() for coverage detection. It still
                // owns its draw batch: submit it before a fallback run is drawn,
                // otherwise Latin glyphs can appear above later Unicode glyphs.
                primary.end();
                primedPrimary = false;
            }
        }
        activeRenderer = null;
        ownsActiveRenderer = false;
    }

    static int safeUtf16End(String text, int requestedLength) {
        int end = Math.max(0, Math.min(requestedLength, text.length()));
        if (end == 0 || end == text.length()) return end;
        if (Character.isHighSurrogate(text.charAt(end - 1))
                && Character.isLowSurrogate(text.charAt(end))) end--;

        // A prefix width is also used for caret positioning. Stopping between
        // a base letter and its combining mark, or in the middle of a ZWJ emoji,
        // must not create half a grapheme that the renderer would never draw.
        // Keep simple Latin/Cyrillic prefixes on the allocation-free fast path.
        if (!mayContainMultiCodepointGraphemes(text, text.length())) return end;
        Matcher matcher = GRAPHEME_CLUSTER.matcher(text);
        int boundary = 0;
        while (matcher.find()) {
            if (matcher.end() > end) break;
            boundary = matcher.end();
        }
        return boundary;
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
