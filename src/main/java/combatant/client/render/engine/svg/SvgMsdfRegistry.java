/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.svg;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import combatant.client.render.engine.Texture;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public enum SvgMsdfRegistry {
    ;
    private static final String SVG_ROOT = "svg/";
    private static final String MSDF_ROOT = "svg/msdf/";
    private static final Map<Identifier, Entry> CACHE = new HashMap<>();
    private static final Set<Identifier> MISSING = new HashSet<>();

    public static void clearCache() {
        synchronized (CACHE) {
            for (Entry entry : CACHE.values()) {
                entry.close();
            }
            CACHE.clear();
        }
        synchronized (MISSING) {
            MISSING.clear();
        }
    }


    public static boolean preload(Identifier svgId) {
        Entry entry = get(svgId);
        return entry != null && entry.isReady();
    }

    public static boolean draw(Renderer2D renderer,
                               Identifier svgId,
                               double x,
                               double y,
                               double width,
                               double height,
                               SvgRenderOptions options) {
        if (renderer == null || svgId == null || options == null) return false;
        if (options.colorMode() != SvgColorMode.OVERRIDE && options.colorMode() != SvgColorMode.GRADIENT_LINEAR) return false;

        Entry entry = get(svgId);
        if (entry == null || !entry.isReady()) return false;

        if (options.colorMode() == SvgColorMode.GRADIENT_LINEAR) {
            int[] colors = gradientColors(width, height, options);
            renderer.msdfTextureQuad(
                    entry.texture.getTextureView(),
                    entry.texture.getSampler(),
                    x,
                    y,
                    width,
                    height,
                    colors[0],
                    colors[1],
                    colors[2],
                    colors[3],
                    entry.pxRange,
                    entry.width,
                    entry.height
            );
        } else {
            renderer.msdfTextureQuad(
                    entry.texture.getTextureView(),
                    entry.texture.getSampler(),
                    x,
                    y,
                    width,
                    height,
                    tint(options),
                    entry.pxRange,
                    entry.width,
                    entry.height
            );
        }
        return true;
    }

    private static @Nullable Entry get(Identifier svgId) {
        synchronized (CACHE) {
            Entry cached = CACHE.get(svgId);
            if (cached != null) return cached;
        }
        synchronized (MISSING) {
            if (MISSING.contains(svgId)) return null;
        }

        Entry loaded = load(svgId);
        if (loaded == null) {
            synchronized (MISSING) {
                MISSING.add(svgId);
            }
            return null;
        }

        synchronized (CACHE) {
            Entry raced = CACHE.get(svgId);
            if (raced != null) {
                loaded.close();
                return raced;
            }
            CACHE.put(svgId, loaded);
        }
        return loaded;
    }

    private static @Nullable Entry load(Identifier svgId) {
        Identifier jsonId = msdfId(svgId, "json");
        Identifier pngId = msdfId(svgId, "png");
        if (jsonId == null || pngId == null) return null;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        ResourceManager rm = mc.getResourceManager();

        JsonObject root;
        try (InputStream in = rm.getResource(jsonId).orElseThrow().open()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }

        BufferedImage image;
        try (InputStream in = rm.getResource(pngId).orElseThrow().open()) {
            image = ImageIO.read(in);
            if (image == null) return null;
        } catch (Exception ignored) {
            DebugLog.warn("[Combatant][SVG] MSDF texture missing/invalid: %s", pngId);
            return null;
        }

        int width = getInt(root, "width", image.getWidth());
        int height = getInt(root, "height", image.getHeight());
        float pxRange = getFloat(root, "pxRange", 4.0f);
        if (width <= 0) width = image.getWidth();
        if (height <= 0) height = image.getHeight();

        Texture texture = uploadTexture(image);
        if (texture == null) {
            DebugLog.warn("[Combatant][SVG] MSDF texture upload failed: %s", pngId);
            return null;
        }
        return new Entry(texture, width, height, pxRange);
    }

    private static @Nullable Identifier msdfId(Identifier svgId, String extension) {
        if (svgId == null || extension == null || extension.isBlank()) return null;
        String path = svgId.getPath();
        if (path == null || !path.startsWith(SVG_ROOT) || !path.endsWith(".svg")) return null;
        String relative = path.substring(SVG_ROOT.length(), path.length() - 4);
        if (relative.isBlank() || relative.startsWith("msdf/")) return null;
        return Identifier.fromNamespaceAndPath(svgId.getNamespace(), MSDF_ROOT + relative + "." + extension);
    }

    private static @Nullable Texture uploadTexture(BufferedImage image) {
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

            Texture texture = new Texture(w, h, GpuFormat.RGBA8_UNORM, FilterMode.LINEAR, FilterMode.LINEAR,
                    AddressMode.CLAMP_TO_EDGE);
            texture.upload(buffer);
            return texture;
        } catch (Exception ignored) {
            return null;
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    private static int tint(SvgRenderOptions options) {
        int argb = options.overrideArgb();
        int alpha = (argb >>> 24) & 0xFF;
        alpha = Math.max(0, Math.min(255, Math.round(alpha * Math.max(0.0f, Math.min(1.0f, options.alpha())))));
        return (argb & 0x00FFFFFF) | (alpha << 24);
    }

    private static int[] gradientColors(double width, double height, SvgRenderOptions options) {
        float angle = (float) Math.toRadians(options.gradientAngleDegrees());
        float dirX = (float) Math.cos(angle);
        float dirY = (float) Math.sin(angle);
        float w = (float) Math.abs(width);
        float h = (float) Math.abs(height);

        float p0 = 0.0f;
        float p1 = w * dirX;
        float p2 = h * dirY;
        float p3 = w * dirX + h * dirY;
        float minProj = Math.min(Math.min(p0, p1), Math.min(p2, p3));
        float maxProj = Math.max(Math.max(p0, p1), Math.max(p2, p3));
        float range = Math.max(0.0001f, maxProj - minProj);

        int start = applyAlpha(options.gradientStartArgb(), options.alpha());
        int end = applyAlpha(options.gradientEndArgb(), options.alpha());
        return new int[]{
                mixArgb(start, end, clamp01((p0 - minProj) / range)),
                mixArgb(start, end, clamp01((p1 - minProj) / range)),
                mixArgb(start, end, clamp01((p3 - minProj) / range)),
                mixArgb(start, end, clamp01((p2 - minProj) / range))
        };
    }

    private static int applyAlpha(int argb, float alphaMul) {
        int alpha = (argb >>> 24) & 0xFF;
        alpha = Math.max(0, Math.min(255, Math.round(alpha * clamp01(alphaMul))));
        return (argb & 0x00FFFFFF) | (alpha << 24);
    }

    private static int mixArgb(int start, int end, float t) {
        int sa = (start >>> 24) & 0xFF;
        int sr = (start >>> 16) & 0xFF;
        int sg = (start >>> 8) & 0xFF;
        int sb = start & 0xFF;

        int ea = (end >>> 24) & 0xFF;
        int er = (end >>> 16) & 0xFF;
        int eg = (end >>> 8) & 0xFF;
        int eb = end & 0xFF;

        int a = (int) (sa + (ea - sa) * t + 0.5f);
        int r = (int) (sr + (er - sr) * t + 0.5f);
        int g = (int) (sg + (eg - sg) * t + 0.5f);
        int b = (int) (sb + (eb - sb) * t + 0.5f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float clamp01(float value) {
        if (value <= 0.0f) return 0.0f;
        if (value >= 1.0f) return 1.0f;
        return value;
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        if (obj == null || key == null || !obj.has(key)) return fallback;
        try {
            return obj.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
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

    private record Entry(Texture texture, int width, int height, float pxRange) {
        boolean isReady() {
            return texture != null && texture.isReady();
        }

        void close() {
            if (texture != null) texture.close();
        }
    }
}
