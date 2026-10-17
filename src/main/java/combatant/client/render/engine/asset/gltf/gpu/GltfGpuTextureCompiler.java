/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.gltf.gpu;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import combatant.client.render.engine.asset.gltf.model.GltfTexture;
import combatant.client.render.engine.rhi.CombatantRhi;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/** Decodes and uploads immutable glTF images through Blaze3D/RHI-owned device resources. */
public final class GltfGpuTextureCompiler {
    private static final int USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING;

    private GltfGpuTextureCompiler() {}

    public static GltfGpuTexture upload(CombatantRhi rhi,
                                        String label,
                                        GltfTexture source,
                                        GltfGpuTextureUsage usage) {
        Objects.requireNonNull(rhi, "rhi");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(usage, "usage");
        RenderSystem.assertOnRenderThread();

        byte[] encoded = bytes(source.encodedImage());
        try (NativeImage image = NativeImage.read(new ByteArrayInputStream(encoded))) {
            if (image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new IllegalArgumentException("Decoded glTF texture has invalid extent: " + label);
            }
            boolean mipmapped = usesMipmaps(source.minFilter());
            int mipLevels = mipmapped ? mipLevels(image.getWidth(), image.getHeight()) : 1;
            GpuTexture texture = RenderSystem.getDevice().createTexture(
                    label, USAGE, GpuFormat.RGBA8_UNORM,
                    image.getWidth(), image.getHeight(), 1, mipLevels
            );
            boolean success = false;
            try {
                NativeImage level = copy(image);
                try {
                    for (int mip = 0; mip < mipLevels; mip++) {
                        RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, level, mip, 0, 0, 0);
                        if (mip + 1 < mipLevels) {
                            NativeImage next = downsample(level, usage);
                            level.close();
                            level = next;
                        }
                    }
                } finally {
                    level.close();
                }

                GpuSampler sampler = rhi.resources().samplers().get(
                        address(source.wrapS()), address(source.wrapT()),
                        minFilter(source.minFilter()), magFilter(source.magFilter()), mipmapped
                );
                var view = rhi.resources().samplers().createTextureView(texture);
                success = true;
                return new GltfGpuTexture(texture, view, sampler, usage,
                        image.getWidth(), image.getHeight(), mipLevels);
            } finally {
                if (!success) {
                    try { texture.close(); } catch (Throwable ignored) { }
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to decode imported glTF image '" + label + "'", exception);
        }
    }

    public static GltfGpuTexture solid(CombatantRhi rhi, String label, int argb, GltfGpuTextureUsage usage) {
        RenderSystem.assertOnRenderThread();
        NativeImage image = new NativeImage(1, 1, false);
        image.setPixel(0, 0, argb);
        try {
            GpuTexture texture = RenderSystem.getDevice().createTexture(
                    label, USAGE, GpuFormat.RGBA8_UNORM, 1, 1, 1, 1
            );
            boolean success = false;
            try {
                RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, image, 0, 0, 0, 0);
                GpuSampler sampler = rhi.resources().samplers().get(
                        AddressMode.REPEAT, AddressMode.REPEAT,
                        FilterMode.LINEAR, FilterMode.LINEAR, false
                );
                var view = rhi.resources().samplers().createTextureView(texture);
                success = true;
                return new GltfGpuTexture(texture, view, sampler, usage, 1, 1, 1);
            } finally {
                if (!success) {
                    try { texture.close(); } catch (Throwable ignored) { }
                }
            }
        } finally {
            image.close();
        }
    }

    private static byte[] bytes(ByteBuffer source) {
        ByteBuffer copy = source.duplicate();
        byte[] out = new byte[copy.remaining()];
        copy.get(out);
        return out;
    }

    private static boolean usesMipmaps(int filter) {
        return filter == 9984 || filter == 9985 || filter == 9986 || filter == 9987;
    }

    private static int mipLevels(int width, int height) {
        int levels = 1;
        int extent = Math.max(width, height);
        while (extent > 1) {
            extent >>= 1;
            levels++;
        }
        return levels;
    }

    private static FilterMode magFilter(int filter) {
        return filter == 9728 ? FilterMode.NEAREST : FilterMode.LINEAR;
    }

    private static FilterMode minFilter(int filter) {
        return switch (filter) {
            case 9728, 9984, 9986 -> FilterMode.NEAREST;
            default -> FilterMode.LINEAR;
        };
    }

    private static AddressMode address(int wrap) {
        return switch (wrap) {
            case 33071 -> AddressMode.CLAMP_TO_EDGE;
            case 33648 -> throw new UnsupportedOperationException(
                    "glTF MIRRORED_REPEAT requires a backend-neutral mirrored sampler primitive");
            default -> AddressMode.REPEAT;
        };
    }

