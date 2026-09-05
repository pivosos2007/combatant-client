/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.resource;

import com.mojang.blaze3d.GpuFormat;

/**
 * Backend-neutral compatibility descriptor for a temporary render target.
 *
 * <p>The logical name describes a compiler allocation; it is deliberately not part of
 * {@link #compatibilityKey()}. Once a lifetime ends, another logical resource with the same
 * physical requirements may reuse the allocation.</p>
 */
public record TransientTargetDescriptor(
        String logicalName,
        int width,
        int height,
        boolean depth,
        GpuFormat colorFormat,
        int samples,
        Lifetime lifetime,
        String owner
) {
    public enum Lifetime {
        PASS,
        SUBMISSION,
        FRAME
    }

    public TransientTargetDescriptor {
        logicalName = logicalName == null || logicalName.isBlank() ? "combatant-transient" : logicalName;
        width = Math.max(1, width);
        height = Math.max(1, height);
        colorFormat = colorFormat != null ? colorFormat : GpuFormat.RGBA8_UNORM;
        samples = Math.max(1, samples);
        lifetime = lifetime != null ? lifetime : Lifetime.PASS;
        owner = owner == null || owner.isBlank() ? "unknown" : owner;
    }

    public static TransientTargetDescriptor frame(String logicalName,
                                                    int width,
                                                    int height,
                                                    boolean depth,
                                                    String owner) {
        return new TransientTargetDescriptor(
                logicalName, width, height, depth, GpuFormat.RGBA8_UNORM, 1, Lifetime.FRAME, owner
        );
    }

    public String logicalKey() {
        return owner + "|" + logicalName;
    }

    public String compatibilityKey() {
        return colorFormat + "|" + depth + "|" + samples + "|" + width + "x" + height;
    }
}
