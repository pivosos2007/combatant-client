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
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.FilterMode;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MsdfFont implements GlyphFont {
    private static final int FONT_SIZES = 5;
    private static final float BASE_HEIGHT = 27f;
    private static final int WIDTH_CACHE_LIMIT = 512;
    private static volatile String lastError = "";
    private final MsdfAtlas atlas;
    private final float pixelScale;
    private final int height;
    private final LinkedHashMap<WidthKey, Double> widthCache = new LinkedHashMap<>(WIDTH_CACHE_LIMIT, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<WidthKey, Double> eldest) {
            return size() > WIDTH_CACHE_LIMIT;
        }
    };

    private MsdfFont(MsdfAtlas atlas, float targetHeight) {
        this.atlas = atlas;
        this.height = Math.max(1, Math.round(targetHeight));
        this.pixelScale = targetHeight / atlas.lineHeight;
        atlas.retain();
    }

    public static GlyphFont[] tryCreate(FontFace face) {
        if (!(face instanceof BuiltinFontFace)) return null;
        MsdfAtlas atlas = MsdfAtlas.load(face.info);
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

    @Override
    public double getWidth(String string, int length) {
        if (string == null || string.isEmpty() || length <= 0) return 0.0;
        int safeLength = Math.min(length, string.length());
        WidthKey key = new WidthKey(string, safeLength);
        Double cached = widthCache.get(key);
        if (cached != null) return cached;

        double width = 0;
        for (int i = 0; i < safeLength; ) {
            int cp = string.codePointAt(i);
            Glyph g = atlas.glyphs.get(cp);
            if (g == null) g = atlas.fallback;
            if (g != null) width += g.advance * pixelScale;
            i += Character.charCount(cp);
        }
        widthCache.put(key, width);
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public double render(MeshBuilder mesh, String string, double x, double y, RenderColor color, double scale) {
        double baseline = y + atlas.ascender * pixelScale * scale;

        int length = string.length();
        mesh.ensureCapacity(length * 4, length * 6);

        for (int i = 0; i < length; ) {
            int cp = string.codePointAt(i);
            Glyph g = atlas.glyphs.get(cp);
            if (g == null) g = atlas.fallback;
            if (g == null) {
                i += Character.charCount(cp);
                continue;
            }

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
        double baseline = y + atlas.ascender * pixelScale * scale;

        int length = string.length();
        for (int i = 0; i < length; ) {
            int cp = string.codePointAt(i);
            Glyph g = atlas.glyphs.get(cp);
            if (g == null) g = atlas.fallback;
            if (g == null) {
                i += Character.charCount(cp);
                continue;
            }

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

    @Override
    public boolean hasGlyph(int codePoint) {
        return atlas.glyphs.containsKey(codePoint);
    }

    @Override
    public double getAdvance(int codePoint) {
        Glyph g = atlas.glyphs.get(codePoint);
        return g != null ? g.advance * pixelScale : 0.0;
    }

    @Override
    public double renderGradient(MeshBuilder mesh, String string, double x, double y, double scale, Font.GlyphGradient gradient) {
        double baseline = y + atlas.ascender * pixelScale * scale;

        int length = string.length();
        mesh.ensureCapacity(length * 4, length * 6);

        int[] colors = new int[2];
        int glyphIndex = 0;
        for (int i = 0; i < length; ) {
            int cp = string.codePointAt(i);
            Glyph g = atlas.glyphs.get(cp);
            if (g == null) g = atlas.fallback;
            if (g == null) {
                i += Character.charCount(cp);
                glyphIndex++;
                continue;
            }

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
        double baseline = y + atlas.ascender * pixelScale * scale;

        int length = string.length();
        mesh.ensureCapacity(length * 4, length * 6);

        int[] colors = new int[4];
        int glyphIndex = 0;
        for (int i = 0; i < length; ) {
            int cp = string.codePointAt(i);
            Glyph g = atlas.glyphs.get(cp);
            if (g == null) g = atlas.fallback;
            if (g == null) {
                i += Character.charCount(cp);
                glyphIndex++;
                continue;
            }

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
        widthCache.clear();
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

    private record WidthKey(String text, int length) {
    }

    private static final class MsdfAtlas {
        private final Texture texture;
        private final float distanceRange;
        private final int width;
        private final int height;
        private final float lineHeight;
        private final float ascender;
        private final Int2ObjectOpenHashMap<Glyph> glyphs;
        private final Glyph fallback;
        private int refs;

        private MsdfAtlas(Texture texture,
                          float distanceRange,
                          int width,
                          int height,
                          float lineHeight,
                          float ascender,
                          Int2ObjectOpenHashMap<Glyph> glyphs,
                          Glyph fallback) {
            this.texture = texture;
            this.distanceRange = distanceRange;
            this.width = width;
            this.height = height;
            this.lineHeight = lineHeight;
            this.ascender = ascender;
            this.glyphs = glyphs;
            this.fallback = fallback;
        }

        private static MsdfAtlas load(FontInfo info) {
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

            Int2ObjectOpenHashMap<Glyph> glyphs = new Int2ObjectOpenHashMap<>();
            JsonArray glyphArray = getArray(root, "glyphs");
            int missingUnicode = 0;
            int duplicateUnicode = 0;
            if (glyphArray != null) {
                float invW = 1f / atlasWidth;
                float invH = 1f / atlasHeight;

                for (JsonElement el : glyphArray) {
                    if (!el.isJsonObject()) {
                        missingUnicode++;
                        continue;
                    }
                    JsonObject g = el.getAsJsonObject();

                    int codePoint = readUnicode(g.get("unicode"));
                    if (!isUnicodeScalar(codePoint)) {
                        missingUnicode++;
                        continue;
                    }

                    float advance = getFloat(g, "advance", 0f);
                    Bounds plane = readBounds(getObject(g, "planeBounds"));
                    Bounds atlas = readBounds(getObject(g, "atlasBounds"));

                    Glyph glyph = Glyph.from(advance, plane, atlas, yOriginBottom, atlasWidth, atlasHeight, invW, invH);
                    if (glyphs.put(codePoint, glyph) != null) duplicateUnicode++;
                }
            }

            if (missingUnicode > 0) {
                warnBadAtlas(jsonId, "unicode", "ignored " + missingUnicode
                        + " glyph entries without a valid Unicode mapping; do not generate runtime atlases with -allglyphs");
            }
            if (duplicateUnicode > 0) {
                warnBadAtlas(jsonId, "unicode-duplicate", "contains " + duplicateUnicode
                        + " duplicate Unicode mappings");
            }

            if (glyphs.isEmpty()) {
                setLastError("msdf glyphs empty: " + jsonId);
                warnBadAtlas(jsonId, "glyphs-empty", glyphArray == null
                        ? "glyphs array is missing"
                        : "no loadable Unicode-mapped glyphs were found");
                return null;
            }

            Glyph fallback = glyphs.get(32);
            if (fallback == null) fallback = glyphs.get('?');

            Texture texture = uploadTexture(image);
            if (texture == null) {
                setLastError("msdf texture upload failed: " + pngId);
                warnBadAtlas(jsonId, "texture-upload", "texture " + pngId + " could not be uploaded to the GPU");
                return null;
            }

            setLastError("");
            TextRenderSystem.glyphAtlases().registerPage("msdf:" + jsonId, texture, atlasWidth, atlasHeight, true);
            TextRenderSystem.glyphAtlases().onDirtyRectUpload(glyphs.size(), atlasWidth * atlasHeight * 4);
            return new MsdfAtlas(texture, distanceRange, atlasWidth, atlasHeight, lineHeight, ascender, glyphs, fallback);
        }

        private static Texture uploadTexture(BufferedImage image) {
            int w = image.getWidth();
            int h = image.getHeight();
            int[] pixels = new int[w * h];
            image.getRGB(0, 0, w, h, pixels, 0, w);

            ByteBuffer buffer = MemoryUtil.memAlloc(w * h * 4);
            try {
                for (int argb : pixels) {
                    buffer.put((byte) ((argb >> 16) & 0xFF));
                    buffer.put((byte) ((argb >> 8) & 0xFF));
                    buffer.put((byte) (argb & 0xFF));
                    buffer.put((byte) ((argb >> 24) & 0xFF));
                }
                buffer.flip();

                Texture texture = new Texture(w, h, GpuFormat.RGBA8_UNORM, FilterMode.LINEAR, FilterMode.LINEAR);
                texture.upload(buffer);
                return texture;
            } catch (Exception ignored) {
                return null;
            } finally {
                MemoryUtil.memFree(buffer);
            }
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
            if (refs <= 0 && texture != null) {
                texture.close();
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
