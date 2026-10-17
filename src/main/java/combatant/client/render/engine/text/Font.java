/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on, adapted from, or implemented
 * with reference to Meteor Client
 * (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Licensed under the GNU General Public License v3.0.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.render.engine.text;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.stb.*;
import org.lwjgl.system.MemoryStack;
import combatant.client.render.engine.Texture;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.uniform.MeshBuilder;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class Font implements GlyphFont {
    private static final int SIZE = 4096;
    public final Texture texture;
    private final int height;
    private final float scale;
    private final float ascent;
    private final Int2ObjectOpenHashMap<CharData> charMap = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<CharData> glyphIds = new Int2ObjectOpenHashMap<>();
    private OpenTypeShaper shaper;
    private boolean shaperLoaded;
    private double shapedEmScale;
    // Keep independent native font data: CustomTextRenderer releases its source buffer after creating atlases.
    // STB's fontinfo contains pointers into that data, so kerning cannot retain the caller's buffer.
    private final ByteBuffer fontData;
    private final STBTTFontinfo fontInfo;
    private final Long2FloatOpenHashMap kerningCache = new Long2FloatOpenHashMap();
    private static final int MAX_KERNING_PAIRS = 4096;

    public Font(ByteBuffer buffer, int height) {
        this.height = height;

        fontData = MemoryUtil.memAlloc(buffer.remaining());
        fontData.put(buffer.duplicate()).flip();
        fontInfo = STBTTFontinfo.create();
        if (!STBTruetype.stbtt_InitFont(fontInfo, fontData)) {
            MemoryUtil.memFree(fontData);
            throw new IllegalArgumentException("Invalid TrueType font");
        }

        ByteBuffer bitmap = MemoryUtil.memAlloc(SIZE * SIZE);
        try {
        STBTTPackedchar.Buffer[] cdata = {
                STBTTPackedchar.create(95),    // 32..126
                STBTTPackedchar.create(96),    // 160..255
                STBTTPackedchar.create(128),   // 256..383
                STBTTPackedchar.create(144),   // 880..1023
                STBTTPackedchar.create(256),   // 1024..1279
                STBTTPackedchar.create(1),     // infinity
                STBTTPackedchar.create(64),    // icons

                STBTTPackedchar.create(112),   // 0x2000..0x206F
                STBTTPackedchar.create(48),    // 0x2070..0x209F
                STBTTPackedchar.create(48),    // 0x20A0..0x20CF
                STBTTPackedchar.create(80),    // 0x2100..0x214F
                STBTTPackedchar.create(64),    // 0x2150..0x218F
                STBTTPackedchar.create(112),   // 0x2190..0x21FF
                STBTTPackedchar.create(256),   // 0x2200..0x22FF
                STBTTPackedchar.create(256),   // 0x2300..0x23FF
                STBTTPackedchar.create(160),   // 0x2460..0x24FF
                STBTTPackedchar.create(128),   // 0x2500..0x257F
                STBTTPackedchar.create(32),    // 0x2580..0x259F
                STBTTPackedchar.create(96),    // 0x25A0..0x25FF
                STBTTPackedchar.create(256),   // 0x2600..0x26FF
                STBTTPackedchar.create(192),   // 0x2700..0x27BF
                STBTTPackedchar.create(128),   // 0x2900..0x297F
                STBTTPackedchar.create(256),   // 0x2B00..0x2BFF
                STBTTPackedchar.create(256),   // 0x1F300..0x1F3FF
                STBTTPackedchar.create(256),   // 0x1F500..0x1F5FF
                STBTTPackedchar.create(256),   // 0x1F600..0x1F6FF
                STBTTPackedchar.create(256),   // 0x1FA00..0x1FAFF
                STBTTPackedchar.create(7)      // 0xFB00..0xFB06: encoded Latin ligatures
        };

        STBTTPackContext packContext = STBTTPackContext.create();
        if (!STBTruetype.stbtt_PackBegin(packContext, bitmap, SIZE, SIZE, 0, 1)) {
            throw new IllegalStateException("Failed to start bitmap glyph packing");
        }
        // True subpixel oversampling rather than setting unused oversample fields in PackRange.
        // Only the smallest atlas uses it to avoid overflowing the fixed 4096² texture.
        if (height <= 27) {
            STBTruetype.stbtt_PackSetOversampling(packContext, 2, 1);
        }
        // Otherwise STB packs hundreds of missing codepoints as identical .notdef bitmaps,
        // wasting atlas space and preventing actual font fallback.
        STBTruetype.stbtt_PackSetSkipMissingCodepoints(packContext, true);

        STBTTPackRange.Buffer packRange = STBTTPackRange.create(cdata.length);
        packRange.put(STBTTPackRange.create().set(height, 32, null, 95, cdata[0], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 160, null, 96, cdata[1], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 256, null, 128, cdata[2], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 880, null, 144, cdata[3], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 1024, null, 256, cdata[4], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 8734, null, 1, cdata[5], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 0xEA00, null, 64, cdata[6], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 0x2000, null, 112, cdata[7], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 0x2070, null, 48, cdata[8], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 0x20A0, null, 48, cdata[9], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 0x2100, null, 80, cdata[10], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 0x2150, null, 64, cdata[11], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 0x2190, null, 112, cdata[12], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 0x2200, null, 256, cdata[13], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 0x2300, null, 256, cdata[14], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 0x2460, null, 160, cdata[15], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 0x2500, null, 128, cdata[16], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 0x2580, null, 32, cdata[17], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 0x25A0, null, 96, cdata[18], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 0x2600, null, 256, cdata[19], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 0x2700, null, 192, cdata[20], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 0x2900, null, 128, cdata[21], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 0x2B00, null, 256, cdata[22], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 0x1F300, null, 256, cdata[23], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 0x1F500, null, 256, cdata[24], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 0x1F600, null, 256, cdata[25], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 0x1FA00, null, 256, cdata[26], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 0xFB00, null, 7, cdata[27], (byte) 2, (byte) 2));
        packRange.flip();

        STBTruetype.stbtt_PackFontRanges(packContext, fontData, 0, packRange);
        STBTruetype.stbtt_PackEnd(packContext);

        texture = new Texture(SIZE, SIZE, GpuFormat.R8_UNORM, FilterMode.LINEAR, FilterMode.LINEAR);
        texture.upload(bitmap);
        TextRenderSystem.glyphAtlases().registerPage("bitmap:" + height + ":"
                + Integer.toHexString(System.identityHashCode(this)), texture, SIZE, SIZE, false);
        FontDebugStats.noteBitmapAtlasPage();
        scale = STBTruetype.stbtt_ScaleForPixelHeight(fontInfo, height);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer ascent = stack.mallocInt(1);
            STBTruetype.stbtt_GetFontVMetrics(fontInfo, ascent, null, null);
            this.ascent = ascent.get(0);
        }

        int registeredGlyphs = 0;
        for (int i = 0; i < cdata.length; i++) {
            STBTTPackedchar.Buffer cbuf = cdata[i];
            int offset = packRange.get(i).first_unicode_codepoint_in_range();

            for (int j = 0; j < cbuf.capacity(); j++) {
                STBTTPackedchar packedChar = cbuf.get(j);

                float ipw = 1f / SIZE;
                float iph = 1f / SIZE;

                int codePoint = j + offset;
                // Packing creates empty quads for spaces; they still have a real advance.
                // Reject missing Unicode characters so the lazy vanilla fallback can own them.
                if (STBTruetype.stbtt_FindGlyphIndex(fontInfo, codePoint) == 0) continue;
                boolean visible = packedChar.x0() != packedChar.x1() && packedChar.y0() != packedChar.y1();
                if (!visible && packedChar.xadvance() <= 0f) continue;

                registeredGlyphs++;
                CharData packed = new CharData(
                        packedChar.xoff(),
                        packedChar.yoff(),
                        packedChar.xoff2(),
                        packedChar.yoff2(),
                        packedChar.x0() * ipw,
                        packedChar.y0() * iph,
                        packedChar.x1() * ipw,
                        packedChar.y1() * iph,
                        packedChar.xadvance(),
                        visible
                );
                charMap.put(codePoint, packed);
                glyphIds.putIfAbsent(STBTruetype.stbtt_FindGlyphIndex(fontInfo, codePoint), packed);
            }
        }
        TextRenderSystem.glyphAtlases().onDirtyRectUpload(registeredGlyphs, SIZE * SIZE);
        } finally {
            MemoryUtil.memFree(bitmap);
        }
    }

    /** Lazy: most atlases never need complex shaping. Never load font data on every width call. */
    private OpenTypeShaper shaper() {
        if (shaperLoaded) return shaper;
        shaperLoaded = true;
        byte[] copy = new byte[fontData.remaining()];
        fontData.duplicate().get(copy);
        shaper = OpenTypeShaper.load(new ByteArrayInputStream(copy));
        if (shaper != null) {
            // STB packs at "pixel height" (ascent - descent), whereas the layout
            // engine reports em units. Calibrate from one actual packed glyph.
            for (int cp : new int[] {'m', 'A', '0', ' '}) {
                CharData data = charMap.get(cp);
                if (data == null) continue;
                OpenTypeShaper.ShapedRun one = shaper.shape(new String(Character.toChars(cp)));
                if (one.advance() > 0.0) {
                    shapedEmScale = data.xAdvance / one.advance();
                    break;
                }
            }
            if (!(shapedEmScale > 0.0) || !Double.isFinite(shapedEmScale)) shaper = null;
        }
        return shaper;
    }

    private OpenTypeShaper.ShapedRun shaped(String text) {
        OpenTypeShaper engine = shaper();
        if (engine == null) return null;
        OpenTypeShaper.ShapedRun run = engine.shape(text);
        for (OpenTypeShaper.PositionedGlyph g : run.glyphs()) {
            if (!glyphIds.containsKey(g.glyphId())) return null;
        }
        return run;
    }

    private float kerning(int previous, int current) {
        if (previous < 0 || current < 0) return 0f;
        long pair = ((long) previous << 32) | (current & 0xFFFFFFFFL);
        if (kerningCache.containsKey(pair)) return kerningCache.get(pair);
        float adjustment = STBTruetype.stbtt_GetCodepointKernAdvance(fontInfo, previous, current) * scale;
        if (kerningCache.size() >= MAX_KERNING_PAIRS) kerningCache.clear();
        kerningCache.put(pair, adjustment);
        return adjustment;
    }

    private CharData resolvedGlyph(int codePoint) {
        CharData glyph = charMap.get(codePoint);
        return glyph != null ? glyph : charMap.get('?');
    }

    public double getWidth(String string, int length) {
        if (string == null || length <= 0) return 0.0;
        double width = 0;
        int previous = -1;
        int end = Math.min(length, string.length());
        if (end > 0 && end < string.length() && Character.isHighSurrogate(string.charAt(end - 1))
                && Character.isLowSurrogate(string.charAt(end))) end--;
        OpenTypeShaper.ShapedRun run = shaped(string.substring(0, end));
        if (run != null) return run.advance() * shapedEmScale;
        for (int i = 0; i < end; ) {
            int cp = string.codePointAt(i);
            CharData c = resolvedGlyph(cp);
            int current = charMap.containsKey(cp) ? cp : -1;
            if (c != null) width += c.xAdvance + kerning(previous, current);
            previous = current;
            i += Character.charCount(cp);
        }
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

        y += ascent * this.scale * scale;

        int length = string.length();
        mesh.ensureCapacity(length * 4, length * 6);
        int previous = -1;

        for (int i = 0; i < length; ) {
            int cp = string.codePointAt(i);
            CharData c = resolvedGlyph(cp);
            if (c == null) {
                previous = -1;
                i += Character.charCount(cp);
                continue;
            }

            int current = charMap.containsKey(cp) ? cp : -1;
            x += kerning(previous, current) * scale;
            previous = current;

            if (c.visible) {
                int i1 = mesh.vec2(x + c.x0 * scale, y + c.y0 * scale).raw2(c.u0, c.v0).color(color.r, color.g, color.b, color.a).next();
                int i2 = mesh.vec2(x + c.x0 * scale, y + c.y1 * scale).raw2(c.u0, c.v1).color(color.r, color.g, color.b, color.a).next();
                int i3 = mesh.vec2(x + c.x1 * scale, y + c.y1 * scale).raw2(c.u1, c.v1).color(color.r, color.g, color.b, color.a).next();
                int i4 = mesh.vec2(x + c.x1 * scale, y + c.y0 * scale).raw2(c.u1, c.v0).color(color.r, color.g, color.b, color.a).next();

                mesh.quad(i1, i2, i3, i4);
            }

            x += c.xAdvance * scale;
            i += Character.charCount(cp);
        }

        return x;
    }

    @Override
    public double emitGlyphs(String string, double x, double y, double scale, GlyphConsumer consumer) {
        OpenTypeShaper.ShapedRun run = shaped(string);
        if (run != null) return emitShaped(run, x, y, scale, consumer);

        y += ascent * this.scale * scale;

        int length = string.length();
        int previous = -1;
        for (int i = 0; i < length; ) {
            int cp = string.codePointAt(i);
            CharData c = resolvedGlyph(cp);
            if (c == null) {
                previous = -1;
                i += Character.charCount(cp);
                continue;
            }

            int current = charMap.containsKey(cp) ? cp : -1;
            x += kerning(previous, current) * scale;
            previous = current;

            if (c.visible) {
                consumer.accept(
                        x + c.x0 * scale,
                        y + c.y0 * scale,
                        x + c.x1 * scale,
                        y + c.y1 * scale,
                        c.u0, c.v0, c.u1, c.v1
                );
            }

            x += c.xAdvance * scale;
            i += Character.charCount(cp);
        }

        return x;
    }

    @Override
    public double renderGradient(MeshBuilder mesh, String string, double x, double y, double scale, GlyphGradient gradient) {
        OpenTypeShaper.ShapedRun run = shaped(string);
        if (run != null) return renderShaped(mesh, run, x, y, null, scale, gradient, null);

        y += ascent * this.scale * scale;

        int length = string.length();
        mesh.ensureCapacity(length * 4, length * 6);

        int[] colors = new int[2];
        int glyphIndex = 0;
        int previous = -1;
        for (int i = 0; i < length; ) {
            int cp = string.codePointAt(i);
            CharData c = resolvedGlyph(cp);
            if (c == null) {
                previous = -1;
                i += Character.charCount(cp);
                glyphIndex++;
                continue;
            }

            int current = charMap.containsKey(cp) ? cp : -1;
            x += kerning(previous, current) * scale;
            previous = current;

            if (c.visible) {
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

                int i1 = mesh.vec2(x + c.x0 * scale, y + c.y0 * scale).raw2(c.u0, c.v0).color(lr, lg, lb, la).next();
                int i2 = mesh.vec2(x + c.x0 * scale, y + c.y1 * scale).raw2(c.u0, c.v1).color(lr, lg, lb, la).next();
                int i3 = mesh.vec2(x + c.x1 * scale, y + c.y1 * scale).raw2(c.u1, c.v1).color(rr, rg, rb, ra).next();
                int i4 = mesh.vec2(x + c.x1 * scale, y + c.y0 * scale).raw2(c.u1, c.v0).color(rr, rg, rb, ra).next();

                mesh.quad(i1, i2, i3, i4);
            }

            x += c.xAdvance * scale;
            i += Character.charCount(cp);
            glyphIndex++;
        }

        return x;
    }

    @Override
    public double renderQuadGradient(MeshBuilder mesh, String string, double x, double y, double scale, GlyphQuadGradient gradient) {
        OpenTypeShaper.ShapedRun run = shaped(string);
        if (run != null) return renderShaped(mesh, run, x, y, null, scale, null, gradient);

        y += ascent * this.scale * scale;

        int length = string.length();
        mesh.ensureCapacity(length * 4, length * 6);

        int[] colors = new int[4];
        int glyphIndex = 0;
        int previous = -1;
        for (int i = 0; i < length; ) {
            int cp = string.codePointAt(i);
            CharData c = resolvedGlyph(cp);
            if (c == null) {
                previous = -1;
                i += Character.charCount(cp);
                glyphIndex++;
                continue;
            }

            int current = charMap.containsKey(cp) ? cp : -1;
            x += kerning(previous, current) * scale;
            previous = current;

            if (c.visible) {
                double x0 = x + c.x0 * scale;
                double y0 = y + c.y0 * scale;
                double x1 = x + c.x1 * scale;
                double y1 = y + c.y1 * scale;

                gradient.colors(glyphIndex, cp, x0, y0, x1, y1, colors);
                int topLeftArgb = colors[0];
                int bottomLeftArgb = colors[1];
                int bottomRightArgb = colors[2];
                int topRightArgb = colors[3];

                int i1 = mesh.vec2(x0, y0).raw2(c.u0, c.v0).color(red(topLeftArgb), green(topLeftArgb), blue(topLeftArgb), alpha(topLeftArgb)).next();
                int i2 = mesh.vec2(x0, y1).raw2(c.u0, c.v1).color(red(bottomLeftArgb), green(bottomLeftArgb), blue(bottomLeftArgb), alpha(bottomLeftArgb)).next();
                int i3 = mesh.vec2(x1, y1).raw2(c.u1, c.v1).color(red(bottomRightArgb), green(bottomRightArgb), blue(bottomRightArgb), alpha(bottomRightArgb)).next();
                int i4 = mesh.vec2(x1, y0).raw2(c.u1, c.v0).color(red(topRightArgb), green(topRightArgb), blue(topRightArgb), alpha(topRightArgb)).next();

                mesh.quad(i1, i2, i3, i4);
            }

            x += c.xAdvance * scale;
            i += Character.charCount(cp);
            glyphIndex++;
        }

        return x;
    }

    private double emitShaped(OpenTypeShaper.ShapedRun run, double x, double y, double scale,
                              GlyphConsumer consumer) {
        double factor = shapedEmScale * scale;
        double baseline = y + ascent * this.scale * scale;
        for (OpenTypeShaper.PositionedGlyph slot : run.glyphs()) {
            CharData c = glyphIds.get(slot.glyphId());
            if (c == null || !c.visible) continue;
            double gx = x + slot.x() * factor;
            double gy = baseline + slot.y() * factor;
            consumer.accept(gx + c.x0 * scale, gy + c.y0 * scale,
                    gx + c.x1 * scale, gy + c.y1 * scale,
                    c.u0, c.v0, c.u1, c.v1);
        }
        return x + run.advance() * factor;
    }

    private double renderShaped(MeshBuilder mesh, OpenTypeShaper.ShapedRun run,
                                double x, double y, RenderColor color, double scale,
                                GlyphGradient gradient, GlyphQuadGradient quadGradient) {
        mesh.ensureCapacity(run.glyphs().length * 4, run.glyphs().length * 6);
        double factor = shapedEmScale * scale;
        double baseline = y + ascent * this.scale * scale;
        int[] pair = gradient == null ? null : new int[2];
        int[] quad = quadGradient == null ? null : new int[4];
        int ordinal = 0;
        for (OpenTypeShaper.PositionedGlyph slot : run.glyphs()) {
            CharData c = glyphIds.get(slot.glyphId());
            if (c != null && c.visible) {
                double gx = x + slot.x() * factor;
                double gy = baseline + slot.y() * factor;
                double x0 = gx + c.x0 * scale;
                double x1 = gx + c.x1 * scale;
                double y0 = gy + c.y0 * scale;
                double y1 = gy + c.y1 * scale;
                int tl, bl, br, tr;
                if (quad != null) {
                    quadGradient.colors(ordinal, slot.codePoint(), x0, y0, x1, y1, quad);
                    tl = quad[0]; bl = quad[1]; br = quad[2]; tr = quad[3];
                } else if (pair != null) {
                    gradient.colors(ordinal, slot.codePoint(), gx, pair);
                    tl = bl = pair[0]; br = tr = pair[1];
                } else {
                    tl = bl = br = tr = color.argb();
                }
                int i1 = mesh.vec2(x0, y0).raw2(c.u0, c.v0).color(red(tl), green(tl), blue(tl), alpha(tl)).next();
                int i2 = mesh.vec2(x0, y1).raw2(c.u0, c.v1).color(red(bl), green(bl), blue(bl), alpha(bl)).next();
                int i3 = mesh.vec2(x1, y1).raw2(c.u1, c.v1).color(red(br), green(br), blue(br), alpha(br)).next();
                int i4 = mesh.vec2(x1, y0).raw2(c.u1, c.v0).color(red(tr), green(tr), blue(tr), alpha(tr)).next();
                mesh.quad(i1, i2, i3, i4);
            }
            ordinal++;
        }
        return x + run.advance() * factor;
    }

    @Override
    public boolean hasGlyph(int codePoint) {
        return charMap.containsKey(codePoint);
    }

    @Override
    public double getAdvance(int codePoint) {
        CharData c = charMap.get(codePoint);
        if (c == null) return 0.0;
        return c.xAdvance;
    }

    @Override
    public Texture getTexture() {
        return texture;
    }

    @Override
    public boolean isReady() {
        return texture != null && texture.isReady();
    }

    @Override
    public void close() {
        if (texture != null) texture.close();
        MemoryUtil.memFree(fontData);
        shaper = null;
        kerningCache.clear();
    }

    @FunctionalInterface
    public interface GlyphGradient {
        void colors(int index, int codePoint, double x, int[] out);
    }

    @FunctionalInterface
    public interface GlyphQuadGradient {
        /**
         * Writes colors in vertex order: top-left, bottom-left, bottom-right, top-right.
         */
        void colors(int index, int codePoint, double x0, double y0, double x1, double y1, int[] out);
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

    private record CharData(float x0, float y0, float x1, float y1,
                            float u0, float v0, float u1, float v1,
                            float xAdvance, boolean visible) {
    }
}
