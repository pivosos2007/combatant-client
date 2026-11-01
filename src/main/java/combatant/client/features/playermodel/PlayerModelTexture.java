/* Combatant Client (GPL-3.0), Copyright (c) 2026 pivosos2007. */
package combatant.client.features.playermodel;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.*;
import net.minecraft.client.renderer.texture.AbstractTexture;
import javax.imageio.ImageIO;
import java.io.*;
import java.util.*;

final class PlayerModelTexture extends AbstractTexture {
    record Level(int width, int height, int[] pixels) {}
    record Quality(float scale, boolean linear) {}
    private static final int MIN_REDUCED_SIDE = 32;

    PlayerModelTexture(String label, List<Level> levels, Quality quality) {
        RenderSystem.assertOnRenderThread();
        Level first = levels.getFirst();
        try {
            texture = RenderSystem.getDevice().createTexture(label,
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    GpuFormat.RGBA8_UNORM, first.width, first.height, 1, levels.size());
            for (int mip = 0; mip < levels.size(); mip++) {
                Level level = levels.get(mip);
                try (NativeImage image = new NativeImage(level.width, level.height, false)) {
                    for (int y = 0; y < level.height; y++) for (int x = 0; x < level.width; x++)
                        image.setPixel(x, y, level.pixels[y * level.width + x]);
                    RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, image, mip, 0, 0, 0);
                }
            }
            FilterMode filter = quality.linear ? FilterMode.LINEAR : FilterMode.NEAREST;
            sampler = RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.REPEAT,
                    filter, filter, true);
            textureView = RenderSystem.getDevice().createTextureView(texture);
        } catch (RuntimeException failure) { close(); throw failure; }
    }

    static List<Level> decode(byte[] bytes, Quality quality) throws IOException {
        PlayerModels.validatePng(bytes);
        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) throw new IOException("Cannot decode model texture");
        Level level = new Level(image.getWidth(), image.getHeight(),
                image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()));
        return levels(level, quality);
    }

    static List<Level> levels(Level source, Quality quality) {
        if (!Float.isFinite(quality.scale) || quality.scale <= 0 || quality.scale > 1
                || source.width <= 0 || source.height <= 0 || source.pixels.length != source.width * source.height)
            throw new IllegalArgumentException("Invalid texture dimensions or quality");

        // Quality is a fraction of the *source atlas*, never a fixed pixel ceiling.
        // Avoid destroying small/skinny texture atlases: reduce neither original
        // dimension below 32 texels unless that dimension was already < 32.
        double minScale = Math.min(1.0, MIN_REDUCED_SIDE / (double) Math.min(source.width, source.height));
        double scale = Math.max(quality.scale, minScale);
        int width = Math.max(1, Math.min(source.width, (int) Math.round(source.width * scale)));
        int height = Math.max(1, Math.min(source.height, (int) Math.round(source.height * scale)));
        Level level = width == source.width && height == source.height ? source : resample(source, width, height);

        // All uploaded PlayerModel textures have the complete mip pyramid.
        List<Level> result = new ArrayList<>();
        result.add(level);
        while (level.width > 1 || level.height > 1) {
            level = downsample(level);
            result.add(level);
        }
        return List.copyOf(result);
    }

    private static final float[] LINEAR = new float[256];
    static {
        for (int i = 0; i < 256; i++) {
            float v = i / 255f;
            LINEAR[i] = v <= 0.04045f ? v / 12.92f : (float) Math.pow((v + .055f) / 1.055f, 2.4);
        }
    }
    private static int srgb(double v) {
        return (int) Math.round(255 * Math.clamp(v <= .0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - .055, 0, 1));
    }
    static Level downsample(Level source) {
        return resample(source, Math.max(1, source.width / 2), Math.max(1, source.height / 2));
    }

    // Area filtered in linear-light, alpha-premultiplied space. Supports arbitrary
    // relative quality factors as well as non-power-of-two mip transitions.
    static Level resample(Level source, int width, int height) {
        if (width < 1 || height < 1 || width > source.width || height > source.height)
            throw new IllegalArgumentException("Invalid reduced texture size");
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            // Area integration includes the final row/column of non-power-of-two community textures.
            double x0 = (double) x * source.width / width, x1 = (double) (x + 1) * source.width / width;
            double y0 = (double) y * source.height / height, y1 = (double) (y + 1) * source.height / height;
            double alpha = 0, red = 0, green = 0, blue = 0, area = (x1 - x0) * (y1 - y0);
            for (int sy = (int) y0; sy < Math.ceil(y1); sy++) for (int sx = (int) x0; sx < Math.ceil(x1); sx++) {
                double weight = (Math.min(x1, sx + 1) - Math.max(x0, sx)) * (Math.min(y1, sy + 1) - Math.max(y0, sy));
                int pixel = source.pixels[sy * source.width + sx];
                double a = (pixel >>> 24) * weight;
                alpha += a;
                red += LINEAR[(pixel >>> 16) & 255] * a;
                green += LINEAR[(pixel >>> 8) & 255] * a;
                blue += LINEAR[pixel & 255] * a;
            }
            // Premultiplied linear-light averaging avoids dark fringes at transparent atlas edges.
            pixels[y * width + x] = alpha == 0 ? 0 : ((int) Math.round(alpha / area) << 24)
                    | (srgb(red / alpha) << 16) | (srgb(green / alpha) << 8) | srgb(blue / alpha);
        }
        return new Level(width, height, pixels);
    }
}
