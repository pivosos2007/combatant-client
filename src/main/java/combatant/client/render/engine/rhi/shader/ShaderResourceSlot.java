/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

import com.mojang.renderpearl.api.GpuFormat;

/** Backend-neutral descriptor/binding slot. Format is optional until shader reflection supplies it. */
public record ShaderResourceSlot(int binding,
                                 ShaderResourceKind kind,
                                 StorageAccess access,
                                 GpuFormat format) {
    public ShaderResourceSlot {
        if (binding < 0) throw new IllegalArgumentException("binding");
        if (kind == null) throw new IllegalArgumentException("kind");
        access = access == null ? StorageAccess.READ_WRITE : access;
        if (format != null && kind != ShaderResourceKind.STORAGE_IMAGE
                && kind != ShaderResourceKind.STORAGE_VOLUME
                && kind != ShaderResourceKind.SAMPLED_VOLUME) {
            throw new IllegalArgumentException("Expected format is only valid for storage image/volume or sampled-volume slots");
        }
        if (kind == ShaderResourceKind.SAMPLED_VOLUME && access != StorageAccess.READ_ONLY) {
            throw new IllegalArgumentException("SAMPLED_VOLUME slots must be READ_ONLY");
        }
    }

    public ShaderResourceSlot(int binding, ShaderResourceKind kind, StorageAccess access) {
        this(binding, kind, access, null);
    }
}
