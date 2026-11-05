/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.backend.vulkan.util;

public final class VulkanUploadAlignment {
    private VulkanUploadAlignment() {}

    public static long staging(long alignment) {
        if (alignment <= 0) throw new IllegalArgumentException("Upload alignment must be positive");
        // Vanilla sprite uploads request byte alignment but later copy RGBA texels.
        long divisor = Math.min(Long.lowestOneBit(alignment), 4L);
        return Math.multiplyExact(alignment, 4L / divisor);
    }
}
