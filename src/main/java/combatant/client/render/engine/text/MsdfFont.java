/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.system.MemoryUtil;
import combatant.client.render.engine.Texture;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.util.logging.DebugLog;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import java.nio.IntBuffer;
import org.lwjgl.system.MemoryStack;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MsdfFont implements GlyphFont {
    private static final int FONT_SIZES = 5;
    private static final float BASE_HEIGHT = 27f;
    private static final int WIDTH_CACHE_SLOTS = 1024;
    private static volatile String lastError = "";
    private final MsdfAtlas atlas;
    private final float pixelScale;
    private final int height;
    /**
     * Direct-mapped width cache. The former access-ordered LinkedHashMap allocated a key record per lookup
     * and relinked its entry on every hit; text is measured many times per frame, so lookups here allocate
     * nothing and a colliding string simply replaces the slot. Entries are immutable, so a racing reader
     * can never observe a half-written value.
     */
    private final WidthEntry[] widthCache = new WidthEntry[WIDTH_CACHE_SLOTS];
    private boolean closed;

    private MsdfFont(MsdfAtlas atlas, float targetHeight) {
        this.atlas = atlas;
        this.height = Math.max(1, Math.round(targetHeight));
        this.pixelScale = targetHeight / atlas.lineHeight;
        atlas.retain();
    }

    public static GlyphFont[] tryCreate(FontFace face) {
        if (!(face instanceof BuiltinFontFace)) return null;
        MsdfAtlas atlas = MsdfAtlas.load(face);
        if (atlas == null) return null;

        GlyphFont[] fonts = new GlyphFont[FONT_SIZES];
        for (int i = 0; i < FONT_SIZES; i++) {
            float target = BASE_HEIGHT * ((i * 0.5f) + 1f);
            fonts[i] = new MsdfFont(atlas, target);
        }
        return fonts;
    }

    public static String getLastError() {
        return lastError;
    }

    private static void setLastError(String reason) {
        lastError = reason != null ? reason : "";
    }

    private static void warnBadAtlas(Identifier resource, String issue, String details) {
        String resourceName = resource != null ? resource.toString() : "<unknown>";
        String issueName = issue != null ? issue : "invalid";
        DebugLog.warnOnce(
                "bad-msdf-atlas:" + resourceName + ':' + issueName,
                "[MSDF] Bad atlas %s: %s",
                resourceName,
                details
        );
    }

    @Override
    public Texture getTexture() {
        return atlas.texture;
    }

    @Override
    public boolean isReady() {
        return atlas.texture != null && atlas.texture.isReady();
    }

    private OpenTypeShaper.ShapedRun shaped(String text) {
        return atlas.shape(text);
    }

    @Override
    public boolean canShape(String text) {
        return text != null && shaped(text) != null;
    }

    @Override
    public double getWidth(String string, int length) {
        if (string == null || string.isEmpty() || length <= 0) return 0.0;
        int safeLength = Math.min(length, string.length());
        if (safeLength > 0 && safeLength < string.length() && Character.isHighSurrogate(string.charAt(safeLength - 1))
                && Character.isLowSurrogate(string.charAt(safeLength))) safeLength--;
        int hash = string.hashCode();
        int slot = ((hash ^ (hash >>> 16)) + safeLength * 0x9E3779B1) & (WIDTH_CACHE_SLOTS - 1);
        WidthEntry cached = widthCache[slot];
        if (cached != null && cached.length == safeLength && (cached.text == string || cached.text.equals(string))) {
            return cached.width;
        }

        // Use GSUB/GPOS advances only if every positioned glyph exists in this
        // atlas. Otherwise retain the old Unicode path without missing quads.
        OpenTypeShaper.ShapedRun run = shaped(string.substring(0, safeLength));
        if (run != null) {
            double width = run.advance() * pixelScale;
            widthCache[slot] = new WidthEntry(string, safeLength, width);
            return width;
        }
        double width = 0;
        int previous = -1;
        for (int i = 0; i < safeLength; ) {
            int cp = string.codePointAt(i);
            Glyph g = atlas.glyphForCodePoint(cp);
            int resolved = g != null ? cp : -1;
            if (g == null) g = atlas.fallback;
            if (g != null) {
                width += (g.advance + atlas.kerning(previous, resolved)) * pixelScale;
            }
            previous = resolved;
            i += Character.charCount(cp);
        }
        widthCache[slot] = new WidthEntry(string, safeLength, width);
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public double render(MeshBuilder mesh, String string, double x, double y, RenderColor color, double scale) {
        OpenTypeShaper.ShapedRun run = shaped(string);
        if (run != null) return renderShaped(mesh, run, x, y, color, scale, null, null);

        double baseline = y + atlas.ascender * pixelScale * scale;

        int length = string.length();
        mesh.ensureCapacity(length * 4, length * 6);
        int previous = -1;

        for (int i = 0; i < length; ) {
            int cp = string.codePointAt(i);
            Glyph g = atlas.glyphForCodePoint(cp);
            int resolved = g != null ? cp : -1;
            if (g == null) g = atlas.fallback;
            if (g == null) {
                previous = -1;
                i += Character.charCount(cp);
                continue;
            }

            x += atlas.kerning(previous, resolved) * pixelScale * scale;
            previous = resolved;
            if (g.visible) {
                double scalePx = pixelScale * scale;
                double x0 = x + g.left * scalePx;
                double x1 = x + g.right * scalePx;
                double y0 = baseline + g.top * scalePx;
                double y1 = baseline + g.bottom * scalePx;

                int i1 = mesh.vec2(x0, y0).raw2(g.u0, g.v0).color(color.r, color.g, color.b, color.a).next();
                int i2 = mesh.vec2(x0, y1).raw2(g.u0, g.v1).color(color.r, color.g, color.b, color.a).next();
                int i3 = mesh.vec2(x1, y1).raw2(g.u1, g.v1).color(color.r, color.g, color.b, color.a).next();
                int i4 = mesh.vec2(x1, y0).raw2(g.u1, g.v0).color(color.r, color.g, color.b, color.a).next();

                mesh.quad(i1, i2, i3, i4);
            }

            x += g.advance * pixelScale * scale;
            i += Character.charCount(cp);
        }

        return x;
    }

    @Override
    public double emitGlyphs(String string, double x, double y, double scale, GlyphConsumer consumer) {
        OpenTypeShaper.ShapedRun run = shaped(string);
        if (run != null) return emitShaped(run, x, y, scale, consumer);

        double baseline = y + atlas.ascender * pixelScale * scale;

        int length = string.length();
        int previous = -1;
        for (int i = 0; i < length; ) {
            int cp = string.codePointAt(i);
            Glyph g = atlas.glyphForCodePoint(cp);
            int resolved = g != null ? cp : -1;
            if (g == null) g = atlas.fallback;
            if (g == null) {
                previous = -1;
                i += Character.charCount(cp);
                continue;
            }

            x += atlas.kerning(previous, resolved) * pixelScale * scale;
            previous = resolved;
            if (g.visible) {
                double scalePx = pixelScale * scale;
                double x0 = x + g.left * scalePx;
                double x1 = x + g.right * scalePx;
                double y0 = baseline + g.top * scalePx;
                double y1 = baseline + g.bottom * scalePx;

                consumer.accept(x0, y0, x1, y1, g.u0, g.v0, g.u1, g.v1);
            }

            x += g.advance * pixelScale * scale;
            i += Character.charCount(cp);
        }

        return x;
    }

    private double emitShaped(OpenTypeShaper.ShapedRun run, double x, double y,
                              double scale, GlyphConsumer consumer) {
        double factor = pixelScale * scale;
        double baseline = y + atlas.ascender * factor;
        for (OpenTypeShaper.PositionedGlyph slot : run.glyphs()) {
            Glyph g = atlas.glyphIds.get(slot.glyphId());
            if (g == null || !g.visible) continue;
            double gx = x + slot.x() * factor;
            double gy = baseline + slot.y() * factor;
            consumer.accept(gx + g.left * factor, gy + g.top * factor,
                    gx + g.right * factor, gy + g.bottom * factor,
                    g.u0, g.v0, g.u1, g.v1);
        }
        return x + run.advance() * factor;
    }

    private double renderShaped(MeshBuilder mesh, OpenTypeShaper.ShapedRun run,
                                double x, double y, RenderColor color, double scale,
                                Font.GlyphGradient gradient, Font.GlyphQuadGradient quadGradient) {
        mesh.ensureCapacity(run.glyphs().length * 4, run.glyphs().length * 6);
        double factor = pixelScale * scale;
        double baseline = y + atlas.ascender * factor;
        int[] pair = gradient == null ? null : new int[2];
        int[] quad = quadGradient == null ? null : new int[4];
        for (OpenTypeShaper.PositionedGlyph slot : run.glyphs()) {
            Glyph g = atlas.glyphIds.get(slot.glyphId());
            if (g != null && g.visible) {
                double gx = x + slot.x() * factor;
                double gy = baseline + slot.y() * factor;
                double x0 = gx + g.left * factor;
                double x1 = gx + g.right * factor;
                double y0 = gy + g.top * factor;
                double y1 = gy + g.bottom * factor;
                int tl, bl, br, tr;
                if (quad != null) {
                    quadGradient.colors(slot.codePointIndex(), slot.codePoint(), x0, y0, x1, y1, quad);
                    tl = quad[0]; bl = quad[1]; br = quad[2]; tr = quad[3];
                } else if (pair != null) {
                    gradient.colors(slot.codePointIndex(), slot.codePoint(), gx, pair);
                    tl = bl = pair[0]; br = tr = pair[1];
                } else {
                    tl = bl = br = tr = ((color.a & 255) << 24) | ((color.r & 255) << 16) | ((color.g & 255) << 8) | (color.b & 255);
                }
                int i1 = mesh.vec2(x0, y0).raw2(g.u0, g.v0).color(red(tl), green(tl), blue(tl), alpha(tl)).next();
                int i2 = mesh.vec2(x0, y1).raw2(g.u0, g.v1).color(red(bl), green(bl), blue(bl), alpha(bl)).next();
                int i3 = mesh.vec2(x1, y1).raw2(g.u1, g.v1).color(red(br), green(br), blue(br), alpha(br)).next();
                int i4 = mesh.vec2(x1, y0).raw2(g.u1, g.v0).color(red(tr), green(tr), blue(tr), alpha(tr)).next();
                mesh.quad(i1, i2, i3, i4);
            }
        }
        return x + run.advance() * factor;
    }

    @Override
    public boolean hasGlyph(int codePoint) {
        return atlas.glyphForCodePoint(codePoint) != null
                || (atlas.shaper != null && atlas.shaper.canDisplay(codePoint) && atlas.nativeFont != null);
    }

    @Override
    public double getAdvance(int codePoint) {
        Glyph g = atlas.glyphForCodePoint(codePoint);
        return g != null ? g.advance * pixelScale : 0.0;
    }

    @Override
    public double renderGradient(MeshBuilder mesh, String string, double x, double y, double scale, Font.GlyphGradient gradient) {
        OpenTypeShaper.ShapedRun run = shaped(string);
        if (run != null) return renderShaped(mesh, run, x, y, null, scale, gradient, null);

        double baseline = y + atlas.ascender * pixelScale * scale;

        int length = string.length();
        mesh.ensureCapacity(length * 4, length * 6);

        int[] colors = new int[2];
        int glyphIndex = 0;
        int previous = -1;
        for (int i = 0; i < length; ) {
            int cp = string.codePointAt(i);
            Glyph g = atlas.glyphForCodePoint(cp);
            int resolved = g != null ? cp : -1;
            if (g == null) g = atlas.fallback;
            if (g == null) {
                previous = -1;
                i += Character.charCount(cp);
                glyphIndex++;
                continue;
            }

            x += atlas.kerning(previous, resolved) * pixelScale * scale;
            previous = resolved;
            gradient.colors(glyphIndex, cp, x, colors);
            int leftArgb = colors[0];
            int rightArgb = colors[1];

            int la = (leftArgb >>> 24) & 0xFF;
            int lr = (leftArgb >>> 16) & 0xFF;
            int lg = (leftArgb >>> 8) & 0xFF;
            int lb = leftArgb & 0xFF;

            int ra = (rightArgb >>> 24) & 0xFF;
            int rr = (rightArgb >>> 16) & 0xFF;
            int rg = (rightArgb >>> 8) & 0xFF;
            int rb = rightArgb & 0xFF;

            if (g.visible) {
                double scalePx = pixelScale * scale;
                double x0 = x + g.left * scalePx;
                double x1 = x + g.right * scalePx;
                double y0 = baseline + g.top * scalePx;
                double y1 = baseline + g.bottom * scalePx;

                int i1 = mesh.vec2(x0, y0).raw2(g.u0, g.v0).color(lr, lg, lb, la).next();
                int i2 = mesh.vec2(x0, y1).raw2(g.u0, g.v1).color(lr, lg, lb, la).next();
                int i3 = mesh.vec2(x1, y1).raw2(g.u1, g.v1).color(rr, rg, rb, ra).next();
                int i4 = mesh.vec2(x1, y0).raw2(g.u1, g.v0).color(rr, rg, rb, ra).next();

                mesh.quad(i1, i2, i3, i4);
            }

            x += g.advance * pixelScale * scale;
            i += Character.charCount(cp);
            glyphIndex++;
        }

        return x;
    }

    @Override
    public double renderQuadGradient(MeshBuilder mesh, String string, double x, double y, double scale, Font.GlyphQuadGradient gradient) {
        OpenTypeShaper.ShapedRun run = shaped(string);
        if (run != null) return renderShaped(mesh, run, x, y, null, scale, null, gradient);

        double baseline = y + atlas.ascender * pixelScale * scale;

        int length = string.length();
        mesh.ensureCapacity(length * 4, length * 6);

        int[] colors = new int[4];
        int glyphIndex = 0;
        int previous = -1;
        for (int i = 0; i < length; ) {
            int cp = string.codePointAt(i);
            Glyph g = atlas.glyphForCodePoint(cp);
            int resolved = g != null ? cp : -1;
            if (g == null) g = atlas.fallback;
            if (g == null) {
                previous = -1;
                i += Character.charCount(cp);
                glyphIndex++;
                continue;
            }

            x += atlas.kerning(previous, resolved) * pixelScale * scale;
            previous = resolved;
            if (g.visible) {
                double scalePx = pixelScale * scale;
                double x0 = x + g.left * scalePx;
                double x1 = x + g.right * scalePx;
                double y0 = baseline + g.top * scalePx;
                double y1 = baseline + g.bottom * scalePx;

                gradient.colors(glyphIndex, cp, x0, y0, x1, y1, colors);
                int topLeftArgb = colors[0];
                int bottomLeftArgb = colors[1];
                int bottomRightArgb = colors[2];
                int topRightArgb = colors[3];

                int i1 = mesh.vec2(x0, y0).raw2(g.u0, g.v0).color(red(topLeftArgb), green(topLeftArgb), blue(topLeftArgb), alpha(topLeftArgb)).next();
                int i2 = mesh.vec2(x0, y1).raw2(g.u0, g.v1).color(red(bottomLeftArgb), green(bottomLeftArgb), blue(bottomLeftArgb), alpha(bottomLeftArgb)).next();
                int i3 = mesh.vec2(x1, y1).raw2(g.u1, g.v1).color(red(bottomRightArgb), green(bottomRightArgb), blue(bottomRightArgb), alpha(bottomRightArgb)).next();
                int i4 = mesh.vec2(x1, y0).raw2(g.u1, g.v0).color(red(topRightArgb), green(topRightArgb), blue(topRightArgb), alpha(topRightArgb)).next();

                mesh.quad(i1, i2, i3, i4);
            }

            x += g.advance * pixelScale * scale;
            i += Character.charCount(cp);
            glyphIndex++;
        }

        return x;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        java.util.Arrays.fill(widthCache, null);
        atlas.release();
    }

    @Override
    public boolean isMsdf() {
        return true;
    }

    @Override
    public float getPxRange() {
        return atlas.distanceRange;
    }

    @Override
    public int getAtlasWidth() {
        return atlas.width;
    }

    @Override
    public int getAtlasHeight() {
        return atlas.height;
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static int red(int argb) {
        return (argb >>> 16) & 0xFF;
    }

    private static int green(int argb) {
        return (argb >>> 8) & 0xFF;
    }

    private static int blue(int argb) {
        return argb & 0xFF;
    }

    private record WidthEntry(String text, int length, double width) {
    }

    private static final class MsdfAtlas {
        private final Texture texture;
        private final float distanceRange;
        private final int width;
        private final int height;
        private final float lineHeight;
        private final float ascender;
        private final Int2ObjectOpenHashMap<Glyph> glyphs;
        private final Int2ObjectOpenHashMap<Glyph> glyphIds;
        private final OpenTypeShaper shaper;
        private final Long2FloatOpenHashMap kerningPairs;
        private final Glyph fallback;
        private final STBTTFontinfo nativeFont;
        private final ByteBuffer nativeFontData;
        private final ByteBuffer uploadPixels;
        private final float sdfEmScale;
        private final float sdfAtlasSize;
        private final GlyphAtlasAllocator extraGlyphs;
        private final int dynamicStartY;
        private final it.unimi.dsi.fastutil.ints.IntOpenHashSet unpackableIds = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
        private int refs;
        private boolean released;
        private boolean staleWarned;
        private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Combatant/MsdfFont");

        private MsdfAtlas(Texture texture,
                          float distanceRange,
                          int width,
                          int height,
                          float lineHeight,
                          float ascender,
                          Int2ObjectOpenHashMap<Glyph> glyphs,
                          Int2ObjectOpenHashMap<Glyph> glyphIds,
                          OpenTypeShaper shaper,
                          Long2FloatOpenHashMap kerningPairs,
                          Glyph fallback,
                          STBTTFontinfo nativeFont, ByteBuffer nativeFontData,
                          ByteBuffer uploadPixels, float sdfEmScale, float sdfAtlasSize,
                          int dynamicStartY) {
            this.texture = texture;
            this.distanceRange = distanceRange;
            this.width = width;
            this.height = height;
            this.lineHeight = lineHeight;
            this.ascender = ascender;
            this.glyphs = glyphs;
            this.glyphIds = glyphIds;
            this.shaper = shaper;
            this.kerningPairs = kerningPairs;
            this.fallback = fallback;
            this.nativeFont = nativeFont;
            this.nativeFontData = nativeFontData;
            this.uploadPixels = uploadPixels;
            this.sdfEmScale = sdfEmScale;
            this.sdfAtlasSize = sdfAtlasSize;
            this.dynamicStartY = dynamicStartY;
            this.extraGlyphs = uploadPixels != null
                    ? new GlyphAtlasAllocator(width, height, dynamicStartY) : null;
        }

        private static long pairKey(int left, int right) {
            return ((long) left << 32) | (right & 0xFFFFFFFFL);
        }

        private float kerning(int previous, int current) {
            return previous < 0 || current < 0 ? 0f : kerningPairs.get(pairKey(previous, current));
        }

        private Glyph glyphForCodePoint(int cp) {
            Glyph glyph = glyphs.get(cp);
            if (glyph != null || shaper == null) return glyph;
            int id = shaper.glyphId(cp);
            return id < 0 ? null : glyphIds.get(id);
        }

        /** Materialize unencoded GSUB/GPOS outputs as single-channel distance fields. */
        private OpenTypeShaper.ShapedRun shape(String text) {
            if (shaper == null) return null;
            OpenTypeShaper.ShapedRun run = shaper.shape(text);
            // Measure uses OpenType metrics without requiring a render context.
            // Upload new glyphs only from the actual render thread, and never into a released atlas: a font still
            // held after a resource reload would otherwise write into freed native pixels and corrupt the heap.
            if (released) {
                if (RenderSystem.isOnRenderThread()) warnStaleUse();
                return run;
            }
            if (!RenderSystem.isOnRenderThread()) return run;
            boolean complete = true;
            for (OpenTypeShaper.PositionedGlyph slot : run.glyphs()) {
                int id = slot.glyphId();
                if (id <= 0 || unpackableIds.contains(id)) {
                    complete = false;
                    continue;
                }
                if (!glyphIds.containsKey(id) && !packGlyphById(id)) {
                    complete = false;
                }
            }
            // Empty glyphs enter the cache without allocating atlas pixels.
            GlyphAtlasAllocator.Region region = extraGlyphs != null ? extraGlyphs.takeDirty() : null;
            if (region != null) {
                texture.uploadAtlasRegion(uploadPixels, 4, width, dynamicStartY,
                        region.x(), region.y(), region.width(), region.height());
            }
            return complete ? run : null;
        }

        /** Logged once per atlas: a font kept past a resource reload asked a released atlas for new glyphs. */
        private void warnStaleUse() {
            if (staleWarned) return;
            staleWarned = true;
            LOGGER.warn("MSDF atlas used after release (font kept past a resource reload); new glyph upload skipped");
        }

        private boolean packGlyphById(int id) {
            if (released) {
                warnStaleUse();
                return false;
            }
            if (nativeFont == null || uploadPixels == null) return false;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer wOut = stack.mallocInt(1);
                IntBuffer hOut = stack.mallocInt(1);
                IntBuffer xOut = stack.mallocInt(1);
                IntBuffer yOut = stack.mallocInt(1);
                int padding = Math.max(2, (int) Math.ceil(distanceRange) + 1);
                // The MSDF shader uses median(R,G,B). Replicating a genuine SDF
                // in RGB yields the same signed-distance coverage for glyphs not
                // present in the pre-generated RGB MSDF asset.
                ByteBuffer sdf = STBTruetype.stbtt_GetGlyphSDF(nativeFont, sdfEmScale, id,
                        padding, (byte) 128, 128.0f / distanceRange,
                        wOut, hOut, xOut, yOut);
                int w = wOut.get(0), h = hOut.get(0);
                try {
                    if (sdf == null || w <= 0 || h <= 0) {
                        // Space / zero-area glyph: valid advance without geometry.
                        glyphIds.put(id, new Glyph(0, 0, 0, 0, 0, 0, 0, 0, 0, false));
                        return true;
                    }
                    GlyphAtlasAllocator.Rect rect = extraGlyphs.place(w, h);
                    if (rect == null) {
                        unpackableIds.add(id);
                        return false;
                    }
                    int x = rect.x(), y = rect.y();
                    for (int j = 0; j < h; j++) {
                        for (int i = 0; i < w; i++) {
                            byte coverage = sdf.get(j * w + i);
                            int out = ((y + j - dynamicStartY) * width + x + i) * 4;
                            uploadPixels.put(out, coverage);
                            uploadPixels.put(out + 1, coverage);
                            uploadPixels.put(out + 2, coverage);
                            uploadPixels.put(out + 3, (byte) 0xff);
                        }
                    }
                    float x0 = (float) xOut.get(0) / sdfAtlasSize;
                    float y0 = (float) yOut.get(0) / sdfAtlasSize;
                    float x1 = (float) (xOut.get(0) + w) / sdfAtlasSize;
                    float y1 = (float) (yOut.get(0) + h) / sdfAtlasSize;
                    // Shaper's positions already supply the advance. The SDF bitmap
                    // includes the padding in its plane bounds.
                    glyphIds.put(id, new Glyph(0, x0, y1, x1, y0,
                            (float) x / width, (float) y / height,
                            (float) (x + w) / width, (float) (y + h) / height, true));
                    return true;
                } finally {
                    if (sdf != null) STBTruetype.stbtt_FreeSDF(sdf);
                }
            }
        }

        private static MsdfAtlas load(FontFace face) {
            FontInfo info = face.info;
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                setLastError("mc null");
                return null;
            }
            ResourceManager rm = mc.getResourceManager();

            Identifier jsonId = FontUtils.msdfResource(info, "json");
            Identifier pngId = FontUtils.msdfResource(info, "png");
            if (jsonId == null || pngId == null) {
                setLastError("msdf ids missing");
                return null;
            }

            var jsonResource = rm.getResource(jsonId);
            if (jsonResource.isEmpty()) {
                // Most bundled fonts intentionally have no MSDF variant. Absence is a normal
                // bitmap fallback; only a present but broken MSDF bundle deserves a warning.
                setLastError("msdf json missing: " + jsonId);
                return null;
            }

            JsonObject root;
            try (InputStream in = jsonResource.get().open()) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                root = JsonParser.parseString(json).getAsJsonObject();
            } catch (Exception exception) {
                setLastError("msdf json missing/invalid: " + jsonId);
                warnBadAtlas(jsonId, "json", "metadata JSON cannot be parsed (" + exception.getClass().getSimpleName() + ")");
                return null;
            }

            BufferedImage image;
            var pngResource = rm.getResource(pngId);
            if (pngResource.isEmpty()) {
                setLastError("msdf png missing: " + pngId);
                warnBadAtlas(jsonId, "png-missing", "metadata exists but texture " + pngId + " is missing");
                return null;
            }
            try (InputStream in = pngResource.get().open()) {
                image = ImageIO.read(in);
                if (image == null) {
                    setLastError("msdf png invalid: " + pngId);
                    warnBadAtlas(jsonId, "png-invalid", "texture " + pngId + " is not a readable image");
                    return null;
                }
            } catch (Exception exception) {
                setLastError("msdf png missing/invalid: " + pngId);
                warnBadAtlas(jsonId, "png-invalid", "texture " + pngId + " cannot be read ("
                        + exception.getClass().getSimpleName() + ")");
                return null;
            }

            JsonObject atlasObj = getObject(root, "atlas");
            float distanceRange = getFloat(atlasObj, "distanceRange", 4f);
            int atlasWidth = getInt(atlasObj, "width", image.getWidth());
            int atlasHeight = getInt(atlasObj, "height", image.getHeight());
            if (atlasWidth <= 0) atlasWidth = image.getWidth();
            if (atlasHeight <= 0) atlasHeight = image.getHeight();
            if (atlasWidth != image.getWidth() || atlasHeight != image.getHeight()) {
                setLastError("msdf atlas/image size mismatch: " + jsonId);
                warnBadAtlas(jsonId, "dimensions", "metadata size " + atlasWidth + 'x' + atlasHeight
                        + " does not match texture size " + image.getWidth() + 'x' + image.getHeight());
                return null;
            }
            if (!Float.isFinite(distanceRange) || distanceRange <= 0f) {
                setLastError("msdf distanceRange invalid: " + jsonId);
                warnBadAtlas(jsonId, "distance-range", "distanceRange must be finite and greater than zero");
                return null;
            }
            String yOrigin = getString(atlasObj, "yOrigin", "bottom");
            boolean yOriginBottom = !"top".equalsIgnoreCase(yOrigin);

            JsonObject metricsObj = getObject(root, "metrics");
            float emSize = getFloat(metricsObj, "emSize", 1f);
            float lineHeight = getFloat(metricsObj, "lineHeight", emSize);
            float ascender = getFloat(metricsObj, "ascender", lineHeight);
            if (!Float.isFinite(lineHeight) || lineHeight <= 0f || !Float.isFinite(ascender)) {
                setLastError("msdf metrics invalid: " + jsonId);
                warnBadAtlas(jsonId, "metrics", "lineHeight and ascender must be finite, with lineHeight greater than zero");
                return null;
            }
            if (ascender < 0f) ascender = -ascender;

            OpenTypeShaper shaper = null;
            ByteBuffer nativeFontData = null;
            STBTTFontinfo nativeFont = null;
            float sdfEmScale = 0f;
            float sdfAtlasSize = getFloat(atlasObj, "size", 48f);
            int extraRows = 0;
            if (!face.isAtlasOnly()) {
                try (InputStream source = face.toStream()) {
                    byte[] bytes = source.readAllBytes();
                    shaper = OpenTypeShaper.load(new ByteArrayInputStream(bytes));
                    if (shaper != null && atlasHeight < 4096) {
                        ByteBuffer data = MemoryUtil.memAlloc(bytes.length);
                        data.put(bytes).flip();
                        STBTTFontinfo info2 = STBTTFontinfo.create();
                        if (STBTruetype.stbtt_InitFont(info2, data)) {
                            nativeFontData = data;
                            nativeFont = info2;
                            sdfEmScale = STBTruetype.stbtt_ScaleForMappingEmToPixels(info2, sdfAtlasSize);
                            extraRows = Math.min(768, 4096 - atlasHeight);
                        } else {
                            info2.free();
                            MemoryUtil.memFree(data);
                        }
                    }
                } catch (Exception ignored) {
                    // Atlas-only or inaccessible sources continue using the MSDF asset.
                }
            }
            int outputHeight = atlasHeight + extraRows;
            Int2ObjectOpenHashMap<Glyph> glyphs = new Int2ObjectOpenHashMap<>();
            Int2ObjectOpenHashMap<Glyph> glyphIds = new Int2ObjectOpenHashMap<>();
            JsonArray glyphArray = getArray(root, "glyphs");
            int missingUnicode = 0;
            int duplicateUnicode = 0;
            if (glyphArray != null) {
                float invW = 1f / atlasWidth;
                float invH = 1f / outputHeight;

                for (JsonElement el : glyphArray) {
                    if (!el.isJsonObject()) {
                        missingUnicode++;
                        continue;
                    }
                    JsonObject g = el.getAsJsonObject();

                    int codePoint = readUnicode(g.get("unicode"));
                    int index = getInt(g, "index", -1);
                    if (!isUnicodeScalar(codePoint) && index < 0) {
                        missingUnicode++;
                        continue;
                    }

                    float advance = getFloat(g, "advance", 0f);
                    Bounds plane = readBounds(getObject(g, "planeBounds"));
                    Bounds atlas = readBounds(getObject(g, "atlasBounds"));

                    Glyph glyph = Glyph.from(advance, plane, atlas, yOriginBottom, atlasWidth, atlasHeight, invW, invH);
                    if (isUnicodeScalar(codePoint)) {
                        if (glyphs.put(codePoint, glyph) != null) duplicateUnicode++;
                        // Unicode atlases may also be shaped when all resulting IDs
                        // happen to be present; never assume all ligatures are packed.
                        if (shaper != null) {
                            int id = shaper.glyphId(codePoint);
                            if (id >= 0) glyphIds.putIfAbsent(id, glyph);
                        }
                    }
                    if (index >= 0) glyphIds.put(index, glyph);
                }
            }

            if (missingUnicode > 0) {
                warnBadAtlas(jsonId, "unicode", "ignored " + missingUnicode
                        + " glyph entries without a valid Unicode or glyph-index mapping");
            }
            if (duplicateUnicode > 0) {
                warnBadAtlas(jsonId, "unicode-duplicate", "contains " + duplicateUnicode
                        + " duplicate Unicode mappings");
            }

            if (glyphs.isEmpty() && (shaper == null || glyphIds.isEmpty())) {
                if (nativeFont != null) nativeFont.free();
                if (nativeFontData != null) MemoryUtil.memFree(nativeFontData);
                setLastError("msdf glyphs empty/unresolvable: " + jsonId);
                warnBadAtlas(jsonId, "glyphs-empty", glyphArray == null
                        ? "glyphs array is missing"
                        : "no loadable Unicode mappings or resolvable glyph IDs were found");
                return null;
            }

            // msdf-atlas-gen stores pair advances in em units, just like glyph advances.
            // Old atlases without a kerning array remain valid; they simply retain legacy spacing.
            Long2FloatOpenHashMap kerningPairs = new Long2FloatOpenHashMap();
            JsonArray kerningArray = getArray(root, "kerning");
            if (kerningArray != null) {
                for (JsonElement element : kerningArray) {
                    if (!element.isJsonObject()) continue;
                    JsonObject entry = element.getAsJsonObject();
                    int left = readUnicode(entry.get("unicode1"));
                    int right = readUnicode(entry.get("unicode2"));
                    if (!isUnicodeScalar(left) || !isUnicodeScalar(right)
                            || !glyphs.containsKey(left) || !glyphs.containsKey(right)) continue;
                    float advance = getFloat(entry, "advance", 0f);
                    if (Float.isFinite(advance) && advance != 0f) {
                        kerningPairs.put(pairKey(left, right), advance);
                    }
                }
            }

            Glyph fallback = glyphs.get('?');
            if (fallback == null) fallback = glyphs.get(32);
            if (fallback == null && shaper != null) fallback = glyphIds.get(shaper.glyphId('?'));

            ByteBuffer[] uploadBacking = new ByteBuffer[1];
            Texture texture = uploadTexture(image, extraRows, uploadBacking);
            if (texture == null) {
                if (nativeFont != null) nativeFont.free();
                if (nativeFontData != null) MemoryUtil.memFree(nativeFontData);
                setLastError("msdf texture upload failed: " + pngId);
                warnBadAtlas(jsonId, "texture-upload", "texture " + pngId + " could not be uploaded to the GPU");
                return null;
            }

            setLastError("");
            TextRenderSystem.glyphAtlases().registerPage("msdf:" + jsonId, texture, atlasWidth, outputHeight, true);
            TextRenderSystem.glyphAtlases().onDirtyRectUpload(glyphs.size(), atlasWidth * outputHeight * 4);
            return new MsdfAtlas(texture, distanceRange, atlasWidth, outputHeight, lineHeight, ascender,
                    glyphs, glyphIds, shaper, kerningPairs, fallback,
                    nativeFont, nativeFontData, uploadBacking[0], sdfEmScale, sdfAtlasSize, atlasHeight);
        }

        private static Texture uploadTexture(BufferedImage image, int extraRows, ByteBuffer[] backing) {
            int w = image.getWidth();
            int h = image.getHeight();

            ByteBuffer buffer = MemoryUtil.memCalloc(w * (h + extraRows) * 4);
            try {
                if (!copyInterleavedBytes(image, buffer)) {
                    // Generic path: getRGB converts every pixel through the ColorModel, which
                    // showed up as seconds of reload time for large atlases.
                    int[] pixels = new int[w * h];
                    image.getRGB(0, 0, w, h, pixels, 0, w);
                    for (int argb : pixels) {
                        buffer.put((byte) ((argb >> 16) & 0xFF));
                        buffer.put((byte) ((argb >> 8) & 0xFF));
                        buffer.put((byte) (argb & 0xFF));
                        buffer.put((byte) ((argb >> 24) & 0xFF));
                    }
                }
                buffer.position(0);
                buffer.limit(buffer.capacity());

                Texture texture = new Texture(w, h + extraRows, GpuFormat.RGBA8_UNORM, FilterMode.LINEAR, FilterMode.LINEAR);
                texture.upload(buffer.duplicate());
                // The initial atlas is already on the GPU. Only newly appended
                // rows need CPU backing; keep no copy of the original PNG pixels.
                backing[0] = extraRows > 0 ? MemoryUtil.memCalloc(w * extraRows * 4) : null;
                return texture;
            } catch (Exception ignored) {
                if (backing[0] != null) MemoryUtil.memFree(backing[0]);
                backing[0] = null;
                return null;
            } finally {
                MemoryUtil.memFree(buffer);
            }
        }

        /**
         * Fast RGBA copy for the byte layouts ImageIO produces for PNGs. Returns false (and writes
         * nothing) for any other layout so the caller falls back to getRGB.
         */
        private static boolean copyInterleavedBytes(BufferedImage image, ByteBuffer out) {
            int type = image.getType();
            if (type != BufferedImage.TYPE_4BYTE_ABGR && type != BufferedImage.TYPE_3BYTE_BGR) return false;
            var raster = image.getRaster();
            if (raster.getSampleModelTranslateX() != 0 || raster.getSampleModelTranslateY() != 0) return false;
            if (!(raster.getDataBuffer() instanceof java.awt.image.DataBufferByte dataBuffer)
                    || dataBuffer.getNumBanks() != 1) return false;

            byte[] data = dataBuffer.getData();
            int pixels = image.getWidth() * image.getHeight();
            if (type == BufferedImage.TYPE_4BYTE_ABGR) {
                if (data.length < pixels * 4) return false;
                for (int i = 0, p = 0; i < pixels; i++, p += 4) {
                    // Stored A, B, G, R; uploaded R, G, B, A.
                    out.put(data[p + 3]).put(data[p + 2]).put(data[p + 1]).put(data[p]);
                }
            } else {
                if (data.length < pixels * 3) return false;
                for (int i = 0, p = 0; i < pixels; i++, p += 3) {
                    // Stored B, G, R with no alpha channel: opaque.
                    out.put(data[p + 2]).put(data[p + 1]).put(data[p]).put((byte) 0xFF);
                }
            }
            return true;
        }

        private static float getFloat(JsonObject obj, String key, float fallback) {
            if (obj == null || key == null || !obj.has(key)) return fallback;
            try {
                return obj.get(key).getAsFloat();
            } catch (Exception ignored) {
                return fallback;
            }
        }

        private static JsonObject getObject(JsonObject obj, String key) {
            if (obj == null || key == null || !obj.has(key)) return null;
            try {
                JsonElement element = obj.get(key);
                return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
            } catch (Exception ignored) {
                return null;
            }
        }

        private static JsonArray getArray(JsonObject obj, String key) {
            if (obj == null || key == null || !obj.has(key)) return null;
            try {
                JsonElement element = obj.get(key);
                return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
            } catch (Exception ignored) {
                return null;
            }
        }

        private static int getInt(JsonObject obj, String key, int fallback) {
            if (obj == null || key == null || !obj.has(key)) return fallback;
            try {
                return obj.get(key).getAsInt();
            } catch (Exception ignored) {
                return fallback;
            }
        }

        private static String getString(JsonObject obj, String key, String fallback) {
            if (obj == null || key == null || !obj.has(key)) return fallback;
            try {
                return obj.get(key).getAsString();
            } catch (Exception ignored) {
                return fallback;
            }
        }

        private static int readUnicode(JsonElement element) {
            if (element == null) return -1;
            try {
                if (element.isJsonPrimitive()) {
                    var prim = element.getAsJsonPrimitive();
                    if (prim.isNumber()) return prim.getAsInt();
                    if (prim.isString()) {
                        String str = prim.getAsString();
                        if (str.isEmpty()) return -1;
                        if (str.length() == 1) return str.codePointAt(0);
                        if (str.startsWith("0x") || str.startsWith("0X")) {
                            return Integer.parseInt(str.substring(2), 16);
                        }
                        return Integer.parseInt(str);
                    }
                }
            } catch (Exception ignored) {
            }
            return -1;
        }

        private static boolean isUnicodeScalar(int codePoint) {
            return Character.isValidCodePoint(codePoint)
                    && (codePoint < Character.MIN_SURROGATE || codePoint > Character.MAX_SURROGATE);
        }

        private static Bounds readBounds(JsonObject obj) {
            if (obj == null) return null;
            float left = getFloat(obj, "left", 0f);
            float right = getFloat(obj, "right", 0f);
            float top = getFloat(obj, "top", 0f);
            float bottom = getFloat(obj, "bottom", 0f);
            return new Bounds(left, bottom, right, top);
        }

        private void retain() {
            refs++;
        }

        private void release() {
            refs--;
            if (refs <= 0 && !released) {
                released = true;
                if (texture != null) texture.close();
                if (uploadPixels != null) MemoryUtil.memFree(uploadPixels);
                if (nativeFont != null) nativeFont.free();
                if (nativeFontData != null) MemoryUtil.memFree(nativeFontData);
            }
        }
    }

    private record Bounds(float left, float bottom, float right, float top) {
    }

    private record Glyph(float advance, float left, float bottom, float right, float top, float u0, float v0, float u1,
                         float v1, boolean visible) {

        private static Glyph from(float advance,
                                  Bounds plane,
                                  Bounds atlas,
                                  boolean yOriginBottom,
                                  int atlasWidth,
                                  int atlasHeight,
                                  float invW,
                                  float invH) {
            if (plane == null || atlas == null) {
                return new Glyph(advance, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, false);
            }

            float left = plane.left;
            float right = plane.right;
            float top = plane.top;
            float bottom = plane.bottom;

            // The renderer expects plane Y values in screen-space (down-positive).
            // MSDF atlases exported with bottom-origin store plane Y in up-positive space.
            if (yOriginBottom) {
                top = -top;
                bottom = -bottom;
            }

            float aLeft = atlas.left;
            float aRight = atlas.right;
            float aTop = atlas.top;
            float aBottom = atlas.bottom;

            if (yOriginBottom) {
                aTop = atlasHeight - aTop;
                aBottom = atlasHeight - aBottom;
            }

            float u0 = aLeft * invW;
            float u1 = aRight * invW;
            float v0 = aTop * invH;
            float v1 = aBottom * invH;

            return new Glyph(advance, left, bottom, right, top, u0, v0, u1, v1, true);
        }
    }
}
