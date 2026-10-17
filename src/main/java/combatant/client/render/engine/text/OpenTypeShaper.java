/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.text;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextAttribute;
import java.awt.geom.Point2D;
import java.io.InputStream;
import java.text.Bidi;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shapes Unicode into font-specific glyph IDs and positioned runs, in em units.
 *
 * <p>Uses the Java runtime OpenType layout engine (typically backed by HarfBuzz
 * in OpenJDK). This is not a codepoint-to-glyph substitution table: GSUB/GPOS,
 * kerning and bidi visual run order are resolved before atlas lookup. An atlas
 * must still contain the resulting glyph IDs; consumers must fall back when it
 * does not. The shaper owns no GPU resources and is shared by MSDF sizes.</p>
 */
public final class OpenTypeShaper {
    private static final float EM_SIZE = 1024f;
    private static final int CACHE_LIMIT = 384;
    private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);
    private final Font font;
    private final Map<Integer, Integer> characterMap = new HashMap<>();
    private final Map<String, ShapedRun> cache = new LinkedHashMap<>(CACHE_LIMIT, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ShapedRun> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    private OpenTypeShaper(Font font) {
        Map<TextAttribute, Object> attributes = new HashMap<>();
        attributes.put(TextAttribute.SIZE, EM_SIZE);
        attributes.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
        attributes.put(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON);
        this.font = font.deriveFont(attributes);
    }

    static OpenTypeShaper fromFont(Font font) {
        return new OpenTypeShaper(font);
    }

    public static OpenTypeShaper load(InputStream stream) {
        try (InputStream in = stream) {
            return new OpenTypeShaper(Font.createFont(Font.TRUETYPE_FONT, in));
        } catch (Exception | LinkageError exception) {
            return null; // Atlas-only faces and unsupported font formats keep their legacy path.
        }
    }

    public boolean canDisplay(int codePoint) {
        return font.canDisplay(codePoint);
    }

    public synchronized int glyphId(int codePoint) {
        Integer cached = characterMap.get(codePoint);
        if (cached != null) return cached;
        int result = -1;
        if (Character.isValidCodePoint(codePoint) && font.canDisplay(codePoint)) {
            char[] text = Character.toChars(codePoint);
            GlyphVector glyphs = font.createGlyphVector(CONTEXT, text);
            if (glyphs.getNumGlyphs() == 1) result = glyphs.getGlyphCode(0);
        }
        if (characterMap.size() >= 4096) characterMap.clear();
        characterMap.put(codePoint, result);
        return result;
    }

    public synchronized ShapedRun shape(String text) {
        if (text == null || text.isEmpty()) return new ShapedRun(new PositionedGlyph[0], 0.0);
        ShapedRun existing = cache.get(text);
        if (existing != null) return existing;
        ShapedRun result = layout(text);
        cache.put(text, result);
        return result;
    }

    private ShapedRun layout(String text) {
        char[] chars = text.toCharArray();
        Bidi bidi = new Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
        int count = bidi.getRunCount();
        VisualRun[] runs = new VisualRun[count];
        byte[] levels = new byte[count];
        for (int i = 0; i < count; i++) {
            int level = bidi.getRunLevel(i);
            runs[i] = new VisualRun(bidi.getRunStart(i), bidi.getRunLimit(i), (level & 1) != 0);
            levels[i] = (byte) level;
        }
        Bidi.reorderVisually(levels, 0, runs, 0, count);
        PositionedGlyph[] result = new PositionedGlyph[Math.max(chars.length, 8)];
        int written = 0;
        double cursor = 0.0;
        for (VisualRun run : runs) {
            GlyphVector vector = font.layoutGlyphVector(CONTEXT, chars, run.start, run.end,
                    run.rtl ? Font.LAYOUT_RIGHT_TO_LEFT : Font.LAYOUT_LEFT_TO_RIGHT);
            int n = vector.getNumGlyphs();
            if (written + n > result.length) result = Arrays.copyOf(result, Math.max(result.length * 2, written + n));
            for (int i = 0; i < n; i++) {
                // Indices are UTF-16 offsets into the input; one glyph can represent a
                // multi-codepoint cluster and multiple glyphs may refer to one cluster.
                int at = Math.max(run.start, Math.min(run.end - 1, run.start + vector.getGlyphCharIndex(i)));
                Point2D position = vector.getGlyphPosition(i);
                result[written++] = new PositionedGlyph(vector.getGlyphCode(i),
                        chars.length == 0 ? -1 : Character.codePointAt(chars, at),
                        at, cursor + position.getX() / EM_SIZE, position.getY() / EM_SIZE);
            }
            cursor += vector.getGlyphPosition(n).getX() / EM_SIZE;
        }
        return new ShapedRun(Arrays.copyOf(result, written), cursor);
    }

    private record VisualRun(int start, int end, boolean rtl) { }

    public record PositionedGlyph(int glyphId, int codePoint, int cluster, double x, double y) { }

    public record ShapedRun(PositionedGlyph[] glyphs, double advance) { }
}
