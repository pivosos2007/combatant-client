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

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
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
