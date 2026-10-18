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

package combatant.client.render.engine;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class Texture extends AbstractTexture {
    public Texture(int width, int height, GpuFormat format, FilterMode min, FilterMode mag) {
        this(width, height, format, min, mag, AddressMode.REPEAT, AddressMode.REPEAT);
    }

    public Texture(int width, int height, GpuFormat format, FilterMode min, FilterMode mag, AddressMode addressMode) {
        this(width, height, format, min, mag, addressMode, addressMode);
    }

    public Texture(int width,
                   int height,
                   GpuFormat format,
                   FilterMode min,
                   FilterMode mag,
                   AddressMode addressModeU,
                   AddressMode addressModeV) {
        texture = RenderSystem.getDevice().createTexture("", 15, format, width, height, 1, 1);
        sampler = RenderSystem.getSamplerCache().getSampler(addressModeU, addressModeV, min, mag, false);
        textureView = RenderSystem.getDevice().createTextureView(texture);
    }

    public int getWidth() {
        return getTexture().getWidth(0);
    }

    public int getHeight() {
        return getTexture().getHeight(0);
    }

    public void upload(byte[] bytes) {
        ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
        try {
            buffer.put(bytes).flip();
            upload(buffer);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    public void upload(ByteBuffer buffer) {
        var image = getImage();

        buffer.rewind();
        MemoryUtil.memCopy(MemoryUtil.memAddress(buffer), image.getPointer(), buffer.remaining());

        RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, image);

        image.close();
    }

    /**
     * Upload only the dirty glyph rectangle instead of retransferring the full
     * atlas (up to 64 MiB). RenderPearl 26.3 supports offset texture writes in
     * both OpenGL and Vulkan command encoders.
     */
    public void uploadAtlasRegion(ByteBuffer atlas, int bytesPerPixel,
                                  int sourceStride, int sourceFirstRow,
                                  int x, int y, int width, int height) {
        if (atlas == null || width <= 0 || height <= 0) return;
        int textureWidth = getWidth();
        int textureHeight = getHeight();
        if ((bytesPerPixel != 1 && bytesPerPixel != 4) || x < 0 || y < 0
                || (long) x + width > textureWidth || (long) y + height > textureHeight
                || sourceStride < x + width || y < sourceFirstRow
                || ((long) y - sourceFirstRow + height) * sourceStride * bytesPerPixel > atlas.capacity())
            throw new IllegalArgumentException("Invalid glyph atlas upload region");
        int rowBytes = Math.multiplyExact(width, bytesPerPixel);
        ByteBuffer staging = MemoryUtil.memAlloc(Math.multiplyExact(rowBytes, height));
        try {
            long source = MemoryUtil.memAddress(atlas);
            long destination = MemoryUtil.memAddress(staging);
            long stride = (long) sourceStride * bytesPerPixel;
            for (int row = 0; row < height; row++) {
                long sourceOffset = ((long) y - sourceFirstRow + row) * stride + (long) x * bytesPerPixel;
                MemoryUtil.memCopy(source + sourceOffset,
                        destination + (long) row * rowBytes, rowBytes);
            }
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                    texture, staging, 0, 0, x, y, width, height);
        } finally {
            MemoryUtil.memFree(staging);
        }
    }

    public boolean isReady() {
        return texture != null && textureView != null && sampler != null;
    }

    private @NotNull NativeImage getImage() {
        NativeImage.Format imageFormat = switch (texture.getFormat()) {
            case RGBA8_UNORM -> NativeImage.Format.RGBA;
            case R8_UNORM -> NativeImage.Format.LUMINANCE;
            default -> throw new IllegalArgumentException("Unsupported texture format.");
        };

        return new NativeImage(imageFormat, getWidth(), getHeight(), false);
    }
}
