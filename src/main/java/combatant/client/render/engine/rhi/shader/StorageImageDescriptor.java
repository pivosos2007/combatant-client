/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

import com.mojang.blaze3d.GpuFormat;

/** Description of a 2D image that can participate in native image load/store. */
public record StorageImageDescriptor(String label,
                                     int width,
                                     int height,
                                     GpuFormat format,
                                     StorageAccess access,
                                     boolean sampled,
                                     boolean renderAttachment) {
    public StorageImageDescriptor {
        label = label == null || label.isBlank() ? "combatant-storage-image" : label;
        width = Math.max(1, width);
        height = Math.max(1, height);
        if (format == null || !format.hasColorAspect()) {
            throw new IllegalArgumentException("Storage image must use a color format");
        }
        access = access == null ? StorageAccess.READ_WRITE : access;
    }
}
