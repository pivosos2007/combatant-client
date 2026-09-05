/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.shader;

public record StorageBinding(int binding,
                             RhiStorageBuffer buffer,
                             long offset,
                             long size,
                             StorageAccess access) {
    public StorageBinding {
        if (binding < 0) throw new IllegalArgumentException("binding");
        if (buffer == null) throw new IllegalArgumentException("buffer");
        offset = Math.max(0L, offset);
        long remaining = Math.max(0L, buffer.descriptor().byteSize() - offset);
        size = size <= 0L ? remaining : Math.min(size, remaining);
        access = access != null ? access : buffer.descriptor().access();
    }
}
