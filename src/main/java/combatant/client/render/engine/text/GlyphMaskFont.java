/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.text;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import combatant.client.render.engine.Texture;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.uniform.MeshBuilder;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;

final class GlyphMaskFont implements GlyphFont {
    // One 4x-coverage atlas per face; glyph origin does not affect cache identity.
    private static final int ATLAS_SIZE = 4096;
    private final GlyphMaskRasterizer rasterizer;
    private final String pageId = "mask:" + Integer.toHexString(System.identityHashCode(this));
    private Texture texture;
    private ByteBuffer pixels;
    private GlyphMaskCache cache;
    private boolean closed;
    // Never oscillate between mask and MSDF each frame when the atlas fills.
    private boolean saturated;

    GlyphMaskFont(InputStream source, GlyphFont reference) throws Exception {
        rasterizer = new GlyphMaskRasterizer(source,
                reference instanceof MsdfFont msdf ? msdf.lineHeightEm() : 0,
                reference instanceof MsdfFont msdf ? msdf.ascenderEm() : 0);
    }

    boolean prepare(String text, double x, double y, double scale, boolean shadow, double shadowOffset) {
        if (closed || saturated || !canShape(text)) return false;
        double density = FontRasterSelection.activePixelDensity();
        int size = GlyphMaskRasterizer.sizeKey(rasterizer.emScale() * scale * density);
        if (size == 0) return false;
        if (cache == null) {
            ByteBuffer allocated = MemoryUtil.memCalloc(ATLAS_SIZE * ATLAS_SIZE);
            Texture created = null;
            try {
                created = new Texture(ATLAS_SIZE, ATLAS_SIZE, GpuFormat.R8_UNORM,
                        FilterMode.LINEAR, FilterMode.LINEAR, AddressMode.CLAMP_TO_EDGE);
                created.upload(allocated);
            } catch (RuntimeException | Error failure) {
                if (created != null) created.close();
                MemoryUtil.memFree(allocated);
                throw failure;
            }
            pixels = allocated;
            texture = created;
            cache = new GlyphMaskCache(ATLAS_SIZE, pixels);
            TextRenderSystem.glyphAtlases().registerPage(pageId, texture, ATLAS_SIZE, ATLAS_SIZE, false);
            TextRenderSystem.glyphAtlases().onDirtyRectUpload(0, ATLAS_SIZE * ATLAS_SIZE);
        }
        cache.beginFrame(TextRenderSystem.glyphFrame());
        int previousGlyphs = cache.size();
        boolean complete = prepareRun(text, x, y, scale, density, size);
        if (complete && shadow) complete = prepareRun(text, x + shadowOffset, y + shadowOffset, scale, density, size);
        // An atlas overflow used to trigger a different raster backend every
        // other frame (reset -> fill -> fallback -> reset). Keep MSDF stable
        // for this face until the font is rebuilt, never recycle in-flight UVs.
        if (!complete) saturated = true;
        var dirty = cache.takeDirty();
        if (dirty != null) {
            texture.uploadAtlasRegion(pixels, 1, ATLAS_SIZE, 0,
                    dirty.x(), dirty.y(), dirty.width(), dirty.height());
            TextRenderSystem.glyphAtlases().onDirtyRectUpload(
                    Math.max(0, cache.size() - previousGlyphs), dirty.width() * dirty.height());
        }
        return complete;
    }

    private boolean prepareRun(String text, double x, double y, double scale, double density, int size) {
        double em = rasterizer.emScale() * scale;
        double baseline = y + rasterizer.baseline() * scale;
        for (var glyph : rasterizer.shape(text).glyphs()) {
            double gx = (x + glyph.x() * em) * density;
            double gy = (baseline + glyph.y() * em) * density;
            if (!Double.isFinite(gx) || !Double.isFinite(gy)
                    || cache.get(rasterizer, key(glyph.glyphId(), size)) == null) return false;
        }
        return true;
    }

    private static GlyphMaskRasterizer.Key key(int glyphId, int size) {
        return new GlyphMaskRasterizer.Key(glyphId, size * GlyphMaskRasterizer.SUPERSAMPLE);
    }

    @Override public Texture getTexture() { return texture; }
    @Override public boolean isReady() { return !closed && (texture == null || texture.isReady()); }
    @Override public int height() { return 27; }
    @Override public boolean hasGlyph(int codePoint) { return rasterizer.hasGlyph(codePoint); }
    @Override public boolean canShape(String text) { return rasterizer.canShape(text); }

