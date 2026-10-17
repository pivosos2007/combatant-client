/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.framegraph;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import combatant.client.render.engine.rhi.shader.RhiTextureUsage;
import combatant.client.render.engine.rhi.shader.StorageAccess;


/** Exact allocation contract for a graph-owned 2D texture. */
public record FrameGraphTextureDescriptor(int width,
                                          int height,
                                          int samples,
                                          int mipLevels,
                                          GpuFormat format,
                                          int usage,
                                          StorageAccess storageAccess)
        implements FrameGraphPhysicalResourceDescriptor {
    private static final int ALLOWED_USAGE = RhiTextureUsage.STORAGE_IMAGE
            | GpuTexture.USAGE_TEXTURE_BINDING
            | GpuTexture.USAGE_COPY_SRC
            | GpuTexture.USAGE_COPY_DST
            | GpuTexture.USAGE_RENDER_ATTACHMENT;

    public FrameGraphTextureDescriptor {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Texture dimensions must be positive");
        if (samples <= 0) throw new IllegalArgumentException("Texture sample count must be positive");
        if (format == null) throw new IllegalArgumentException("format");
        if (usage == 0) throw new IllegalArgumentException("Texture usage must be explicit");
        int unsupportedUsage = usage & ~ALLOWED_USAGE;
        if (unsupportedUsage != 0) {
            throw new IllegalArgumentException("Unsupported frame-graph texture usage bits: 0x"
                    + Integer.toHexString(unsupportedUsage));
        }
        int maxMips = 32 - Integer.numberOfLeadingZeros(Math.max(width, height));
        if (mipLevels < 1 || mipLevels > maxMips) {
            throw new IllegalArgumentException("Invalid mip count " + mipLevels + " for " + width + "x" + height);
        }
        if (samples > 1 && mipLevels != 1) {
            throw new IllegalArgumentException("Multisampled graph textures cannot have mip chains");
        }
        boolean storage = (usage & RhiTextureUsage.STORAGE_IMAGE) != 0;
        if (storage && samples > 1) {
            throw new IllegalArgumentException("Storage graph textures cannot be multisampled");
        }
        if (storage && !format.hasColorAspect()) {
            throw new IllegalArgumentException("Storage graph textures require a color format: " + format);
        }
        storageAccess = storage
                ? (storageAccess == null ? StorageAccess.READ_WRITE : storageAccess)
                : StorageAccess.READ_WRITE;
    }

    @Override
    public FrameGraphResourceKind kind() {
        return FrameGraphResourceKind.TEXTURE;
    }

    public boolean storage() {
        return (usage & RhiTextureUsage.STORAGE_IMAGE) != 0;
    }

    public boolean sampled() {
        return (usage & GpuTexture.USAGE_TEXTURE_BINDING) != 0;
    }

    public boolean renderAttachment() {
        return (usage & GpuTexture.USAGE_RENDER_ATTACHMENT) != 0;
    }

    public boolean copySource() {
        return (usage & GpuTexture.USAGE_COPY_SRC) != 0;
    }

    public boolean copyDestination() {
        return (usage & GpuTexture.USAGE_COPY_DST) != 0;
    }

    @Override
    public boolean supports(FrameGraphAccess access) {
        if (access == null) return false;
        boolean readable = sampled() || copySource() || (storage() && storageAccess.readable());
        boolean writable = renderAttachment() || copyDestination() || (storage() && storageAccess.writable());
        return (!access.reads() || readable) && (!access.writes() || writable);
    }

    @Override
    public boolean aliasCompatible(FrameGraphPhysicalResourceDescriptor other) {
        if (!(other instanceof FrameGraphTextureDescriptor value)) return false;
        return width == value.width && height == value.height && samples == value.samples
                && mipLevels == value.mipLevels && format == value.format && usage == value.usage
                && storageAccess == value.storageAccess;
    }

    @Override
    public long approximateByteSize() {
        long bytes = 0L;
        for (int mip = 0; mip < mipLevels; mip++) {
            long w = Math.max(1, width >> mip);
            long h = Math.max(1, height >> mip);
            long level = saturatedMultiply(saturatedMultiply(w, h), format.blockSize());
            level = saturatedMultiply(level, samples);
            if (Long.MAX_VALUE - bytes < level) return Long.MAX_VALUE;
            bytes += level;
        }
        return bytes;
    }

    @Override
    public String debugDescription() {
        return width + "x" + height + " samples=" + samples + " mips=" + mipLevels
                + " format=" + format + " usage=0x" + Integer.toHexString(usage)
                + " storageAccess=" + storageAccess;
    }

    private static long saturatedMultiply(long a, long b) {
        if (a == 0L || b == 0L) return 0L;
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }
}
