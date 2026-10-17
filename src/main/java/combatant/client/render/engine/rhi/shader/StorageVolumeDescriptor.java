/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;

/** Generic native three-dimensional image used by compute/volumetric workloads. */
public record StorageVolumeDescriptor(String label,
                                      int width,
                                      int height,
                                      int depth,
                                      GpuFormat format,
                                      StorageAccess access,
                                      int usage,
                                      int mipLevels) {
    private static final int ALLOWED_USAGE = RhiTextureUsage.STORAGE_IMAGE
            | GpuTexture.USAGE_TEXTURE_BINDING
            | GpuTexture.USAGE_COPY_SRC
            | GpuTexture.USAGE_COPY_DST;

    public StorageVolumeDescriptor {
        label = label == null || label.isBlank() ? "combatant-storage-volume" : label;
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("Storage volume dimensions must be positive: "
                    + width + "x" + height + "x" + depth);
        }
        if (format == null || !format.hasColorAspect()) {
            throw new IllegalArgumentException("3D volume requires a color format: " + format);
        }
        access = access == null ? StorageAccess.READ_WRITE : access;
        if (usage == 0) {
            throw new IllegalArgumentException("3D volume usage must declare at least one usage bit");
        }
        if ((usage & RhiTextureUsage.STORAGE_IMAGE) != 0 && format.componentCount() == 3) {
            throw new IllegalArgumentException("3D storage-image usage requires an image-load/store-compatible format: " + format);
        }
        int unsupportedUsage = usage & ~ALLOWED_USAGE;
        if (unsupportedUsage != 0) {
            throw new IllegalArgumentException("Unsupported storage-volume usage bits: 0x"
                    + Integer.toHexString(unsupportedUsage));
        }
        int maximumMipLevels = 32 - Integer.numberOfLeadingZeros(Math.max(width, Math.max(height, depth)));
        if (mipLevels < 1 || mipLevels > maximumMipLevels) {
            throw new IllegalArgumentException("Invalid mip count " + mipLevels + " for "
                    + width + "x" + height + "x" + depth + " storage volume");
        }
    }

    public StorageVolumeDescriptor(String label,
                                   int width,
                                   int height,
                                   int depth,
                                   GpuFormat format,
                                   StorageAccess access) {
        this(label, width, height, depth, format, access, RhiTextureUsage.STORAGE_IMAGE, 1);
    }

    public boolean storage() {
        return (usage & RhiTextureUsage.STORAGE_IMAGE) != 0;
    }

    public boolean sampled() {
        return (usage & GpuTexture.USAGE_TEXTURE_BINDING) != 0;
    }

    public boolean copySource() {
        return (usage & GpuTexture.USAGE_COPY_SRC) != 0;
    }

    public boolean copyDestination() {
        return (usage & GpuTexture.USAGE_COPY_DST) != 0;
    }

    public int mipWidth(int mipLevel) {
        checkMip(mipLevel);
        return Math.max(1, width >> mipLevel);
    }

    public int mipHeight(int mipLevel) {
        checkMip(mipLevel);
        return Math.max(1, height >> mipLevel);
    }

    public int mipDepth(int mipLevel) {
        checkMip(mipLevel);
        return Math.max(1, depth >> mipLevel);
    }

    public long approximateByteSize() {
        long total = 0L;
        long bytesPerTexel = format.blockSize();
        for (int mip = 0; mip < mipLevels; mip++) {
            long texels = saturatedMultiply(mipWidth(mip), mipHeight(mip));
            texels = saturatedMultiply(texels, mipDepth(mip));
            long bytes = saturatedMultiply(texels, bytesPerTexel);
            if (Long.MAX_VALUE - total < bytes) return Long.MAX_VALUE;
            total += bytes;
        }
        return total;
    }

    private void checkMip(int mipLevel) {
        if (mipLevel < 0 || mipLevel >= mipLevels) {
            throw new IllegalArgumentException("Mip level out of range: " + mipLevel + " / " + mipLevels);
        }
    }

    private static long saturatedMultiply(long a, long b) {
        if (a == 0L || b == 0L) return 0L;
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;
        return a * b;
    }
}
