/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.resource;

import com.mojang.renderpearl.api.GpuFormat;

/**
 * Stable descriptor for framebuffer resources owned by RenderResourceManager.
 */
public record FramebufferDescriptor(
        String name,
        int width,
        int height,
        boolean depth,
        GpuFormat colorFormat,
        boolean persistent,
        boolean sizeDependent,
        String owner
) {
    public FramebufferDescriptor {
        name = name == null || name.isBlank() ? "combatant-framebuffer" : name;
        width = Math.max(1, width);
        height = Math.max(1, height);
        owner = owner == null || owner.isBlank() ? "unknown" : owner;
        colorFormat = colorFormat == null ? GpuFormat.RGBA8_UNORM : colorFormat;
        if (colorFormat != GpuFormat.RGBA8_UNORM && colorFormat != GpuFormat.RGBA16_FLOAT) {
            throw new IllegalArgumentException("Unsupported framebuffer color format: " + colorFormat);
        }
    }

    public static FramebufferDescriptor persistent(String name, int width, int height, boolean depth, String owner) {
        return persistent(name, width, height, depth, GpuFormat.RGBA8_UNORM, owner);
    }

    public static FramebufferDescriptor temporary(String name, int width, int height, boolean depth, String owner) {
        return temporary(name, width, height, depth, GpuFormat.RGBA8_UNORM, owner);
    }

    public static FramebufferDescriptor persistent(String name, int width, int height, boolean depth,
                                                   GpuFormat format, String owner) {
        return new FramebufferDescriptor(name, width, height, depth, format, true, true, owner);
    }

    public static FramebufferDescriptor temporary(String name, int width, int height, boolean depth,
                                                  GpuFormat format, String owner) {
        return new FramebufferDescriptor(name, width, height, depth, format, false, true, owner);
    }

    public String poolKey() {
        return name + "|" + colorFormat + "|" + depth + "|" + persistent + "|" + owner;
    }

    public String tempKey() {
        return colorFormat + "|" + depth + "|" + width + "x" + height + "|" + owner;
    }
}
