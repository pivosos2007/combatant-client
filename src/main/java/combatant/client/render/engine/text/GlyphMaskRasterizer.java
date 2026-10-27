/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.text;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

final class GlyphMaskRasterizer {
    // One high-resolution mask per glyph and physical font size. Phase-dependent
    // atlases snap during animation even with NEAREST filtering.
    static final int SUPERSAMPLE = 4;
    static final int MASK_BORDER = SUPERSAMPLE + 2;
    static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);
    private final java.awt.Font face;
    private final OpenTypeShaper shaper;
    private final double lineHeight;
    private final double ascender;
    private final Map<Integer, java.awt.Font> sizes = new LinkedHashMap<>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, java.awt.Font> entry) {
            return size() > 32;
        }
    };

    GlyphMaskRasterizer(InputStream source, double lineHeight, double ascender) throws Exception {
        face = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, source);
        shaper = OpenTypeShaper.fromFont(face);
        var metrics = face.deriveFont(1024f).getLineMetrics("Hg", CONTEXT);
        // Legacy STB bitmap faces normalize against ascent - descent, excluding
        // hhea lineGap. Built-in MSDF faces instead pass their JSON lineHeight.
        this.lineHeight = lineHeight > 0 ? lineHeight
                : (metrics.getHeight() - metrics.getLeading()) / 1024.0;
        this.ascender = ascender > 0 ? ascender : metrics.getAscent() / 1024.0;
    }

    double emScale() { return 27.0 / lineHeight; }
    double baseline() { return ascender * emScale(); }
    OpenTypeShaper.ShapedRun shape(String text) { return shaper.shape(text); }
    boolean hasGlyph(int codePoint) { return shaper.canDisplay(codePoint); }

    boolean canShape(String text) {
        for (var glyph : shape(text).glyphs()) {
            if (glyph.glyphId() == face.getMissingGlyphCode()) return false;
        }
        return true;
    }

    /** Quarter-physical-pixel steps prevent scale animations from exhausting the atlas.
     * Coordinates and advances are never rounded to the raster-size key.
     */
    static int sizeKey(double emPixels) {
        if (!Double.isFinite(emPixels) || emPixels < 0.75 || emPixels > 256) return 0;
        return Math.max(64, (int) Math.round(emPixels * 4.0) * 16);
    }

    static double rasterPixels(int sizeKey) {
        return sizeKey / 64.0;
    }

    static double physicalQuadOrigin(double glyphOrigin, int maskOffset, double physicalPerTexel) {
        return glyphOrigin + maskOffset * physicalPerTexel;
    }


    Mask rasterize(Key key) {
        java.awt.Font font = sizes.computeIfAbsent(key.size(), size -> face.deriveFont(size / 64f));
        GlyphVector glyph = font.createGlyphVector(CONTEXT, new int[]{key.glyphId()});
        Rectangle outline = glyph.getGlyphPixelBounds(0, CONTEXT, 0, 0);
        if (outline.isEmpty()) return new Mask(0, 0, 0, 0, new byte[0]);
        // Padding is part of the mask geometry. It allows the final pixel-area
        // integration to sample outside the actual outline while it moves.
        Rectangle bounds = new Rectangle(outline.x - MASK_BORDER, outline.y - MASK_BORDER,
                outline.width + MASK_BORDER * 2, outline.height + MASK_BORDER * 2);
        BufferedImage image = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setColor(Color.WHITE);
            graphics.drawGlyphVector(glyph, -bounds.x, -bounds.y);
        } finally {
            graphics.dispose();
        }
        return new Mask(bounds.x, bounds.y, bounds.width, bounds.height,
                ((DataBufferByte) image.getRaster().getDataBuffer()).getData());
    }

    record Key(int glyphId, int size) { }
    record Mask(int left, int top, int width, int height, byte[] pixels) { }
}
