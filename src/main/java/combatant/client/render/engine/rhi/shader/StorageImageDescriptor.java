/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

import com.mojang.renderpearl.api.GpuFormat;

/** Description of a 2D image that can participate in native image load/store. */
public record StorageImageDescriptor(String label,
                                     int width,
                                     int height,
                                     GpuFormat format,
                                     StorageAccess access,
                                     boolean sampled,
                                     boolean renderAttachment,
                                     int mipLevels) {
    public StorageImageDescriptor {
        label = label == null || label.isBlank() ? "combatant-storage-image" : label;
        width = Math.max(1, width);
        height = Math.max(1, height);
        if (format == null || !format.hasColorAspect()) {
            throw new IllegalArgumentException("Storage image must use a color format");
        }
        access = access == null ? StorageAccess.READ_WRITE : access;
        int maximumMipLevels = 32 - Integer.numberOfLeadingZeros(Math.max(width, height));
        if (mipLevels < 1 || mipLevels > maximumMipLevels) {
            throw new IllegalArgumentException("Invalid mip count " + mipLevels + " for "
                    + width + "x" + height + " storage image");
        }
    }

    public StorageImageDescriptor(String label,
                                  int width,
                                  int height,
                                  GpuFormat format,
                                  StorageAccess access,
                                  boolean sampled,
                                  boolean renderAttachment) {
        this(label, width, height, format, access, sampled, renderAttachment, 1);
    }
}