    @Override public double getWidth(String text, int length) {
        if (text == null || length <= 0) return 0;
        int end = Math.min(length, text.length());
        if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))
                && Character.isLowSurrogate(text.charAt(end))) end--;
        return rasterizer.shape(text.substring(0, end)).advance() * rasterizer.emScale();
    }

    private double visit(String text, double x, double y, double scale, PositionedConsumer consumer) {
        double density = FontRasterSelection.activePixelDensity();
        double em = rasterizer.emScale() * scale;
        int size = GlyphMaskRasterizer.sizeKey(em * density);
        double baseline = y + rasterizer.baseline() * scale;
        // The quad follows the actual fractional glyph origin, with no phase
        // quantization. The high-resolution atlas is downsampled in text_mask.frag.
        var run = rasterizer.shape(text);
        for (var glyph : run.glyphs()) {
            double gx = (x + glyph.x() * em) * density;
            double gy = (baseline + glyph.y() * em) * density;
            var entry = cache.get(rasterizer, key(glyph.glyphId(), size));
            if (entry == null) throw new IllegalStateException("Glyph mask run was not prepared");
            if (entry.width() == 0) continue;
            // The size bucket only controls cached bitmap resolution, never the
            // actual advance, baseline or rendered scale during UI animation.
            double rasterEm = GlyphMaskRasterizer.rasterPixels(size);
            double toPhysical = em * density / rasterEm / GlyphMaskRasterizer.SUPERSAMPLE;
            double x0 = GlyphMaskRasterizer.physicalQuadOrigin(gx, entry.left(), toPhysical) / density;
            double y0 = GlyphMaskRasterizer.physicalQuadOrigin(gy, entry.top(), toPhysical) / density;
            consumer.accept(glyph, x0, y0,
                    x0 + entry.width() * toPhysical / density,
                    y0 + entry.height() * toPhysical / density,
                    entry.x() / (float) ATLAS_SIZE, entry.y() / (float) ATLAS_SIZE,
                    (entry.x() + entry.width()) / (float) ATLAS_SIZE, (entry.y() + entry.height()) / (float) ATLAS_SIZE);
        }
        return x + run.advance() * em;
    }

    @Override public double emitGlyphs(String text, double x, double y, double scale, GlyphConsumer consumer) {
        return visit(text, x, y, scale, (glyph, x0, y0, x1, y1, u0, v0, u1, v1) ->
                consumer.accept(x0, y0, x1, y1, u0, v0, u1, v1));
    }

    @Override public double render(MeshBuilder mesh, String text, double x, double y, RenderColor color, double scale) {
        return draw(mesh, text, x, y, scale, color.argb(), null, null);
    }

    @Override public double renderGradient(MeshBuilder mesh, String text, double x, double y, double scale,
                                           Font.GlyphGradient gradient) {
        return draw(mesh, text, x, y, scale, 0, gradient, null);
    }

    @Override public double renderQuadGradient(MeshBuilder mesh, String text, double x, double y, double scale,
                                               Font.GlyphQuadGradient gradient) {
        return draw(mesh, text, x, y, scale, 0, null, gradient);
    }

    private double draw(MeshBuilder mesh, String text, double x, double y, double scale, int color,
                        Font.GlyphGradient gradient, Font.GlyphQuadGradient quadGradient) {
        mesh.ensureCapacity(rasterizer.shape(text).glyphs().length * 4, rasterizer.shape(text).glyphs().length * 6);
        int[] colors = new int[4];
        return visit(text, x, y, scale, (glyph, x0, y0, x1, y1, u0, v0, u1, v1) -> {
            if (quadGradient != null) {
                quadGradient.colors(glyph.codePointIndex(), glyph.codePoint(), x0, y0, x1, y1, colors);
            } else if (gradient != null) {
                gradient.colors(glyph.codePointIndex(), glyph.codePoint(), x + glyph.x() * rasterizer.emScale() * scale, colors);
                colors[2] = colors[3] = colors[1];
                colors[1] = colors[0];
            } else {
                java.util.Arrays.fill(colors, color);
            }
            int a = vertex(mesh, x0, y0, u0, v0, colors[0]);
            int b = vertex(mesh, x0, y1, u0, v1, colors[1]);
            int c = vertex(mesh, x1, y1, u1, v1, colors[2]);
            int d = vertex(mesh, x1, y0, u1, v0, colors[3]);
            mesh.quad(a, b, c, d);
        });
    }

    private static int vertex(MeshBuilder mesh, double x, double y, float u, float v, int argb) {
        return mesh.vec2(x, y).raw2(u, v).color((argb >>> 16) & 255, (argb >>> 8) & 255,
                argb & 255, (argb >>> 24) & 255).next();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (texture != null) {
            TextRenderSystem.glyphAtlases().unregisterPage(pageId);
            texture.close();
        }
        if (pixels != null) MemoryUtil.memFree(pixels);
        cache = null;
        pixels = null;
    }

    @FunctionalInterface private interface PositionedConsumer {
        void accept(OpenTypeShaper.PositionedGlyph glyph, double x0, double y0, double x1, double y1,
                    float u0, float v0, float u1, float v1);
    }
}