    private static NativeImage copy(NativeImage source) {
        // Keep this independent of NativeImage.copyRect direction/overload semantics. This runs
        // once while creating immutable imported texture residency, so an explicit pixel copy is
        // cheap and, more importantly, cannot accidentally overwrite the decoded source with a
        // zero-initialized destination (the cause of fully-black imported textures).
        NativeImage out = new NativeImage(source.getWidth(), source.getHeight(), false);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                out.setPixel(x, y, source.getPixel(x, y));
            }
        }
        return out;
    }

    private static NativeImage downsample(NativeImage source, GltfGpuTextureUsage usage) {
        int width = Math.max(1, source.getWidth() >> 1);
        int height = Math.max(1, source.getHeight() >> 1);
        NativeImage out = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int p0 = pixel(source, x * 2, y * 2);
                int p1 = pixel(source, x * 2 + 1, y * 2);
                int p2 = pixel(source, x * 2, y * 2 + 1);
                int p3 = pixel(source, x * 2 + 1, y * 2 + 1);
                out.setPixel(x, y, switch (usage) {
                    case SRGB_COLOR -> averageSrgb(p0, p1, p2, p3);
                    case NORMAL_DATA -> averageNormal(p0, p1, p2, p3);
                    case LINEAR_DATA -> averageLinearBytes(p0, p1, p2, p3);
                });
            }
        }
        return out;
    }

    private static int pixel(NativeImage image, int x, int y) {
        return image.getPixel(Math.min(image.getWidth() - 1, x), Math.min(image.getHeight() - 1, y));
    }

    private static int averageSrgb(int a, int b, int c, int d) {
        int[] p = {a, b, c, d};
        float lr = 0.0f, lg = 0.0f, lb = 0.0f;
        int alpha = 0;
        for (int value : p) {
            lr += srgbToLinear(red(value) / 255.0f);
            lg += srgbToLinear(green(value) / 255.0f);
            lb += srgbToLinear(blue(value) / 255.0f);
            alpha += alpha(value);
        }
        return argb((alpha + 2) / 4,
                unorm8(linearToSrgb(lr * 0.25f)),
                unorm8(linearToSrgb(lg * 0.25f)),
                unorm8(linearToSrgb(lb * 0.25f)));
    }

    private static int averageLinearBytes(int a, int b, int c, int d) {
        return argb(
                (alpha(a) + alpha(b) + alpha(c) + alpha(d) + 2) / 4,
                (red(a) + red(b) + red(c) + red(d) + 2) / 4,
                (green(a) + green(b) + green(c) + green(d) + 2) / 4,
                (blue(a) + blue(b) + blue(c) + blue(d) + 2) / 4
        );
    }

    private static int averageNormal(int a, int b, int c, int d) {
        int[] p = {a, b, c, d};
        float x = 0.0f, y = 0.0f, z = 0.0f;
        int alpha = 0;
        for (int value : p) {
            x += red(value) / 127.5f - 1.0f;
            y += green(value) / 127.5f - 1.0f;
            z += blue(value) / 127.5f - 1.0f;
            alpha += alpha(value);
        }
        float length2 = x * x + y * y + z * z;
        if (length2 < 1.0e-10f) {
            x = 0.0f; y = 0.0f; z = 1.0f;
        } else {
            float inv = (float) (1.0 / Math.sqrt(length2));
            x *= inv; y *= inv; z *= inv;
        }
        return argb((alpha + 2) / 4,
                unorm8(x * 0.5f + 0.5f),
                unorm8(y * 0.5f + 0.5f),
                unorm8(z * 0.5f + 0.5f));
    }

    private static float srgbToLinear(float value) {
        return value <= 0.04045f ? value / 12.92f
                : (float) Math.pow((value + 0.055f) / 1.055f, 2.4);
    }

    private static float linearToSrgb(float value) {
        value = Math.max(0.0f, Math.min(1.0f, value));
        return value <= 0.0031308f ? value * 12.92f
                : 1.055f * (float) Math.pow(value, 1.0 / 2.4) - 0.055f;
    }

    private static int unorm8(float value) {
        return Math.round(Math.max(0.0f, Math.min(1.0f, value)) * 255.0f);
    }

    private static int argb(int a, int r, int g, int b) {
        return ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }
    private static int alpha(int p) { return (p >>> 24) & 255; }
    private static int red(int p) { return (p >>> 16) & 255; }
    private static int green(int p) { return (p >>> 8) & 255; }
    private static int blue(int p) { return p & 255; }
}
