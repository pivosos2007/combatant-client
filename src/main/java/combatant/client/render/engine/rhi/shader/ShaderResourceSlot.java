/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

/** Backend-neutral descriptor/binding slot. */
public record ShaderResourceSlot(int binding,
                                 ShaderResourceKind kind,
                                 StorageAccess access) {
    public ShaderResourceSlot {
        if (binding < 0) throw new IllegalArgumentException("binding");
        if (kind == null) throw new IllegalArgumentException("kind");
        access = access == null ? StorageAccess.READ_WRITE : access;
    }
}
